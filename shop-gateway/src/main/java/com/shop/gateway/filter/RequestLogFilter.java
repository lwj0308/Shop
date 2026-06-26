package com.shop.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 请求日志过滤器
 * <p>
 * 记录每个经过网关的请求信息：请求路径、方法、来源IP、耗时等。
 * 支持敏感信息脱敏、超大请求体警告、慢请求告警。
 * </p>
 * <p>
 * 小白理解：这个过滤器就像商场的监控摄像头，记录每个人从哪个门进来、
 * 什么时候进来的、待了多久。出了问题可以回放录像查原因。
 * 同时还会给敏感信息打马赛克（脱敏），保护用户隐私。
 * </p>
 * <p>
 * 注意：这个过滤器的order值要大于AuthGlobalFilter（-100），
 * 这样鉴权先执行，日志后执行。未通过鉴权的请求不会记录到正常日志中。
 * </p>
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    /** 请求开始时间的属性Key，存在exchange的属性里，方便后续计算耗时 */
    private static final String START_TIME_KEY = "requestStartTime";

    /** 慢请求阈值（毫秒），超过这个时间的请求会被标记为WARN */
    private static final long SLOW_REQUEST_THRESHOLD = 3000L;

    /** 超大请求体阈值（字节），超过1MB记录警告 */
    private static final long LARGE_BODY_THRESHOLD = 1024 * 1024;

    /** 需要脱敏的参数名（不区分大小写），这些参数的值不能原样记录到日志 */
    private static final Set<String> SENSITIVE_PARAM_NAMES = Set.of(
            "password", "pwd", "passwd",
            "token", "accesstoken", "access_token",
            "secret", "secretkey", "secret_key",
            "creditcard", "credit_card",
            "idcard", "id_card",
            "cvv", "cvv2"
    );

    /** 手机号正则：匹配11位数字，中间4位用*替换，比如 13812345678 → 138****5678 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");

    /** 身份证号正则：匹配18位，中间部分用*替换 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{6})\\d{8}(\\d{4})");

    /**
     * 记录请求日志的核心方法
     * <p>
     * 执行流程：
     * 1. 请求进来时：记录开始时间，打印请求信息（脱敏后的参数）
     * 2. 检查请求体大小，超大请求体记录警告
     * 3. 请求完成后：计算耗时，打印响应信息，慢请求标记WARN
     * </p>
     * <p>
     * then()方法的作用：在请求处理完成后执行一段逻辑，
     * 类似于"先去做你的事，做完回来告诉我"。
     * </p>
     *
     * @param exchange WebFlux的上下文对象
     * @param chain    过滤器链
     * @return Mono<Void> 响应式返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 记录请求开始时间，用于计算接口耗时
        exchange.getAttributes().put(START_TIME_KEY, System.currentTimeMillis());

        // 打印请求基本信息
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String clientIp = getClientIp(request);
        String query = request.getURI().getQuery();

        // 对查询参数进行脱敏处理，防止密码、手机号等泄露到日志
        String sanitizedQuery = sanitizeQuery(query);

        log.info("请求开始 >>> 方法：{}，路径：{}，参数：{}，来源IP：{}", method, path,
                sanitizedQuery, clientIp);

        // 检查请求体大小，超大请求体记录警告
        checkRequestBodySize(request);

        // chain.filter(exchange) 表示继续执行后续过滤器和目标服务
        // then() 表示请求处理完成后的回调（类似finally）
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 计算接口耗时
            Long startTime = exchange.getAttribute(START_TIME_KEY);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

            // 获取响应状态码
            Integer statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;

            // 根据耗时选择日志级别：正常用INFO，慢请求用WARN
            if (duration > SLOW_REQUEST_THRESHOLD) {
                log.warn("慢接口警告！方法：{}，路径：{}，状态码：{}，耗时：{}ms，请检查是否需要优化",
                        method, path, statusCode, duration);
            } else {
                log.info("请求结束 <<< 方法：{}，路径：{}，状态码：{}，耗时：{}ms",
                        method, path, statusCode, duration);
            }
        }));
    }

    /**
     * 对查询参数进行脱敏处理
     * <p>
     * 遍历查询参数，对敏感参数（如password、token等）的值替换为***，
     * 对手机号和身份证号进行部分遮盖。
     * </p>
     * <p>
     * 小白理解：就像快递单上的手机号，中间4位用星号代替，
     * 既能让人知道有这个信息，又不会泄露完整内容。
     * </p>
     *
     * @param query 原始查询字符串，比如 "username=zhangsan&password=123456"
     * @return 脱敏后的查询字符串，比如 "username=zhangsan&password=***"
     */
    private String sanitizeQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "无";
        }

        String sanitized = query;

        // 对敏感参数的值进行脱敏：password=xxx → password=***
        for (String paramName : SENSITIVE_PARAM_NAMES) {
            // 匹配参数名=值 的模式，不区分大小写
            sanitized = sanitized.replaceAll("(?i)(" + paramName + "=)([^&\\s]+)", "$1***");
        }

        // 对手机号脱敏：13812345678 → 138****5678
        sanitized = maskPhoneNumber(sanitized);

        // 对身份证号脱敏：110101199001011234 → 110101********1234
        sanitized = maskIdCard(sanitized);

        return sanitized;
    }

    /**
     * 对手机号进行脱敏
     * <p>
     * 保留前3位和后4位，中间用****替代。
     * 比如：13812345678 → 138****5678
     * </p>
     *
     * @param text 可能包含手机号的文本
     * @return 脱敏后的文本
     */
    private String maskPhoneNumber(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            // 第1组：前3位，第2组：后4位，中间用****替代
            matcher.appendReplacement(sb, matcher.group(1) + "****" + matcher.group(2));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 对身份证号进行脱敏
     * <p>
     * 保留前6位和后4位，中间用********替代。
     * 比如：110101199001011234 → 110101********1234
     * </p>
     *
     * @param text 可能包含身份证号的文本
     * @return 脱敏后的文本
     */
    private String maskIdCard(String text) {
        Matcher matcher = ID_CARD_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            // 第1组：前6位，第2组：后4位，中间用********替代
            matcher.appendReplacement(sb, matcher.group(1) + "********" + matcher.group(2));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 检查请求体大小，超大请求体记录警告
     * <p>
     * 通过Content-Length请求头判断请求体大小。
     * 超过1MB的请求体可能是上传文件，也可能是恶意的大请求攻击。
     * </p>
     *
     * @param request HTTP请求对象
     */
    private void checkRequestBodySize(ServerHttpRequest request) {
        String contentLength = request.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            try {
                long size = Long.parseLong(contentLength);
                if (size > LARGE_BODY_THRESHOLD) {
                    log.warn("超大请求体警告！路径：{}，请求体大小：{}字节（{}KB），请确认是否正常",
                            request.getURI().getPath(), size, size / 1024);
                }
            } catch (NumberFormatException e) {
                // Content-Length格式不对，忽略
            }
        }
    }

    /**
     * 获取客户端真实IP地址
     * <p>
     * 请求可能经过多层代理（比如Nginx），直接取remoteAddress拿到的是代理的IP。
     * 需要从X-Forwarded-For等Header中获取真实客户端IP。
     * </p>
     * <p>
     * X-Forwarded-For格式：客户端IP, 代理1IP, 代理2IP
     * 取第一个就是真实客户端IP。
     * </p>
     *
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = null;

        // 优先从代理Header中获取真实IP
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            // X-Forwarded-For可能有多个IP，取第一个（真实客户端IP）
            ip = forwarded.split(",")[0].trim();
        }

        // 备用Header
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }

        // 最后兜底：直接取远程地址
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }

        return ip;
    }

    /**
     * 过滤器执行顺序
     * <p>
     * 设为-90，比AuthGlobalFilter（-100）大，所以鉴权先执行。
     * 这样未通过鉴权的请求会被直接拒绝，不会走到日志过滤器。
     * </p>
     * <p>
     * Filter执行顺序：RequestIdFilter(-200) → RequestLogFilter(-90) → AuthGlobalFilter(-100)
     * 注意：Spring Cloud Gateway的GlobalFilter按order值从小到大执行
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -90;
    }
}
