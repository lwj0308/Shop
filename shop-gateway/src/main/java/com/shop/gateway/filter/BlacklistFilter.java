package com.shop.gateway.filter;

import com.shop.gateway.config.BlacklistConfig;
import com.shop.gateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

import java.util.List;

/**
 * 黑白名单过滤器（RL-11 引入）
 * <p>
 * 在限流过滤器之前执行，实现两级拦截：
 * 1. 黑名单检查：命中直接返回 403（比限流更早拦截，节省资源）
 * 2. 白名单检查：命中跳过限流（内部服务器、监控探针等不受限流约束）
 * </p>
 * <p>
 * 数据来源：
 * - 静态名单：从 Nacos 配置中心读取（BlacklistConfig），适合长期固定的名单
 * - 动态名单：存储在 Redis Set 中，适合运行时动态拉黑（如自动拉黑策略）
 * </p>
 * <p>
 * Filter执行顺序：
 * RequestIdFilter(-200) → AuthGlobalFilter(-100) → RequestLogFilter(-90)
 * → BlacklistFilter(-85) → RateLimitFilter(-80) → XssFilter(-70)
 * </p>
 * <p>
 * 小白理解：
 * - 黑名单就像小区保安手里的"通缉令"，看到通缉犯直接抓起来（403拒绝）
 * - 白名单就像小区保安手里的"VIP名单"，VIP不用排队直接进（跳过限流）
 * - 这个过滤器在限流前面执行，先抓通缉犯，再放VIP，最后普通人才排队
 * </p>
 */
@Slf4j
@Component
public class BlacklistFilter implements GlobalFilter, Ordered {

    /** Redis Key：IP 黑名单 Set（public 供 RateLimitFilter 自动拉黑使用） */
    public static final String REDIS_IP_BLACKLIST = "blacklist:ip";

    /** Redis Key：用户 ID 黑名单 Set */
    private static final String REDIS_USER_BLACKLIST = "blacklist:user";

    /** Redis Key：IP 白名单 Set */
    private static final String REDIS_IP_WHITELIST = "whitelist:ip";

    /** Redis Key：用户 ID 白名单 Set */
    private static final String REDIS_USER_WHITELIST = "whitelist:user";

    /** Exchange 属性 Key：标记白名单请求跳过限流 */
    public static final String ATTR_SKIP_RATE_LIMIT = "SKIP_RATE_LIMIT";

    /** 响应式 Redis 操作模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 黑白名单配置（从 Nacos 读取，支持动态刷新） */
    private final BlacklistConfig config;

