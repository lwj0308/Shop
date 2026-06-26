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

import java.util.regex.Pattern;

/**
 * XSS防护过滤器
 * <p>
 * 检查请求参数中是否包含危险的XSS攻击字符（如 &lt;script&gt;、javascript: 等），
 * 如果检测到危险内容，直接拒绝请求。
 * </p>
 * <p>
 * 小白理解：XSS攻击就像有人在你家的留言板上写了一段"恶意代码"，
 * 当其他人看留言板时，这段代码就会自动执行，可能偷走他们的登录信息。
 * 这个过滤器就是"留言板审核员"，发现有恶意代码的留言就直接删掉。
 * </p>
 * <p>
 * 防护策略：
 * 1. 检查URL查询参数中的危险字符
 * 2. 检查路径中的危险字符
 * 3. 不检查请求体（JSON请求体由下游服务自行校验，避免误拦截正常数据）
 * </p>
 */
@Slf4j
@Component
public class XssFilter implements GlobalFilter, Ordered {

    /**
     * XSS危险字符正则表达式
     * <p>
     * 匹配常见的XSS攻击模式：
     * - &lt;script&gt; 标签（最经典的XSS攻击方式）
     * - javascript: 协议（可以在链接中执行JS代码）
     * - on开头的事件属性（onclick、onerror等，可以执行JS代码）
     * - eval(、expression(（可以执行JS/CSS代码）
     * - data: 协议（可以嵌入恶意数据）
     * - vbscript: 协议（IE浏览器特有）
     * </p>
     */
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "<script|</script|javascript:|onerror|onload|onclick|onmouseover|" +
            "onfocus|onblur|eval\\(|expression\\(|data:|vbscript:",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * XSS防护过滤器的核心方法
     * <p>
     * 执行流程：
     * 1. 获取请求的URL查询参数和路径
     * 2. 检查是否包含XSS危险字符
     * 3. 包含危险字符 → 记录日志并返回403
     * 4. 不包含 → 放行
     * </p>
     *
     * @param exchange WebFlux的上下文对象
     * @param chain    过滤器链
     * @return Mono<Void> 响应式返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 获取查询参数和路径
        String query = request.getURI().getRawQuery();
        String path = request.getURI().getRawPath();

        // 检查查询参数
        if (query != null && containsXss(query)) {
            log.warn("XSS攻击检测！路径：{}，查询参数：{}，来源IP：{}",
                    path, truncateForLog(query), getClientIp(request));
            return ResponseUtil.writeForbiddenResponse(exchange, "请求包含非法字符");
        }

        // 检查路径
        if (containsXss(path)) {
            log.warn("XSS攻击检测！路径：{}，来源IP：{}", path, getClientIp(request));
            return ResponseUtil.writeForbiddenResponse(exchange, "请求路径包含非法字符");
        }

        // 通过检查，放行
        return chain.filter(exchange);
    }

    /**
     * 检查字符串是否包含XSS危险字符
     *
     * @param value 待检查的字符串
     * @return true=包含危险字符，false=安全
     */
    private boolean containsXss(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return XSS_PATTERN.matcher(value).find();
    }

    /**
     * 截断过长的字符串，避免日志过长
     *
     * @param value 原始字符串
     * @return 截断后的字符串（最多200个字符）
     */
    private String truncateForLog(String value) {
        if (value.length() <= 200) {
            return value;
        }
        return value.substring(0, 200) + "...";
    }

    /**
     * 获取客户端IP地址
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
     * 设为-70，在限流过滤器之后执行。
     * 限流先执行可以减少XSS检查的开销（被限流的请求不需要检查XSS）。
     * </p>
     * <p>
     * Filter执行顺序：RequestIdFilter(-200) → RequestLogFilter(-90) → AuthGlobalFilter(-100)
     *                → RateLimitFilter(-80) → XssFilter(-70)
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -70;
    }
}
