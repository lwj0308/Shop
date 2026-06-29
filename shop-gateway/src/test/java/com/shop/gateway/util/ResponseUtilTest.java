package com.shop.gateway.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResponseUtil 网关响应工具类单元测试
 * <p>
 * 这个工具类负责在网关层直接返回 JSON 格式的错误响应。
 * 因为网关是 WebFlux 响应式，不能像普通 Controller 那样 return 对象，
 * 需要手动把 JSON 字符串写入响应流。
 * </p>
 * <p>
 * 测试要点：
 * 1. 验证返回的 HTTP 状态码是否正确（401/403/429）
 * 2. 验证响应体是 JSON 格式，包含正确的 code 和 message
 * 3. 验证 Content-Type 是 application/json
 * </p>
 * <p>
 * 提取响应体的小技巧：
 * MockServerHttpResponse 把写入的数据缓存在 Flux 里，
 * 用 DataBufferUtils.join 合并成一个 buffer，再转成字符串就能读取内容。
 * </p>
 */
@DisplayName("ResponseUtil 网关响应工具类测试")
class ResponseUtilTest {

    /**
     * 从 MockServerWebExchange 的响应中提取 body 字符串
     * <p>
     * 因为响应是响应式的（Flux&lt;DataBuffer&gt;），需要先合并所有数据块再转字符串。
     * 就像把一封拆成几封信的内容拼回成一封完整的信。
     * </p>
     *
     * @param exchange 测试用的 WebFlux 上下文
     * @return 响应体字符串
     */
    private String getResponseBody(MockServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getResponse().getBody())
                .map(buffer -> {
                    // 把数据块转成UTF-8字符串
                    String content = buffer.toString(StandardCharsets.UTF_8);
                    // 释放数据块，避免内存泄漏
                    DataBufferUtils.release(buffer);
                    return content;
                })
                .block();
    }

    // ==================== writeErrorResponse 测试 ====================

    @Nested
    @DisplayName("writeErrorResponse 通用错误响应")
    class ErrorResponseTest {

        @Test
        @DisplayName("写入401未登录响应：状态码、code、message、Content-Type都应正确")
        void writeErrorResponse_shouldReturn401WithCorrectJson() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());

            // 调用 writeErrorResponse，写入401响应
            StepVerifier.create(ResponseUtil.writeErrorResponse(
                            exchange, HttpStatus.UNAUTHORIZED, 401, "未登录"))
                    .expectComplete()
                    .verify();

            // 验证：HTTP 状态码是 401
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            // 验证：Content-Type 是 application/json
            assertThat(exchange.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
            // 验证：响应体是 JSON，包含正确的字段
            String body = getResponseBody(exchange);
            assertThat(body).contains("\"code\":401");
            assertThat(body).contains("\"message\":\"未登录\"");
            assertThat(body).contains("\"data\":null");
            assertThat(body).contains("\"timestamp\"");
        }

        @Test
        @DisplayName("写入自定义状态码和业务码：应正确返回指定的httpStatus和code")
        void writeErrorResponse_withCustomCode() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());

            // 写入500状态码，业务码50001
            StepVerifier.create(ResponseUtil.writeErrorResponse(
                            exchange, HttpStatus.INTERNAL_SERVER_ERROR, 50001, "系统异常"))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            String body = getResponseBody(exchange);
            assertThat(body).contains("\"code\":50001");
            assertThat(body).contains("\"message\":\"系统异常\"");
        }
    }

    // ==================== writeRateLimitResponse 测试 ====================

    @Nested
    @DisplayName("writeRateLimitResponse 限流响应")
    class RateLimitResponseTest {

        @Test
        @DisplayName("写入限流响应：应返回429状态码和429业务码")
        void writeRateLimitResponse_shouldReturn429() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());

            StepVerifier.create(ResponseUtil.writeRateLimitResponse(
                            exchange, "请求太频繁，请稍后再试"))
                    .expectComplete()
                    .verify();

            // 验证：HTTP 状态码是 429（Too Many Requests）
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // 验证：Content-Type 是 application/json
            assertThat(exchange.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
            // 验证：响应体包含 429 业务码和提示信息
            String body = getResponseBody(exchange);
            assertThat(body).contains("\"code\":429");
            assertThat(body).contains("\"message\":\"请求太频繁，请稍后再试\"");
            assertThat(body).contains("\"data\":null");
            assertThat(body).contains("\"timestamp\"");
        }
    }

    // ==================== writeForbiddenResponse 测试 ====================

    @Nested
    @DisplayName("writeForbiddenResponse 禁止访问响应")
    class ForbiddenResponseTest {

        @Test
        @DisplayName("写入禁止访问响应：应返回403状态码和403业务码")
        void writeForbiddenResponse_shouldReturn403() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());

            StepVerifier.create(ResponseUtil.writeForbiddenResponse(
                            exchange, "请求包含非法字符"))
                    .expectComplete()
                    .verify();

            // 验证：HTTP 状态码是 403（Forbidden）
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            // 验证：Content-Type 是 application/json
            assertThat(exchange.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
            // 验证：响应体包含 403 业务码和提示信息
            String body = getResponseBody(exchange);
            assertThat(body).contains("\"code\":403");
            assertThat(body).contains("\"message\":\"请求包含非法字符\"");
            assertThat(body).contains("\"data\":null");
            assertThat(body).contains("\"timestamp\"");
        }
    }

    // ==================== Content-Type 验证 ====================

    @Nested
    @DisplayName("Content-Type 响应头验证")
    class ContentTypeTest {

        @Test
        @DisplayName("所有响应方法的Content-Type都应是 application/json")
        void allResponses_shouldHaveJsonContentType() {
            // 测试 writeErrorResponse
            MockServerWebExchange exchange1 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            StepVerifier.create(ResponseUtil.writeErrorResponse(
                            exchange1, HttpStatus.UNAUTHORIZED, 401, "未登录"))
                    .expectComplete()
                    .verify();
            assertThat(exchange1.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);

            // 测试 writeRateLimitResponse
            MockServerWebExchange exchange2 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            StepVerifier.create(ResponseUtil.writeRateLimitResponse(
                            exchange2, "限流"))
                    .expectComplete()
                    .verify();
            assertThat(exchange2.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);

            // 测试 writeForbiddenResponse
            MockServerWebExchange exchange3 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            StepVerifier.create(ResponseUtil.writeForbiddenResponse(
                            exchange3, "禁止"))
                    .expectComplete()
                    .verify();
            assertThat(exchange3.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
        }

        @Test
        @DisplayName("响应体应能被解析为合法JSON格式")
        void responseBody_shouldBeValidJson() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            StepVerifier.create(ResponseUtil.writeErrorResponse(
                            exchange, HttpStatus.BAD_REQUEST, 400, "参数错误"))
                    .expectComplete()
                    .verify();

            String body = getResponseBody(exchange);
            // 验证：是合法JSON（以 { 开头，以 } 结尾）
            assertThat(body).startsWith("{").endsWith("}");
            // 验证：包含Result<T>的所有字段
            assertThat(body).contains("\"code\"");
            assertThat(body).contains("\"message\"");
            assertThat(body).contains("\"data\"");
            assertThat(body).contains("\"timestamp\"");
        }
    }
}