    /**
     * 构造函数：注入 Redis 模板和配置
     */
    public BlacklistFilter(ReactiveStringRedisTemplate redisTemplate, BlacklistConfig config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    /**
     * 过滤器核心方法
     * <p>
     * 执行流程：
     * 1. 获取客户端 IP 和用户 ID
     * 2. 黑名单检查（静态名单 → Redis 动态名单）
     * 3. 白名单检查（静态名单 → Redis 动态名单）
     * 4. 黑名单命中返回 403，白名单命中设置跳过限流标记
     * </p>
     *
     * @param exchange WebFlux 上下文
     * @param chain    过滤器链
     * @return Mono<Void> 响应式返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = GatewayIpUtils.getClientIp(request);
        String userId = request.getHeaders().getFirst("X-User-Id");

        // 第1步：检查静态黑名单（Nacos 配置，同步检查，性能高）
        if (isInStaticBlacklist(clientIp, userId)) {
            log.warn("静态黑名单拦截，IP：{}，userId：{}", clientIp, userId);
            return ResponseUtil.writeForbiddenResponse(exchange, "访问被拒绝");
        }

        // 第2步：检查静态白名单（Nacos 配置，同步检查）
        if (isInStaticWhitelist(clientIp, userId)) {
            log.debug("静态白名单放行，跳过限流，IP：{}，userId：{}", clientIp, userId);
            exchange.getAttributes().put(ATTR_SKIP_RATE_LIMIT, true);
            return chain.filter(exchange);
        }

        // 第3步：检查 Redis 动态黑名单（异步检查）
        log.debug("开始检查 Redis 动态黑白名单，IP：{}，userId：{}", clientIp, userId);
        return checkRedisBlacklist(clientIp, userId)
                .flatMap(blocked -> {
                    log.debug("Redis 黑名单检查结果，IP：{}，blocked：{}", clientIp, blocked);
                    if (blocked) {
                        log.warn("动态黑名单拦截，IP：{}，userId：{}", clientIp, userId);
                        return ResponseUtil.writeForbiddenResponse(exchange, "访问被拒绝")
                                .then(Mono.empty());
                    }
                    // 第4步：检查 Redis 动态白名单
                    return checkRedisWhitelist(clientIp, userId);
                })
                .flatMap(whitelisted -> {
                    if (whitelisted) {
                        log.debug("动态白名单放行，跳过限流，IP：{}，userId：{}", clientIp, userId);
                        exchange.getAttributes().put(ATTR_SKIP_RATE_LIMIT, true);
                    }
                    return chain.filter(exchange);
                })
                // 降级策略：Redis 异常时放行（黑白名单是弱依赖，不能因为 Redis 挂了导致全站不可用）
                .onErrorResume(error -> {
                    // N-P 性能测试整改：区分客户端断连和真正的 Redis 异常
                    // 客户端主动断开连接（StacklessClosedChannelException/AbortedException）是正常现象
                    // （比如用户关闭浏览器、JMeter 超时断开），不需要记录 ERROR 日志污染错误日志
                    if (isClientDisconnectedError(error)) {
                        log.debug("客户端断开连接，跳过后续处理。IP：{}，userId：{}", clientIp, userId);
                        return Mono.empty();
                    }
                    log.error("黑白名单检查异常，降级放行。IP：{}，userId：{}", clientIp, userId, error);
                    return chain.filter(exchange);
                });
    }

    /**
     * 检查静态黑名单（Nacos 配置）
     * <p>
     * 同步检查 BlacklistConfig 中的 ipBlacklist 和 userBlacklist 列表。
     * </p>
     *
     * @param ip     客户端 IP
     * @param userId 用户 ID（可为 null）
     * @return true=在黑名单中，false=不在
     */
    private boolean isInStaticBlacklist(String ip, String userId) {
        List<String> ipBlacklist = config.getIpBlacklist();
        if (ipBlacklist != null && ipBlacklist.contains(ip)) {
            return true;
        }
        if (userId != null && !userId.isEmpty()) {
            List<String> userBlacklist = config.getUserBlacklist();
            if (userBlacklist != null && userBlacklist.contains(userId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查静态白名单（Nacos 配置）
     *
     * @param ip     客户端 IP
     * @param userId 用户 ID（可为 null）
     * @return true=在白名单中，false=不在
     */
    private boolean isInStaticWhitelist(String ip, String userId) {
        List<String> ipWhitelist = config.getIpWhitelist();
        if (ipWhitelist != null && ipWhitelist.contains(ip)) {
            return true;
        }
        if (userId != null && !userId.isEmpty()) {
            List<String> userWhitelist = config.getUserWhitelist();
            if (userWhitelist != null && userWhitelist.contains(userId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查 Redis 动态黑名单
     * <p>
     * 用 SISMEMBER 命令检查 IP 和 userId 是否在 Redis Set 中。
     * 两个检查并行执行（zip），任一命中即返回 true。
     * </p>
     *
     * @param ip     客户端 IP
     * @param userId 用户 ID（可为 null）
     * @return Mono<Boolean> true=在黑名单中
     */
    private Mono<Boolean> checkRedisBlacklist(String ip, String userId) {
        Mono<Boolean> ipCheck = redisTemplate.opsForSet().isMember(REDIS_IP_BLACKLIST, ip)
                .defaultIfEmpty(false);

        Mono<Boolean> userCheck;
        if (userId != null && !userId.isEmpty()) {
            userCheck = redisTemplate.opsForSet().isMember(REDIS_USER_BLACKLIST, userId)
                    .defaultIfEmpty(false);
        } else {
            userCheck = Mono.just(false);
        }

        // 两个检查并行，任一命中即拉黑
        return Mono.zip(ipCheck, userCheck)
                .map(tuple -> tuple.getT1() || tuple.getT2());
    }

    /**
     * 检查 Redis 动态白名单
     *
     * @param ip     客户端 IP
     * @param userId 用户 ID（可为 null）
     * @return Mono<Boolean> true=在白名单中
     */
    private Mono<Boolean> checkRedisWhitelist(String ip, String userId) {
        Mono<Boolean> ipCheck = redisTemplate.opsForSet().isMember(REDIS_IP_WHITELIST, ip)
                .defaultIfEmpty(false);

        Mono<Boolean> userCheck;
        if (userId != null && !userId.isEmpty()) {
            userCheck = redisTemplate.opsForSet().isMember(REDIS_USER_WHITELIST, userId)
                    .defaultIfEmpty(false);
        } else {
            userCheck = Mono.just(false);
        }

        return Mono.zip(ipCheck, userCheck)
                .map(tuple -> tuple.getT1() || tuple.getT2());
    }

    /**
     * 过滤器执行顺序：-85
     * <p>
     * 在 RequestLogFilter(-90) 之后，RateLimitFilter(-80) 之前执行。
     * 这样黑白名单检查在限流之前完成：
     * - 黑名单请求直接 403，不消耗限流配额
     * - 白名单请求跳过限流，直接放行
     * </p>
     */
    @Override
    public int getOrder() {
        return -85;
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
        // 直接匹配 AbortedException（reactor-netty 公开类）
        if (error instanceof AbortedException) {
            return true;
        }
        // 通过类名匹配 StacklessClosedChannelException（Netty package-private 类，无法直接引用）
        String errorClassName = error.getClass().getName();
        if (errorClassName.contains("StacklessClosedChannelException")) {
            return true;
        }
        // 递归检查 cause 链（异常可能被包装）
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
