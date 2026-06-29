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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RateLimitFilter 限流过滤器单元测试
 * <p>
 * 这个过滤器基于"令牌桶"算法实现限流，就像游乐园过山车每小时最多接待500人，
 * 超过的人就得等下一轮。这里测试令牌桶的计数、正常放行、超限拦截、多IP独立计数。
 * </p>
 * <p>
 * 注意：TokenBucket 是 RateLimitFilter 的私有内部类，无法直接访问，
 * 所以用 Java 反射机制来测试它（就像从窗外偷看屋里的东西）。
 * </p>
 */
@DisplayName("RateLimitFilter 限流过滤器测试")
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    /** 过滤器链的 mock，模拟放行动作 */
    @Mock
    private GatewayFilterChain chain;

    /** 被测对象：限流过滤器 */
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // 默认放行动作返回 Mono.empty()，用 lenient 避免严格模式报错
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ==================== 过滤器顺序测试 ====================

    @Test
    @DisplayName("getOrder：应返回 -80，在鉴权过滤器之后执行")
    void getOrder_shouldReturnMinus80() {
        assertThat(filter.getOrder()).isEqualTo(-80);
    }

    // ==================== TokenBucket 内部类测试（用反射） ====================

    @Nested
    @DisplayName("TokenBucket 令牌桶计数测试")
    class TokenBucketTest {

        /**
         * 通过反射创建 TokenBucket 实例
         * <p>
         * TokenBucket 是私有内部类，外部无法直接 new，
         * 只能通过反射"撬开"它的构造器来创建实例。
         * </p>
         */
        private Object createTokenBucket() throws Exception {
            Class<?> tokenBucketClass = Class.forName("com.shop.gateway.filter.RateLimitFilter$TokenBucket");
            Constructor<?> constructor = tokenBucketClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }

        /**
         * 通过反射调用 TokenBucket 的 increment 方法
         */
        private long invokeIncrement(Object bucket) throws Exception {
            Method incrementMethod = bucket.getClass().getDeclaredMethod("increment");
            incrementMethod.setAccessible(true);
            return (long) incrementMethod.invoke(bucket);
        }

        @Test
        @DisplayName("increment：连续递增应返回递增后的值（1, 2, 3...）")
        void increment_shouldIncreaseSequentially() throws Exception {
            // 创建一个令牌桶
            Object bucket = createTokenBucket();

            // 第一次递增，应返回1
            assertThat(invokeIncrement(bucket)).isEqualTo(1L);
            // 第二次递增，应返回2
            assertThat(invokeIncrement(bucket)).isEqualTo(2L);
            // 第三次递增，应返回3
            assertThat(invokeIncrement(bucket)).isEqualTo(3L);
        }

        @Test
        @DisplayName("increment：递增100次，最后一次应返回100")
        void increment_manyTimes_shouldReturnCount() throws Exception {
            Object bucket = createTokenBucket();
            long last = 0;
            for (int i = 0; i < 100; i++) {
                last = invokeIncrement(bucket);
            }
            assertThat(last).isEqualTo(100L);
        }

        @Test
        @DisplayName("increment：多个令牌桶独立计数，互不影响")
        void increment_multipleBuckets_shouldCountIndependently() throws Exception {
            // 创建两个独立的令牌桶（模拟两个IP各自计数）
            Object bucket1 = createTokenBucket();
            Object bucket2 = createTokenBucket();

            // bucket1 递增3次
            invokeIncrement(bucket1);
            invokeIncrement(bucket1);
            long bucket1Count = invokeIncrement(bucket1);
            // bucket2 递增1次
            long bucket2Count = invokeIncrement(bucket2);

            // 两个桶的计数独立
            assertThat(bucket1Count).isEqualTo(3L);
            assertThat(bucket2Count).isEqualTo(1L);
        }
    }

    // ==================== 正常请求测试 ====================

    @Nested
    @DisplayName("正常请求处理")
    class NormalRequestTest {

        @Test
        @DisplayName("单个正常请求（未超限）应放行")
        void normalRequest_shouldPass() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "192.168.1.1")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
        }

        @Test
        @DisplayName("同一IP连续50次请求（刚好达到限制但未超限）应都放行")
        void withinLimit_shouldPass() {
            String ip = "192.168.1.100";
            // 50次请求，计数从1到50，都不超过50（50 > 50 为false）
            for (int i = 0; i < 50; i++) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/test")
                                .header("X-Forwarded-For", ip)
                                .build());
                StepVerifier.create(filter.filter(exchange, chain))
                        .expectComplete()
                        .verify();
            }
            // 50次都放行
            verify(chain, times(50)).filter(any());
        }
    }

    // ==================== 超限请求测试 ====================

    @Nested
    @DisplayName("超限请求处理")
    class ExceedLimitTest {

        @Test
        @DisplayName("同一IP每秒请求超过50次，第51次应返回429限流响应")
        void exceedIpLimit_shouldReturn429() {
            String ip = "10.0.0.1";
            // 前50次请求都放行
            for (int i = 0; i < 50; i++) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/test")
                                .header("X-Forwarded-For", ip)
                                .build());
                StepVerifier.create(filter.filter(exchange, chain))
                        .expectComplete()
                        .verify();
            }

            // 第51次请求，IP计数达到51 > 50，触发限流
            MockServerWebExchange blockedExchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", ip)
                            .build());
            StepVerifier.create(filter.filter(blockedExchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：返回429状态码（Too Many Requests）
            assertThat(blockedExchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // 验证：chain.filter 只被调用50次（第51次被拦截未放行）
            verify(chain, times(50)).filter(any());
        }

        @Test
        @DisplayName("不同IP的请求计数相互独立，一个IP超限不影响另一个IP")
        void differentIp_shouldCountIndependently() {
            // IP1 发送50次请求（达到限制但未超限），都放行
            for (int i = 0; i < 50; i++) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/test")
                                .header("X-Forwarded-For", "1.1.1.1")
                                .build());
                StepVerifier.create(filter.filter(exchange, chain))
                        .expectComplete()
                        .verify();
            }
            // 此时IP1已经发了50次

            // IP2 发送1次请求，应放行（独立计数，不受IP1影响）
            MockServerWebExchange exchange2 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "2.2.2.2")
                            .build());
            StepVerifier.create(filter.filter(exchange2, chain))
                    .expectComplete()
                    .verify();

            // 验证：IP2 的请求被放行（chain.filter 共调用51次：IP1的50次 + IP2的1次）
            verify(chain, times(51)).filter(any());
            // IP2 的响应不是429
            assertThat(exchange2.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
