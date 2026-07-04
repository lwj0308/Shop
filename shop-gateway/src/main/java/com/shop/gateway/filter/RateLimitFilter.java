package com.shop.gateway.filter;

import com.shop.gateway.config.RateLimitProperties;
import com.shop.gateway.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;
import java.util.UUID;

/**
 * 请求限流过滤器（Redis + Lua 滑动窗口实现）
 * <p>
 * 使用Redis的Sorted Set + Lua脚本实现滑动窗口限流，相比内存版的优势：
 * 1. 多实例共享：多个Gateway实例共享同一份限流计数（内存版只能单机限流）
 * 2. 准确性高：滑动窗口算法避免固定窗口的"临界点"突发流量问题
 * 3. 原子性强：Lua脚本在Redis单线程内执行，天然防止并发问题
 * </p>
 * <p>
 * 限流维度（两个维度同时检查，任一超限即拒绝）：
 * 1. IP级别：限制单个IP每秒请求数（防止爬虫/刷接口）
 * 2. 接口级别：限制单个接口每秒总请求数（保护后端服务）
 * </p>
 * <p>
 * 分级限流（通过RateLimitProperties配置）：
 * - 默认：IP 50 QPS，接口 200 QPS
 * - 认证接口（/api/user/auth/**）：IP 5 QPS（防爆破）
 * - 秒杀接口（/api/seckill/**）：IP 3 QPS（防刷单）
 * - 下单支付（/api/order/**）：IP 10 QPS
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
 * - 降级策略就是：万一中心系统坏了，保安就先放行，不能因为系统坏了把所有人都拦外面
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    /** 限流窗口大小（毫秒）：1秒一个窗口 */
    private static final long WINDOW_SIZE_MS = 1000L;

    /** Redis Key 前缀：IP级别 */
    private static final String IP_KEY_PREFIX = "rate_limit:ip:";

    /** Redis Key 前缀：接口级别 */
    private static final String PATH_KEY_PREFIX = "rate_limit:path:";

    /** 响应式Redis操作模板（WebFlux环境下必须用Reactive版本，不能用阻塞的StringRedisTemplate） */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 限流Lua脚本（由RateLimitScriptConfig注入） */
    private final RedisScript<Long> rateLimitScript;

    /** 限流配置（从Nacos读取，支持动态刷新） */
    private final RateLimitProperties properties;

    /** Ant路径匹配器（用于规则路径匹配，和WhitelistConfig保持一致） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 限流过滤器的核心方法
     * <p>
     * 执行流程：
     * 1. 检查限流总开关 → 关闭则直接放行
     * 2. 获取客户端IP和请求路径
     * 3. 匹配限流规则，确定QPS限制值
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

        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIp(request);
        String path = request.getURI().getPath();

        // 第2步：根据路径匹配限流规则，确定QPS限制值
        RateLimitProperties.Rule matchedRule = matchRule(path);
        long ipQps = matchedRule != null ? matchedRule.getIpQps() : properties.getDefaultIpQps();
        long pathQps = matchedRule != null ? matchedRule.getPathQps() : properties.getDefaultPathQps();

        // 生成唯一请求ID（用于ZADD的member，避免同一毫秒的请求被去重）
        String requestId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // 第3步：IP级别限流检查（用flatMap串联，响应式编程）
        // 注意：第一个flatMap的两个分支必须返回相同类型，否则Java会推断成Mono<Object>。
        // - IP限流失败：写429响应后用 .then(Mono.<Boolean>empty()) 转成 Mono<Boolean>
        // - IP限流通过：返回 checkLimit(...) 的 Mono<Boolean>
        // 这样两个分支都是 Mono<Boolean>，类型一致。
        return checkLimit(IP_KEY_PREFIX + clientIp, now, ipQps, requestId)
                .flatMap(ipAllowed -> {
                    if (!ipAllowed) {
                        log.warn("IP限流触发，IP：{}，路径：{}，限制：{} QPS", clientIp, path, ipQps);
                        // 写完429响应后返回空Mono（Boolean类型），保持类型一致
                        return ResponseUtil.writeRateLimitResponse(exchange, "请求太频繁，请稍后再试")
                                .then(Mono.<Boolean>empty());
                    }
                    // 第4步：IP检查通过，继续检查接口级别
                    return checkLimit(PATH_KEY_PREFIX + path, now, pathQps, requestId);
                })
                .flatMap(pathAllowed -> {
                    if (!pathAllowed) {
                        log.warn("接口限流触发，IP：{}，路径：{}，限制：{} QPS", clientIp, path, pathQps);
                        return ResponseUtil.writeRateLimitResponse(exchange, "当前访问人数较多，请稍后再试");
                    }
                    // 通过限流检查，放行
                    return chain.filter(exchange);
                })
                // 第5步：降级策略——Redis异常时放行（限流是弱依赖）
                .onErrorResume(error -> {
                    log.error("限流检查异常，降级放行。IP：{}，路径：{}", clientIp, path, error);
                    return chain.filter(exchange);
                });
    }

    /**
     * 调用Lua脚本检查是否超限
     * <p>
     * 把限流逻辑封装成一次Redis调用（Lua脚本保证原子性）：
     * 1. 移除窗口外的旧记录
     * 2. 统计当前窗口内的请求数
     * 3. 未超限则添加本次请求，返回1；超限则返回0
     * </p>
     *
     * @param redisKey  Redis Key（如 rate_limit:ip:192.168.1.1）
     * @param now       当前时间戳（毫秒）
     * @param limit     最大请求数（QPS）
     * @param requestId 唯一请求ID（ZADD的member）
     * @return Mono<Boolean> true=放行，false=被限流
     */
    private Mono<Boolean> checkLimit(String redisKey, long now, long limit, String requestId) {
        List<String> keys = List.of(redisKey);
        Object[] args = new Object[]{
                String.valueOf(now),          // ARGV[1]：当前时间戳
                String.valueOf(WINDOW_SIZE_MS), // ARGV[2]：窗口大小
                String.valueOf(limit),         // ARGV[3]：最大请求数
                requestId                       // ARGV[4]：唯一请求ID
        };

        // 执行Lua脚本，返回Long（1=放行，0=限流）
        // next()是因为execute返回Flux<Long>，我们只需要第一个元素
        return redisTemplate.execute(rateLimitScript, keys, args)
                .next()
                .map(result -> result != null && result == 1L)
                // 如果Redis返回null或异常，降级放行
                .defaultIfEmpty(true);
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
}
