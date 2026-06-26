package com.shop.gateway.filter;

import com.shop.gateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求限流过滤器
 * <p>
 * 基于令牌桶算法实现IP级别和接口级别的请求限流。
 * 防止恶意用户或爬虫频繁请求导致服务不可用。
 * </p>
 * <p>
 * 小白理解：就像游乐园的过山车，每小时最多只能接待500人。
 * 如果人数超过限制，后面的人就得排队等下一轮。
 * 限流就是给接口设一个"最大接待量"，超过就返回"请稍后再试"。
 * </p>
 * <p>
 * 限流策略：
 * 1. IP级别限流：同一个IP每秒最多允许的请求数（防止单个IP刷接口）
 * 2. 接口级别限流：同一个接口每秒最多允许的总请求数（保护服务不被打崩）
 * </p>
 * <p>
 * 注意：这是基于内存的单机限流，适合中小规模项目。
 * 如果是大规模分布式部署，建议使用Sentinel或Redis限流。
 * </p>
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    /** IP级别限流：每个IP每秒允许的最大请求数 */
    private static final long IP_QPS_LIMIT = 50L;

    /** 接口级别限流：每个接口每秒允许的最大请求数 */
    private static final long PATH_QPS_LIMIT = 200L;

    /** 限流窗口大小（毫秒），1秒一个窗口 */
    private static final long WINDOW_SIZE_MS = 1000L;

    /** IP请求计数器：Key=IP地址+窗口起始时间，Value=请求次数 */
    private final ConcurrentHashMap<String, TokenBucket> ipCounters = new ConcurrentHashMap<>();

    /** 接口请求计数器：Key=接口路径+窗口起始时间，Value=请求次数 */
    private final ConcurrentHashMap<String, TokenBucket> pathCounters = new ConcurrentHashMap<>();

    /**
     * 限流过滤器的核心方法
     * <p>
     * 执行流程：
     * 1. 获取客户端IP和请求路径
     * 2. 检查IP级别是否超限 → 超限：返回429
     * 3. 检查接口级别是否超限 → 超限：返回429
     * 4. 通过限流检查，放行
     * </p>
     *
     * @param exchange WebFlux的上下文对象
     * @param chain    过滤器链
     * @return Mono<Void> 响应式返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIp(request);
        String path = request.getURI().getPath();

        // 第一步：IP级别限流检查
        if (isRateLimited(ipCounters, "ip:" + clientIp, IP_QPS_LIMIT)) {
            log.warn("IP限流触发，IP：{}，路径：{}", clientIp, path);
            return ResponseUtil.writeRateLimitResponse(exchange, "请求太频繁，请稍后再试");
        }

        // 第二步：接口级别限流检查
        if (isRateLimited(pathCounters, "path:" + path, PATH_QPS_LIMIT)) {
            log.warn("接口限流触发，IP：{}，路径：{}", clientIp, path);
            return ResponseUtil.writeRateLimitResponse(exchange, "当前访问人数较多，请稍后再试");
        }

        // 通过限流检查，放行
        return chain.filter(exchange);
    }

    /**
     * 检查是否触发限流
     * <p>
     * 使用滑动窗口算法：每秒一个窗口，统计当前窗口内的请求次数。
     * 如果超过限制则返回true（被限流），否则计数+1并返回false（放行）。
     * </p>
     *
     * @param counters 计数器Map
     * @param key      限流Key（IP或路径）
     * @param limit    每秒允许的最大请求数
     * @return true=被限流（超过限制），false=未限流（放行）
     */
    private boolean isRateLimited(ConcurrentHashMap<String, TokenBucket> counters, String key, long limit) {
        // 计算当前窗口的起始时间（每秒一个窗口）
        long currentWindow = System.currentTimeMillis() / WINDOW_SIZE_MS;
        String windowKey = key + ":" + currentWindow;

        // 获取或创建当前窗口的令牌桶
        TokenBucket bucket = counters.computeIfAbsent(windowKey, k -> new TokenBucket());

        // 原子递增并检查是否超限
        long count = bucket.increment();
        if (count > limit) {
            return true;  // 超过限制，触发限流
        }

        // 清理过期的计数器（防止内存泄漏）
        cleanExpiredCounters(counters, currentWindow);

        return false;  // 未超限，放行
    }

    /**
     * 清理过期的计数器
     * <p>
     * 只保留当前窗口的计数器，删除之前的窗口。
     * 防止计数器Map无限增长导致内存泄漏。
     * </p>
     *
     * @param counters      计数器Map
     * @param currentWindow 当前窗口的起始时间
     */
    private void cleanExpiredCounters(ConcurrentHashMap<String, TokenBucket> counters, long currentWindow) {
        // 每隔一段时间清理一次，避免频繁清理影响性能
        if (currentWindow % 10 == 0) {
            counters.entrySet().removeIf(entry -> {
                String key = entry.getKey();
                int lastColon = key.lastIndexOf(':');
                if (lastColon > 0) {
                    try {
                        long windowTime = Long.parseLong(key.substring(lastColon + 1));
                        // 删除2秒前的窗口（保留1秒的缓冲）
                        return windowTime < currentWindow - 2;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return false;
            });
        }
    }

    /**
     * 获取客户端真实IP地址
     * <p>
     * 请求可能经过多层代理，需要从X-Forwarded-For等Header中获取真实IP。
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
     * 设为-80，在鉴权过滤器之后执行。
     * 先鉴权再限流，避免未认证的请求也占用限流配额。
     * </p>
     * <p>
     * Filter执行顺序：RequestIdFilter(-200) → RequestLogFilter(-90) → AuthGlobalFilter(-100) → RateLimitFilter(-80)
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -80;
    }

    /**
     * 令牌桶（内部类）
     * <p>
     * 使用AtomicLong实现线程安全的计数器。
     * 每个时间窗口对应一个令牌桶，记录该窗口内的请求次数。
     * </p>
     */
    private static class TokenBucket {
        private final AtomicLong count = new AtomicLong(0);

        /**
         * 原子递增计数，返回递增后的值
         *
         * @return 递增后的计数值
         */
        public long increment() {
            return count.incrementAndGet();
        }
    }
}
