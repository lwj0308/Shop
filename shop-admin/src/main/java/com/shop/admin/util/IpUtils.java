package com.shop.admin.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP地址工具类
 * <p>
 * 从HTTP请求中获取客户端的真实IP地址。
 * 考虑了经过Nginx等反向代理的情况，优先从代理转发的请求头中获取真实IP。
 * </p>
 * <p>
 * 为什么要这个工具类？
 * 因为获取IP的逻辑在多个地方都用到了（操作日志、权限切面、安全事件等），
 * 如果每个地方都写一遍，代码就重复了，改起来也容易漏。
 * 统一放到这里，大家都调用同一个方法，改一处就全部生效。
 * </p>
 */
public class IpUtils {

    /**
     * 从当前HTTP请求中获取客户端真实IP地址
     * <p>
     * 使用场景：在Service层或切面中，没有HttpServletRequest参数时调用这个方法。
     * 它会自动从Spring的RequestContextHolder中获取当前请求。
     * </p>
     *
     * @return 客户端IP地址，获取不到时返回"unknown"
     */
    public static String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // 非Web环境（如定时任务、消息消费者），无法获取IP
            return "unknown";
        }
        return getClientIp(attributes.getRequest());
    }

    /**
     * 从HttpServletRequest中获取客户端真实IP地址
     * <p>
     * 优先级：
     * 1. X-Forwarded-For：经过Nginx等代理时，真实IP在这个头里
     * 2. X-Real-IP：Nginx配置了proxy_set_header X-Real-IP时使用
     * 3. RemoteAddr：直连时使用，就是客户端的真实IP
     * </p>
     * <p>
     * 注意：X-Forwarded-For可能包含多个IP（经过多级代理），格式为"IP1, IP2, IP3"，
     * 其中第一个IP就是客户端的真实IP。
     * </p>
     *
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        // 优先从X-Forwarded-For获取（经过Nginx等代理时，真实IP在这个头里）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 其次从X-Real-IP获取
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 最后用RemoteAddr
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For可能包含多个IP（经过多级代理），取第一个就是真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
