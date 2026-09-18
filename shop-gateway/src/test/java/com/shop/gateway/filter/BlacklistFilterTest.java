package com.shop.gateway.filter;

import com.shop.gateway.config.BlacklistConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BlacklistFilter 黑白名单过滤器单元测试（B-U-14）
 * <p>
 * 这个过滤器像小区保安：先查"通缉令"（黑名单）抓坏人，再查"VIP名单"（白名单）让贵宾免排队。
 * 两级拦截：静态名单（Nacos 配置）→ Redis 动态名单（运行时拉黑）。
 * </p>
 * <p>
 * 核心规则（项目约束）：
 * - 黑名单命中 → 403 Forbidden
 * - 白名单命中 → 放行，并设置 SKIP_RATE_LIMIT 标记跳过限流
 * - Redis 异常 → 降级放行（黑白名单是弱依赖，不能因为 Redis 挂了导致全站不可用）
 * - 执行顺序：-85（在 RateLimitFilter -80 之前执行）
 * </p>
 * <p>
 * 测试用 Mock 模拟 ReactiveStringRedisTemplate 和 BlacklistConfig，
 * 不需要真的连 Redis 和 Nacos，只验证过滤器的判断逻辑对不对。
 * </p>
 */
@DisplayName("BlacklistFilter 黑白名单过滤器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlacklistFilterTest {

    /** Redis 操作模板的 mock */
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    /** Redis Set 操作的 mock（opsForSet() 返回的对象） */
    @Mock
    private ReactiveSetOperations<String, String> setOps;

    /** 黑白名单配置的 mock（模拟从 Nacos 读取的静态名单） */
    @Mock
    private BlacklistConfig config;

    /** 过滤器链的 mock，模拟放行动作 */
    @Mock
    private GatewayFilterChain chain;

    /** 被测对象：黑白名单过滤器 */
    private BlacklistFilter filter;

    /**
     * 每个测试前都跑一遍的初始化
     * <p>
     * 用 lenient() 设置默认桩，避免严格模式下"桩未被使用"的报错。
     * 默认所有名单都为空、所有 Redis 检查都返回 false（不在名单中），
     * 这样每个测试只需要改自己关心的那个桩。
     * </p>
     */
    @BeforeEach
    void setUp() {
        filter = new BlacklistFilter(redisTemplate, config);
        // 默认放行动作返回 Mono.empty()
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
        // 默认 opsForSet() 返回我们的 mock
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        // 默认所有 Redis 名单检查都返回 false（不在名单中）
        lenient().when(setOps.isMember(anyString(), anyString())).thenReturn(Mono.just(false));
        // 默认所有静态名单都为空
        lenient().when(config.getIpBlacklist()).thenReturn(List.of());
        lenient().when(config.getUserBlacklist()).thenReturn(List.of());
        lenient().when(config.getIpWhitelist()).thenReturn(List.of());
        lenient().when(config.getUserWhitelist()).thenReturn(List.of());
    }

    // ==================== 过滤器顺序测试 ====================

    @Test
    @DisplayName("getOrder：应返回 -85，在 RateLimitFilter(-80) 之前执行")
    void getOrder_shouldReturnMinus85() {
        assertThat(filter.getOrder()).isEqualTo(-85);
    }

    // ==================== 静态黑名单测试（Nacos 配置） ====================

    @Nested
    @DisplayName("静态黑名单检查（Nacos 配置）")
    class StaticBlacklistTest {

        @Test
        @DisplayName("IP 命中静态黑名单 → 返回 403，不进入 Redis 检查")
        void staticIpBlacklist_shouldReturn403() {
            // 准备：静态 IP 黑名单包含 1.1.1.1
            when(config.getIpBlacklist()).thenReturn(List.of("1.1.1.1"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "1.1.1.1")
                            .build());

            // 执行
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：返回 403
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            // 验证：未放行
            verify(chain, never()).filter(any());
            // 验证：未进入 Redis 检查（静态黑名单优先）
            verify(setOps, never()).isMember(anyString(), anyString());
        }

        @Test
        @DisplayName("用户 ID 命中静态黑名单 → 返回 403")
        void staticUserBlacklist_shouldReturn403() {
            // 准备：静态用户黑名单包含 user123
            when(config.getUserBlacklist()).thenReturn(List.of("user123"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "9.9.9.9")
                            .header("X-User-Id", "user123")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("静态黑名单为 null → 不抛异常，继续后续检查")
        void staticBlacklistNull_shouldNotThrow() {
            // 准备：静态黑名单为 null（边界场景）
            when(config.getIpBlacklist()).thenReturn(null);
            when(config.getUserBlacklist()).thenReturn(null);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "1.1.1.1")
                            .build());

            // 执行：应正常完成，不抛 NPE
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：放行（所有名单都未命中）
            verify(chain).filter(any());
        }
    }

    // ==================== 静态白名单测试（Nacos 配置） ====================

    @Nested
    @DisplayName("静态白名单检查（Nacos 配置）")
    class StaticWhitelistTest {

        @Test
        @DisplayName("IP 命中静态白名单 → 放行，设置 SKIP_RATE_LIMIT=true")
        void staticIpWhitelist_shouldPassAndSetSkipFlag() {
            when(config.getIpWhitelist()).thenReturn(List.of("10.0.0.1"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "10.0.0.1")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：放行
            verify(chain).filter(any());
            // 验证：设置了跳过限流标记
            assertThat((Boolean) exchange.getAttribute(BlacklistFilter.ATTR_SKIP_RATE_LIMIT))
                    .isEqualTo(true);
            // 验证：未进入 Redis 检查
            verify(setOps, never()).isMember(anyString(), anyString());
        }

        @Test
        @DisplayName("用户 ID 命中静态白名单 → 放行，设置 SKIP_RATE_LIMIT=true")
        void staticUserWhitelist_shouldPassAndSetSkipFlag() {
            when(config.getUserWhitelist()).thenReturn(List.of("admin-user"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "9.9.9.9")
                            .header("X-User-Id", "admin-user")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
            assertThat((Boolean) exchange.getAttribute(BlacklistFilter.ATTR_SKIP_RATE_LIMIT))
                    .isEqualTo(true);
        }
    }

    // ==================== Redis 动态黑名单测试 ====================

    @Nested
    @DisplayName("Redis 动态黑名单检查")
    class RedisBlacklistTest {

        @Test
        @DisplayName("IP 命中 Redis 动态黑名单 → 返回 403")
        void redisIpBlacklist_shouldReturn403() {
            // 准备：Redis IP 黑名单包含 2.2.2.2
            when(setOps.isMember(BlacklistFilter.REDIS_IP_BLACKLIST, "2.2.2.2"))
                    .thenReturn(Mono.just(true));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "2.2.2.2")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("用户 ID 命中 Redis 动态黑名单 → 返回 403")
        void redisUserBlacklist_shouldReturn403() {
            // 准备：Redis 用户黑名单包含 banned-user
            // 注意：REDIS_USER_BLACKLIST 是 private 常量，这里用字符串值 "blacklist:user"
            when(setOps.isMember("blacklist:user", "banned-user"))
                    .thenReturn(Mono.just(true));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "8.8.8.8")
                            .header("X-User-Id", "banned-user")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(chain, never()).filter(any());
        }
    }

    // ==================== Redis 动态白名单测试 ====================

    @Nested
    @DisplayName("Redis 动态白名单检查")
    class RedisWhitelistTest {

        @Test
        @DisplayName("IP 命中 Redis 动态白名单 → 放行，设置 SKIP_RATE_LIMIT=true")
        void redisIpWhitelist_shouldPassAndSetSkipFlag() {
            when(setOps.isMember("whitelist:ip", "192.168.0.1"))
                    .thenReturn(Mono.just(true));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "192.168.0.1")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
            assertThat((Boolean) exchange.getAttribute(BlacklistFilter.ATTR_SKIP_RATE_LIMIT))
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("IP 和用户都未命中任何名单 → 放行，不设置 SKIP_RATE_LIMIT")
        void normalRequest_shouldPassWithoutSkipFlag() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "3.3.3.3")
                            .header("X-User-Id", "normal-user")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
            // 验证：未设置跳过限流标记
            assertThat((Boolean) exchange.getAttribute(BlacklistFilter.ATTR_SKIP_RATE_LIMIT))
                    .isNull();
        }
    }

    // ==================== 降级策略测试（弱依赖） ====================

    @Nested
    @DisplayName("降级策略测试（Redis 异常时放行）")
    class FallbackTest {

        @Test
        @DisplayName("Redis 抛异常 → 降级放行，不返回 403")
        void redisException_shouldFallbackAndPass() {
            // 准备：Redis isMember 抛异常
            when(setOps.isMember(anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Redis 连接失败")));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "4.4.4.4")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：降级放行
            verify(chain).filter(any());
            // 验证：未返回 403
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    // ==================== 客户端 IP 解析测试 ====================

    @Nested
    @DisplayName("客户端 IP 解析测试")
    class ClientIpTest {

        @Test
        @DisplayName("X-Forwarded-For 包含多个 IP → 取第一个（客户端真实 IP）")
        void xForwardedForMultipleIps_shouldUseFirst() {
            // 准备：静态黑名单包含第一个 IP
            when(config.getIpBlacklist()).thenReturn(List.of("1.1.1.1"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：取第一个 IP（1.1.1.1），命中黑名单，返回 403
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("无 X-Forwarded-For 但有 X-Real-IP → 使用 X-Real-IP")
        void xRealIp_shouldBeUsed() {
            when(config.getIpBlacklist()).thenReturn(List.of("5.5.5.5"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Real-IP", "5.5.5.5")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("无代理 Header → 使用 RemoteAddress")
        void remoteAddress_shouldBeUsed() {
            when(config.getIpBlacklist()).thenReturn(List.of("6.6.6.6"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .remoteAddress(new InetSocketAddress("6.6.6.6", 8080))
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
