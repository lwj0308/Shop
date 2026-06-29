package com.shop.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * XssFilter XSS防护过滤器单元测试
 * <p>
 * 这个过滤器就像留言板审核员，检查请求里有没有恶意代码（XSS攻击）。
 * 发现恶意代码就直接拒绝（返回403），没有才放行。
 * </p>
 * <p>
 * 注意：根据源码，XssFilter 只检查 URL 的查询参数（query）和路径（path），
 * 不检查请求头（Header）。所以 Header 里的 XSS 字符不会被拦截，请求会放行。
 * 如果业务需要检查 Header，需要修改 XssFilter 源码。
 * </p>
 * <p>
 * 测试用的 XSS payload 选择 "javascript:alert(1)" 和 "onerror=alert(1)"，
 * 因为它们是常见的 XSS 攻击模式，且不含 URL 非法字符（&lt;&gt; 在 URL 中会被拒绝解析）。
 * </p>
 */
@DisplayName("XssFilter XSS防护过滤器测试")
@ExtendWith(MockitoExtension.class)
class XssFilterTest {

    /** 过滤器链的 mock，模拟放行动作 */
    @Mock
    private GatewayFilterChain chain;

    /** 被测对象：XSS防护过滤器 */
    private XssFilter filter;

    @BeforeEach
    void setUp() {
        filter = new XssFilter();
        // 默认放行动作返回 Mono.empty()，用 lenient 避免严格模式报错
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ==================== 过滤器顺序测试 ====================

    @Test
    @DisplayName("getOrder：应返回 -70，在限流过滤器之后执行")
    void getOrder_shouldReturnMinus70() {
        assertThat(filter.getOrder()).isEqualTo(-70);
    }

    // ==================== 正常请求测试 ====================

    @Nested
    @DisplayName("正常请求处理")
    class NormalRequestTest {

        @Test
        @DisplayName("不含XSS字符的正常请求应放行")
        void normalRequest_shouldPass() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
        }

        @Test
        @DisplayName("带正常查询参数的请求应放行")
        void normalQuery_shouldPass() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test?name=shop&page=1").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
        }

        @Test
        @DisplayName("无查询参数的请求应放行")
        void noQuery_shouldPass() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/product/list").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
        }
    }

    // ==================== XSS攻击拦截测试 ====================

    @Nested
    @DisplayName("XSS攻击拦截处理")
    class XssDetectionTest {

        @Test
        @DisplayName("查询参数含 javascript: 协议（XSS攻击），应拦截返回403")
        void javascriptInQuery_shouldReturn403() {
            // javascript: 协议可以在链接中执行JS代码，是经典XSS攻击
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test?q=javascript:alert(1)").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：未放行
            verify(chain, never()).filter(any());
            // 验证：返回403禁止访问
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("查询参数含 onerror 事件（XSS攻击），应拦截返回403")
        void onerrorInQuery_shouldReturn403() {
            // onerror 是HTML事件属性，可以触发JS执行，是XSS攻击模式
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test?q=onerror=alert(1)").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("查询参数含 eval() 调用（XSS攻击），应拦截返回403")
        void evalInQuery_shouldReturn403() {
            // eval() 可以执行任意JS代码，是危险函数
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test?q=eval(alert(1))").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("查询参数含 onload 事件（XSS攻击），应拦截返回403")
        void onloadInQuery_shouldReturn403() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test?q=onload=alert(1)").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ==================== Header中的XSS测试 ====================

    @Nested
    @DisplayName("Header中的XSS字符处理")
    class HeaderXssTest {

        @Test
        @DisplayName("Header含XSS字符时请求仍放行（XssFilter只检查query和path，不检查Header）")
        void xssInHeader_shouldPassBecauseFilterDoesNotCheckHeader() {
            // Header 中含 javascript: 协议，但 XssFilter 不检查 Header
            // 所以请求会被放行。这是源码的实际行为。
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Custom-Header", "javascript:alert(1)")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：请求被放行（XssFilter不检查Header）
            verify(chain).filter(any());
            // 验证：响应状态码不是403
            assertThat(exchange.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Header含onerror字符时请求仍放行（XssFilter不检查Header）")
        void onerrorInHeader_shouldPass() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Token", "onerror=alert(1)")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
        }
    }
}
