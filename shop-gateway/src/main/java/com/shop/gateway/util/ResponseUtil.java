package com.shop.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关响应工具类
 * <p>
 * 在网关层直接返回JSON格式的错误响应。
 * 因为网关是WebFlux响应式，不能像普通Controller那样直接返回对象，
 * 需要手动把JSON字符串写入响应流。
 * </p>
 * <p>
 * 响应格式和 shop-common 中的 Result&lt;T&gt; 完全一致：
 * {"code":401, "message":"未登录", "data":null, "timestamp":1234567890}
 * </p>
 * <p>
 * 小白理解：普通Controller可以return一个对象，Spring自动帮你转成JSON。
 * 但网关不一样，它用的是响应式编程，你得自己"动手"把数据写进响应里，
 * 就像自己往信封里塞信一样，不能指望别人帮你做。
 * </p>
 */
@Slf4j
public class ResponseUtil {

    /** JSON序列化工具，把Java对象转成JSON字符串 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 向客户端写入JSON格式的错误响应
     * <p>
     * 响应格式和 shop-common 中的 Result&lt;T&gt; 保持一致：
     * {"code":401, "message":"未登录", "data":null, "timestamp":1234567890}
     * </p>
     *
     * @param exchange   WebFlux的上下文对象，可以拿到请求和响应
     * @param httpStatus HTTP状态码，比如401、403
     * @param code       业务状态码，和Result中的code对应
     * @param message    提示信息，比如"未登录"、"无权限"
     * @return Mono<Void> 响应式编程的返回值，表示一个异步操作
     */
    public static Mono<Void> writeErrorResponse(ServerWebExchange exchange,
                                                 HttpStatus httpStatus,
                                                 int code,
                                                 String message) {
        // 按照Result<T>的格式构建响应数据，字段顺序和Result类一致
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        result.put("data", null);
        result.put("timestamp", System.currentTimeMillis());

        return writeJsonResponse(exchange, httpStatus, result);
    }

    /**
     * 向客户端写入JSON格式的限流响应（429 Too Many Requests）
     * <p>
     * 当请求频率超过限制时返回此响应。
     * 响应格式和 Result&lt;T&gt; 保持一致。
     * </p>
     *
     * @param exchange WebFlux的上下文对象
     * @param message  提示信息，比如"请求太频繁，请稍后再试"
     * @return Mono<Void> 响应式编程的返回值
     */
    public static Mono<Void> writeRateLimitResponse(ServerWebExchange exchange, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 429);
        result.put("message", message);
        result.put("data", null);
        result.put("timestamp", System.currentTimeMillis());

        return writeJsonResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, result);
    }

    /**
     * 向客户端写入JSON格式的禁止访问响应（403 Forbidden）
     * <p>
     * 当请求包含危险内容（如XSS攻击）时返回此响应。
     * 响应格式和 Result&lt;T&gt; 保持一致。
     * </p>
     *
     * @param exchange WebFlux的上下文对象
     * @param message  提示信息，比如"请求包含非法字符"
     * @return Mono<Void> 响应式编程的返回值
     */
    public static Mono<Void> writeForbiddenResponse(ServerWebExchange exchange, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 403);
        result.put("message", message);
        result.put("data", null);
        result.put("timestamp", System.currentTimeMillis());

        return writeJsonResponse(exchange, HttpStatus.FORBIDDEN, result);
    }

    /**
     * 通用JSON响应写入方法
     * <p>
     * 将Map对象序列化为JSON字符串，写入响应流。
     * 这是所有writeXxxResponse方法的底层实现。
     * </p>
     *
     * @param exchange   WebFlux的上下文对象
     * @param httpStatus HTTP状态码
     * @param result     响应数据Map
     * @return Mono<Void> 响应式编程的返回值
     */
    private static Mono<Void> writeJsonResponse(ServerWebExchange exchange,
                                                 HttpStatus httpStatus,
                                                 Map<String, Object> result) {
        try {
            // 把Map转成JSON字符串
            String jsonBody = OBJECT_MAPPER.writeValueAsString(result);

            // 设置响应头：告诉浏览器返回的是JSON格式的数据，编码是UTF-8
            exchange.getResponse().setStatusCode(httpStatus);
            exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

            // 把JSON字符串写入响应体（就像把信塞进信封）
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(jsonBody.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            // JSON序列化失败时，记录错误日志并返回简单的错误信息
            log.error("网关响应JSON序列化失败", e);
            exchange.getResponse().setStatusCode(httpStatus);
            DataBuffer buffer = exchange.getResponse().bufferFactory()
                    .wrap(("{\"code\":" + result.get("code") + ",\"message\":\"" + result.get("message") + "\"}").getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }
}
