package com.shop.gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 网关侧客户端IP工具（WebFlux版）
 * <p>
 * 网关基于 WebFlux（响应式），请求对象是 {@link ServerHttpRequest} 而不是 Servlet 的
 * HttpServletRequest，且网关模块不依赖 shop-common，因此这里保留一份独立实现，
 * 供各全局过滤器（黑名单、限流、XSS）共用，避免同一段取IP逻辑在每个过滤器里重复一遍。
 * </p>
 * <p>
 * 取值优先级：X-Forwarded-For（多级代理时取第一个） → X-Real-IP → 远程地址 → "unknown"
 * </p>
 */
final class GatewayIpUtils {

    /** 兜底IP：拿不到任何来源时使用 */
    private static final String UNKNOWN = "unknown";

    private GatewayIpUtils() {
    }

    /**
     * 获取客户端真实IP地址
     *
     * @param request 响应式HTTP请求对象
     * @return 客户端IP地址，取不到时返回"unknown"
     */
    static String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress() : UNKNOWN;
    }
}
