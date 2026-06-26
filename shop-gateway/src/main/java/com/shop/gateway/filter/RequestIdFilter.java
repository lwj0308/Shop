package com.shop.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 请求ID过滤器
 * <p>
 * 为每个经过网关的请求生成唯一的traceId（追踪ID），
 * 并放入请求Header传递给下游服务，用于全链路追踪。
 * </p>
 * <p>
 * 小白理解：就像快递的运单号，每个包裹都有一个唯一的编号。
 * 当你在多个快递站之间转运时，只要看运单号就能追踪包裹的完整路线。
 * 在微服务中，一个请求可能经过多个服务，traceId就是用来把所有服务的日志串联起来的"运单号"。
 * </p>
 * <p>
 * 如果请求已经携带了X-Request-Id（比如前端或上游网关传入），则复用该ID，不再重新生成。
 * </p>
 */
@Slf4j
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    /** traceId的Header名称，下游服务通过这个Header获取追踪ID */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /**
     * 为请求生成或复用traceId
     * <p>
     * 执行流程：
     * 1. 检查请求是否已有X-Request-Id → 有：复用
     * 2. 没有则生成新的UUID作为traceId
     * 3. 将traceId写入请求Header，传递给下游服务
     * 4. 将traceId记录到MDC（日志上下文），方便日志中打印
     * </p>
     *
     * @param exchange WebFlux的上下文对象
     * @param chain    过滤器链
     * @return Mono<Void> 响应式返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 尝试获取已有的traceId（上游网关或前端可能已经生成了）
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER_REQUEST_ID);

        // 如果没有，生成新的traceId
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }

        // 将traceId写入请求Header，下游服务可以通过这个Header获取
        // 同时也写入响应Header，方便前端排查问题
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER_REQUEST_ID, traceId)
                .build();

        // 将traceId也写入响应Header，前端可以通过响应头看到
        exchange.getResponse().getHeaders().add(HEADER_REQUEST_ID, traceId);

        log.debug("请求追踪ID：{}，路径：{}", traceId, request.getURI().getPath());

        ServerWebExchange newExchange = exchange.mutate().request(request).build();
        return chain.filter(newExchange);
    }

    /**
     * 生成唯一的追踪ID
     * <p>
     * 使用UUID去掉横线的格式，32位十六进制字符串。
     * 格式示例：a1b2c3d4e5f67890a1b2c3d4e5f67890
     * </p>
     *
     * @return 唯一的追踪ID字符串
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 过滤器执行顺序
     * <p>
     * 设为-200，是最先执行的过滤器，确保所有后续过滤器和下游服务都能拿到traceId。
     * </p>
     * <p>
     * Filter执行顺序：RequestIdFilter(-200) → RequestLogFilter(-90) → AuthGlobalFilter(-100)
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -200;
    }
}
