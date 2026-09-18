package com.shop.gateway.filter;

import com.shop.gateway.config.RateLimitProperties;
import com.shop.gateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

import java.util.List;
import java.util.UUID;

/**
 * 请求限流过滤器（Redis + Lua 双算法实现）
 * <p>
 * RL-01 阶段升级为双算法架构，按接口特征选择最优算法：
 * 1. ZSet 精确滑动窗口（sliding-window）：低频高精度场景，如认证、评论
 *    - 每个请求存一条 ZSET 记录，毫秒级精度
 *    - 内存占用高，不适合高频接口（50+ QPS）
 * 2. 令牌桶（token-bucket）：高频接口，如秒杀、下单、商品浏览
 *    - 只存 tokens + last_refill 两个字段，内存 O(1)
 *    - 允许合理突发（桶容量 = 突发上限）
 * </p>
 * <p>
 * 限流维度（两个维度同时检查，任一超限即拒绝）：
 * 1. 用户级别（RL-10 引入）：登录用户按 userId 限流，防止代理池绕过IP限流
 *    - 仅登录用户生效，未登录用户降级用 IP 维度
 *    - 防止用户用多IP绕过IP限流刷接口（比如代理池秒杀）
 * 2. 接口级别：限制单个接口每秒总请求数（保护后端服务）
 * </p>
 * <p>
 * 分级限流（通过RateLimitProperties配置）：
 * - 默认：令牌桶，IP 50 QPS，接口 200 QPS
 * - 认证接口（/api/user/auth/**）：ZSet 滑动窗口，IP 5 QPS（防爆破）
 * - 秒杀接口（/api/seckill/grab/**）：令牌桶，IP 3 QPS（防刷单，桶容量 5）
 * - 下单支付（/api/order/**）：令牌桶，IP 10 QPS（桶容量 20，允许短时突发）
 * - 评论提交（/api/product/comment/create）：ZSet 滑动窗口，IP 3 QPS
 * </p>
 * <p>
 * 降级策略：Redis不可用时放行请求（限流是弱依赖，不能因为Redis挂了导致全站不可用）。
 * 这和秒杀不一样——秒杀的Redis库存是强依赖（超卖比拒绝对业务伤害更大），
 * 但限流只是防护措施，挂了最多是没防护，业务还能继续跑。
 * </p>
 * <p>
 * 小白理解：
 * - 内存限流就像每个小区保安自己记车牌，但多几个门就乱套了
 * - Redis限流就像把车牌记录放到统一中心，所有保安共享数据
 * - Lua脚本就像给保安一份"操作手册"，告诉他怎么查、怎么记，一步到位不会出错
 * - 双算法就像小区保安有两套工具：高峰时段用速通门（令牌桶，快速但允许爆发），
 *   低峰时段用登记本（滑动窗口，精确但慢）
 * - 降级策略就是：万一中心系统坏了，保安就先放行，不能因为系统坏了把所有人都拦外面
 * </p>
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    /** 限流窗口大小（毫秒）：1秒一个窗口，仅 sliding-window 算法使用 */
    private static final long WINDOW_SIZE_MS = 1000L;

    /** Redis Key 前缀：IP级别 */
    private static final String IP_KEY_PREFIX = "rate_limit:ip:";

    /** Redis Key 前缀：接口级别 */
    private static final String PATH_KEY_PREFIX = "rate_limit:path:";

    /** Redis Key 前缀：用户级别（RL-10 引入） */
    private static final String USER_KEY_PREFIX = "rate_limit:user:";

    /** 算法常量：ZSet 精确滑动窗口 */
    private static final String ALGORITHM_SLIDING_WINDOW = "sliding-window";

    /** 算法常量：令牌桶 */
    private static final String ALGORITHM_TOKEN_BUCKET = "token-bucket";

    /** 响应式Redis操作模板（WebFlux环境下必须用Reactive版本，不能用阻塞的StringRedisTemplate） */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 滑动窗口限流Lua脚本（由RateLimitScriptConfig注入） */
    private final RedisScript<Long> rateLimitScript;

    /** 令牌桶限流Lua脚本（由RateLimitScriptConfig注入，通过 @Qualifier 按名称区分） */
    private final RedisScript<Long> tokenBucketScript;

    /** 限流配置（从Nacos读取，支持动态刷新） */
    private final RateLimitProperties properties;

    /** 黑白名单配置（RL-11 引入，用于自动拉黑阈值判断） */
    private final com.shop.gateway.config.BlacklistConfig blacklistConfig;

    /** Ant路径匹配器（用于规则路径匹配，和WhitelistConfig保持一致） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造函数：通过 @Qualifier 按名称注入两个不同的 Lua 脚本 Bean
     * <p>
     * Spring 默认按类型注入，但这里有两个 RedisScript&lt;Long&gt; 类型的 Bean
     * （rateLimitScript 和 tokenBucketScript），必须用 @Qualifier 按名称区分。
     * </p>
     */
    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                            @Qualifier("rateLimitScript") RedisScript<Long> rateLimitScript,
                            @Qualifier("tokenBucketScript") RedisScript<Long> tokenBucketScript,
                            RateLimitProperties properties,
                            com.shop.gateway.config.BlacklistConfig blacklistConfig) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.tokenBucketScript = tokenBucketScript;
        this.properties = properties;
        this.blacklistConfig = blacklistConfig;
    }

    /**
     * 限流过滤器的核心方法
     * <p>
     * 执行流程：
     * 1. 检查限流总开关 → 关闭则直接放行
     * 2. 获取客户端IP和请求路径
     * 3. 匹配限流规则，确定算法和限流参数
     * 4. IP级别限流检查 → 超限：返回429
     * 5. 接口级别限流检查 → 超限：返回429
     * 6. 通过限流检查，放行
     * </p>
     * <p>
     * 响应式编程说明：
     * - 所有Redis操作返回Mono，必须用flatMap串联，不能阻塞
     * - 任意一步超限就返回429响应，不再继续后续检查
     * - Redis异常时用onErrorResume降级放行
     * </p>
     *
     * @param exchange WebFlux的上下文对象
     * @param chain    过滤器链
     * @return Mono<Void> 响应式返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 第1步：检查限流总开关
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        // RL-11：白名单请求跳过限流（BlacklistFilter 设置的标记）
        if (Boolean.TRUE.equals(exchange.getAttribute(BlacklistFilter.ATTR_SKIP_RATE_LIMIT))) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIp(request);
        String path = request.getURI().getPath();

        // 第2步：根据路径匹配限流规则，确定算法和限流参数
        RateLimitProperties.Rule matchedRule = matchRule(path);
        String algorithm = resolveAlgorithm(matchedRule);
        long ipQps = matchedRule != null && matchedRule.getIpQps() > 0
                ? matchedRule.getIpQps() : properties.getDefaultIpQps();
        long pathQps = matchedRule != null && matchedRule.getPathQps() > 0
                ? matchedRule.getPathQps() : properties.getDefaultPathQps();
        long capacity = resolveCapacity(matchedRule, ipQps, pathQps);

        // RL-10：获取登录用户ID（AuthGlobalFilter 已写入 X-User-Id Header）
        // 登录用户用 user 维度限流，防止用多IP绕过IP限流（代理池刷单）
        String userId = request.getHeaders().getFirst("X-User-Id");
        long userQps = matchedRule != null ? matchedRule.getUserQps() : 0L;
        // 是否启用用户维度限流：配置了 userQps > 0 且请求携带 userId
        boolean useUserLimit = userId != null && !userId.isEmpty() && userQps > 0;

        // 滑动窗口需要唯一请求ID（ZADD 的 member，避免同毫秒请求被去重）
        String requestId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // 第3步：第一道限流检查（用户维度 或 IP维度）
        // 登录用户：检查 user 维度（替代 IP 维度，防止代理池绕过）
        // 未登录用户：检查 IP 维度（已有逻辑）
        // 注意：第一个flatMap的两个分支必须返回相同类型，否则Java会推断成Mono<Object>。
        String firstKey = useUserLimit ? USER_KEY_PREFIX + userId : IP_KEY_PREFIX + clientIp;
        long firstQps = useUserLimit ? userQps : ipQps;
        String firstDimDesc = useUserLimit ? "用户" + userId : "IP " + clientIp;

        return checkLimit(firstKey, algorithm, now, firstQps, capacity, requestId)
                .flatMap(firstAllowed -> {
                    if (!firstAllowed) {
                        log.warn("{}限流触发，路径：{}，算法：{}，限制：{}",
                                firstDimDesc, path, algorithm, firstQps);
                        // RL-11：触发限流时，自动拉黑计数
                        String blockKey = useUserLimit ? userId : clientIp;
                        return autoBlockIfExceeded(blockKey)
                                .then(ResponseUtil.writeRateLimitResponse(exchange, "操作太频繁，请稍后再试"))
                                .then(Mono.<Boolean>empty());
                    }
                    // 第4步：第一道检查通过，继续检查接口级别
                    return checkLimit(PATH_KEY_PREFIX + path, algorithm, now, pathQps, capacity, requestId);
                })
                .flatMap(pathAllowed -> {
                    if (!pathAllowed) {
                        log.warn("接口限流触发，IP：{}，路径：{}，算法：{}，限制：{}",
                                clientIp, path, algorithm, pathQps);
                        return ResponseUtil.writeRateLimitResponse(exchange, "当前访问人数较多，请稍后重试");
                    }
                    // 通过限流检查，放行
                    return chain.filter(exchange);
                })
                // 第5步：降级策略——Redis异常时放行（限流是弱依赖）
                .onErrorResume(error -> {
                    // N-P 性能测试整改：客户端断连是正常现象，不记录 ERROR 日志
                    if (isClientDisconnectedError(error)) {
                        log.debug("客户端断开连接，跳过限流后续处理。IP：{}，路径：{}", clientIp, path);
                        return Mono.empty();
                    }
                    log.error("限流检查异常，降级放行。IP：{}，路径：{}", clientIp, path, error);
                    return chain.filter(exchange);
                });
    }

    /**
     * 自动拉黑逻辑（RL-11 引入）
     * <p>
     * 当请求触发限流时，对限流 Key（IP 或 userId）进行计数。
     * 如果在1小时内累计触发限流超过阈值（默认10次），自动拉黑1小时。
     * </p>
     * <p>
     * 小白理解：就像超市保安发现有人连续10次插队，就把这个人拉进黑名单，
     * 1小时内不许再进超市。过了1小时自动解除，给个改过自新的机会。
     * </p>
     *
     * @param blockKey 限流 Key（IP 或 userId）
     * @return Mono<Void> 完成信号
     */
    private Mono<Void> autoBlockIfExceeded(String blockKey) {
        int threshold = blacklistConfig.getAutoBlockThreshold();
        // 阈值为0表示禁用自动拉黑
        if (threshold <= 0) {
            return Mono.empty();
        }

        String countKey = "rate_limit:reject:" + blockKey;
        // INCR 计数 + 设置过期时间（首次设置，避免计数器无限增长）
        return redisTemplate.opsForValue().increment(countKey)
                .flatMap(count -> {
                    // 首次触发限流时设置过期时间（1小时）
                    if (count != null && count == 1L) {
                        return redisTemplate.expire(countKey, java.time.Duration.ofSeconds(blacklistConfig.getAutoBlockDuration()))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count != null && count >= threshold) {
                        // 超过阈值，自动拉黑
                        log.warn("自动拉黑触发，key：{}，限流次数：{}，阈值：{}，拉黑时长：{}秒",
                                blockKey, count, threshold, blacklistConfig.getAutoBlockDuration());
                        // 添加到 IP 黑名单 Set（无论 blockKey 是 IP 还是 userId，统一存 IP 黑名单）
                        // 注意：userId 维度的拉黑应该存到 user 黑名单，但为简化实现，统一存 IP 黑名单
                        return redisTemplate.opsForSet().add(BlacklistFilter.REDIS_IP_BLACKLIST, blockKey)
                                .then(redisTemplate.expire(
                                        BlacklistFilter.REDIS_IP_BLACKLIST + ":" + blockKey,
                                        java.time.Duration.ofSeconds(blacklistConfig.getAutoBlockDuration())
                                ))
                                .then();
                    }
                    return Mono.empty();
                })
                // 自动拉黑是弱依赖，异常不影响限流响应
                .onErrorResume(error -> {
                    // N-P 性能测试整改：客户端断连不记录 ERROR 日志
                    if (isClientDisconnectedError(error)) {
                        log.debug("客户端断开连接，跳过自动拉黑处理。key：{}", blockKey);
                        return Mono.empty();
                    }
                    log.error("自动拉黑检查异常，key：{}", blockKey, error);
                    return Mono.empty();
                });
    }

    /**
     * 调用Lua脚本检查是否超限（按算法类型分发）
     * <p>
     * 根据 algorithm 选择不同的 Lua 脚本：
     * - sliding-window：调用 rateLimitScript，传 4 个参数（now, window, limit, uid）
     * - token-bucket：调用 tokenBucketScript，传 3 个参数（now, rate, capacity）
     * </p>
     *
     * @param redisKey  Redis Key（如 rate_limit:ip:192.168.1.1）
     * @param algorithm 限流算法：sliding-window 或 token-bucket
     * @param now       当前时间戳（毫秒）
     * @param limit     限流值（sliding-window=QPS, token-bucket=rate 令牌/秒）
     * @param capacity  令牌桶容量（仅 token-bucket 用）
     * @param requestId 唯一请求ID（仅 sliding-window 用，ZADD 的 member）
     * @return Mono<Boolean> true=放行，false=被限流
     */
    private Mono<Boolean> checkLimit(String redisKey, String algorithm, long now,
                                      long limit, long capacity, String requestId) {
        List<String> keys = List.of(redisKey);
        Object[] args;
        RedisScript<Long> script;

        if (ALGORITHM_TOKEN_BUCKET.equals(algorithm)) {
            // 令牌桶：参数 = 当前时间戳, 速率 rate, 桶容量 capacity
            args = new Object[]{
                    String.valueOf(now),         // ARGV[1]：当前时间戳
                    String.valueOf(limit),        // ARGV[2]：令牌生成速率（令牌/秒）
                    String.valueOf(capacity)      // ARGV[3]：桶容量
            };
            script = tokenBucketScript;
        } else {
            // 滑动窗口（默认）：参数 = 当前时间戳, 窗口大小, 最大请求数, 唯一请求ID
            args = new Object[]{
                    String.valueOf(now),          // ARGV[1]：当前时间戳
                    String.valueOf(WINDOW_SIZE_MS), // ARGV[2]：窗口大小
                    String.valueOf(limit),         // ARGV[3]：最大请求数
                    requestId                       // ARGV[4]：唯一请求ID
            };
            script = rateLimitScript;
        }

        // 执行Lua脚本，返回Long（1=放行，0=限流）
        // next()是因为execute返回Flux<Long>，我们只需要第一个元素
        return redisTemplate.execute(script, keys, args)
                .next()
                .map(result -> result != null && result == 1L)
                // 如果Redis返回null或异常，降级放行
                .defaultIfEmpty(true);
    }

    /**
     * 解析当前请求使用的限流算法
     * <p>
     * 优先级：规则配置的 algorithm > 默认算法
     * 兜底：未识别的算法值统一回退到 token-bucket（高频场景多，且更安全）
     * </p>
     *
     * @param rule 匹配到的限流规则，可为 null
     * @return 算法常量（sliding-window 或 token-bucket）
     */
    private String resolveAlgorithm(RateLimitProperties.Rule rule) {
        String algorithm = rule != null && rule.getAlgorithm() != null && !rule.getAlgorithm().isEmpty()
                ? rule.getAlgorithm() : properties.getDefaultAlgorithm();
        // 未识别的算法值兜底为 token-bucket
        if (!ALGORITHM_SLIDING_WINDOW.equals(algorithm) && !ALGORITHM_TOKEN_BUCKET.equals(algorithm)) {
            algorithm = ALGORITHM_TOKEN_BUCKET;
        }
        return algorithm;
    }

    /**
     * 解析令牌桶容量
     * <p>
     * 仅 token-bucket 算法使用，优先级：规则 capacity > 默认 capacity > ipQps/pathQps 取较小者的2倍
     * 桶容量一般设为速率的 2 倍，允许短时突发
     * </p>
     *
     * @param rule    匹配到的限流规则，可为 null
     * @param ipQps   IP 维度速率
     * @param pathQps 接口维度速率
     * @return 令牌桶容量
     */
    private long resolveCapacity(RateLimitProperties.Rule rule, long ipQps, long pathQps) {
        if (rule != null && rule.getCapacity() != null && rule.getCapacity() > 0) {
            return rule.getCapacity();
        }
        // 默认容量 = IP 和接口速率的较小者的 2 倍（取较小者保证两个维度容量一致）
        // 这样配置更直观：rate=3, capacity 默认 6，允许瞬时 6 个请求
        long smaller = Math.min(ipQps, pathQps);
        if (smaller > 0) {
            return smaller * 2;
        }
        return properties.getDefaultCapacity();
    }

    /**
     * 根据请求路径匹配限流规则
     * <p>
     * 遍历配置的规则列表，找到第一个匹配的规则返回。
     * 没匹配到返回null，调用方使用默认值。
     * </p>
     *
     * @param path 请求路径（如 /api/user/auth/login）
     * @return 匹配到的规则，没匹配到返回null
     */
    private RateLimitProperties.Rule matchRule(String path) {
        if (properties.getRules() == null || properties.getRules().isEmpty()) {
            return null;
        }
        for (RateLimitProperties.Rule rule : properties.getRules()) {
            if (rule.getPathPattern() != null && pathMatcher.match(rule.getPathPattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 获取客户端真实IP地址
     * <p>
     * 请求可能经过多层代理，需要从X-Forwarded-For等Header中获取真实IP。
     * X-Forwarded-For格式：client, proxy1, proxy2，取第一个就是客户端IP。
     * </p>
     *
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * 过滤器执行顺序
     * <p>
     * 设为-80，在鉴权过滤器（-100）之后执行。
     * 先鉴权再限流的好处：
     * 1. 未认证的请求直接被401拒绝，不消耗限流配额
     * 2. 限流只保护已认证的合法用户请求，资源利用率更高
     * </p>
     * <p>
     * Filter执行顺序：
     * RequestIdFilter(-200) → AuthGlobalFilter(-100) → RequestLogFilter(-90) → RateLimitFilter(-80)
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -80;
    }

    /**
     * 判断异常是否由客户端断开连接引起
     * <p>
     * 客户端主动关闭连接（如用户关闭浏览器、JMeter 连接超时）时，
     * Netty 会抛出 AbortedException（包装 StacklessClosedChannelException）。
     * 这类异常不是服务端 bug，不应该记录 ERROR 级别日志。
     * </p>
     * <p>
     * 注意：StacklessClosedChannelException 是 Netty 的 package-private 类，
     * 无法直接 instanceof 判断，所以通过类名字符串匹配。
     * </p>
     *
     * @param error 捕获的异常
     * @return true=客户端断连，false=其他异常
     */
    private boolean isClientDisconnectedError(Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof AbortedException) {
            return true;
        }
        String errorClassName = error.getClass().getName();
        if (errorClassName.contains("StacklessClosedChannelException")) {
            return true;
        }
        Throwable cause = error.getCause();
        while (cause != null && cause != error) {
            if (cause instanceof AbortedException) {
                return true;
            }
            if (cause.getClass().getName().contains("StacklessClosedChannelException")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
