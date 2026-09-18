package com.shop.common.util;

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
 * 因为获取IP的逻辑在多个地方都用到了（操作日志、权限切面、登录日志等），
 * 统一放到这里，大家都调用同一个方法，改一处就全部生效。
 * </p>
 */
public final class IpUtils {

    /** 多级代理时X-Forwarded-For的IP分隔符 */
    private static final String IP_LIST_SEPARATOR = ",";

    /** 代理头缺省值：部分代理会把取不到的头填成"unknown" */
    private static final String UNKNOWN = "unknown";

    private IpUtils() {
    }

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
            return UNKNOWN;
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
        // 按 X-Forwarded-For → X-Real-IP → RemoteAddr 的优先级依次取值，取到第一个有效值即停止
        String ip = usableIp(request.getHeader("X-Forwarded-For"));
        if (ip == null) {
            ip = usableIp(request.getHeader("X-Real-IP"));
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For可能包含多个IP（经过多级代理），取第一个就是真实IP
        if (ip != null && ip.contains(IP_LIST_SEPARATOR)) {
            ip = ip.split(IP_LIST_SEPARATOR)[0].trim();
        }
        return ip;
    }

    /**
     * 判断请求头里的IP值是否有效（非空且不是"unknown"）
     *
     * @param ip 请求头中取到的原始值
     * @return 有效则返回原值，无效返回null（表示需要继续找下一个来源）
     */
    private static String usableIp(String ip) {
        return (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) ? null : ip;
    }
}
