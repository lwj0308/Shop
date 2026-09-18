package com.shop.gateway.filter;

import com.shop.gateway.config.BlacklistConfig;
import com.shop.gateway.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RateLimitFilter 限流过滤器单元测试（B-U-15，重写版）
 * <p>
 * 这个过滤器基于"Redis + Lua 双算法"实现限流：
 * - sliding-window（ZSet 精确滑动窗口）：低频高精度场景，如认证、评论
 * - token-bucket（令牌桶）：高频接口，如秒杀、下单、商品浏览
 * </p>
 * <p>
 * 限流维度（两个维度同时检查，任一超限即拒绝）：
 * 1. 用户级别（登录用户）：按 userId 限流，防止代理池绕过 IP 限流
 * 2. 接口级别：限制单个接口每秒总请求数
 * </p>
 * <p>
 * 核心规则（项目约束）：
 * - 限流总开关 enabled=false → 直接放行
 * - 白名单标记 SKIP_RATE_LIMIT=true → 直接放行
 * - 任一维度超限 → 返回 429 Too Many Requests
 * - Redis 异常 → 降级放行（限流是弱依赖）
 * - 触发限流超过阈值（默认10次）→ 自动拉黑1小时
 * - 执行顺序：-80（在 BlacklistFilter -85 之后执行）
 * </p>
 * <p>
 * 旧版测试基于已删除的 TokenBucket 内存计数类，无法编译，这里完全重写，
 * 改为 mock ReactiveStringRedisTemplate 和 Lua 脚本，验证 Redis + Lua 逻辑。
 * </p>
 */
@DisplayName("RateLimitFilter 限流过滤器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    /** Redis 操作模板的 mock */
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    /** Redis Value 操作的 mock（opsForValue() 返回的对象，用于 autoBlock 计数） */
    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    /** Redis Set 操作的 mock（opsForSet() 返回的对象，用于 autoBlock 拉黑） */
    @Mock
    private ReactiveSetOperations<String, String> setOps;

    /** 滑动窗口限流脚本的 mock */
    @Mock
    private RedisScript<Long> rateLimitScript;

    /** 令牌桶限流脚本的 mock */
    @Mock
    private RedisScript<Long> tokenBucketScript;

    /** 限流配置的 mock */
    @Mock
    private RateLimitProperties properties;

    /** 黑白名单配置的 mock（用于 autoBlock 阈值判断） */
    @Mock
    private BlacklistConfig blacklistConfig;

    /** 过滤器链的 mock，模拟放行动作 */
    @Mock
    private GatewayFilterChain chain;

    /** 被测对象：限流过滤器 */
    private RateLimitFilter filter;

    /**
     * 每个测试前都跑一遍的初始化
     * <p>
     * 设置默认桩：
     * - 限流开启，默认算法 token-bucket，默认 QPS 50/200
     * - Lua 脚本执行返回 1L（放行）
     * - autoBlock 阈值=0（禁用自动拉黑）
     * - 没有自定义规则
     * </p>
     */
    @BeforeEach
    void setUp() {
        // 通过构造函数注入所有 mock 依赖
        filter = new RateLimitFilter(redisTemplate, rateLimitScript, tokenBucketScript,
                properties, blacklistConfig);

        // 默认放行动作
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());

        // 默认限流配置：开启、令牌桶、50/200 QPS、无自定义规则
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getDefaultAlgorithm()).thenReturn("token-bucket");
        lenient().when(properties.getDefaultIpQps()).thenReturn(50L);
        lenient().when(properties.getDefaultPathQps()).thenReturn(200L);
        lenient().when(properties.getDefaultCapacity()).thenReturn(100L);
        lenient().when(properties.getRules()).thenReturn(List.of());

        // 默认 Lua 脚本执行返回 1L（放行）
        lenient().when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(Flux.just(1L));

        // 默认 autoBlock 禁用（阈值=0）
        lenient().when(blacklistConfig.getAutoBlockThreshold()).thenReturn(0);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    /**
     * 构造一个带 X-Forwarded-For 的测试请求
     */
    private MockServerWebExchange buildExchange(String ip, String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("X-Forwarded-For", ip)
                        .build());
    }

    // ==================== 过滤器顺序测试 ====================

    @Test
    @DisplayName("getOrder：应返回 -80，在 BlacklistFilter(-85) 之后执行")
    void getOrder_shouldReturnMinus80() {
        assertThat(filter.getOrder()).isEqualTo(-80);
    }

    // ==================== 总开关测试 ====================

    @Nested
    @DisplayName("限流总开关测试")
    class EnabledSwitchTest {

        @Test
        @DisplayName("enabled=false → 直接放行，不检查 Redis")
        void disabled_shouldPassWithoutRedisCheck() {
            when(properties.isEnabled()).thenReturn(false);
            MockServerWebExchange exchange = buildExchange("1.1.1.1", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：放行
            verify(chain).filter(any());
            // 验证：未调用 Redis 执行限流脚本
            verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        }
    }

    // ==================== 白名单跳过测试 ====================

    @Nested
    @DisplayName("白名单跳过限流测试")
    class SkipByWhitelistTest {

        @Test
        @DisplayName("exchange 带 SKIP_RATE_LIMIT=true → 直接放行，不检查 Redis")
        void skipFlagSet_shouldPassWithoutRedisCheck() {
            MockServerWebExchange exchange = buildExchange("1.1.1.1", "/api/test");
            // 模拟 BlacklistFilter 设置的跳过标记
            exchange.getAttributes().put(BlacklistFilter.ATTR_SKIP_RATE_LIMIT, true);

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
            verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        }
    }

    // ==================== 正常请求放行测试 ====================

    @Nested
    @DisplayName("正常请求放行测试")
    class PassThroughTest {

        @Test
        @DisplayName("Redis 脚本返回 1L（放行）→ 请求通过")
        void redisReturnAllowed_shouldPass() {
            MockServerWebExchange exchange = buildExchange("1.1.1.1", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("Redis 脚本返回空 Flux → defaultIfEmpty(true) 放行（降级保护）")
        void redisReturnEmpty_shouldPassByDefault() {
            // 场景：Redis 执行返回空（比如脚本异常），defaultIfEmpty(true) 兜底放行
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.empty());

            MockServerWebExchange exchange = buildExchange("1.1.1.1", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
        }
    }

    // ==================== IP 维度限流测试 ====================

    @Nested
    @DisplayName("IP 维度限流测试")
    class IpLimitTest {

        @Test
        @DisplayName("第一道（IP维度）返回 0L → 返回 429，不进入第二道检查")
        void ipLimitTriggered_shouldReturn429() {
            // Lua 脚本返回 0L 表示限流
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.just(0L));

            MockServerWebExchange exchange = buildExchange("2.2.2.2", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：返回 429
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // 验证：未放行
            verify(chain, never()).filter(any());
        }
    }

    // ==================== 接口维度限流测试 ====================

    @Nested
    @DisplayName("接口维度限流测试")
    class PathLimitTest {

        @Test
        @DisplayName("第一道通过，第二道（接口维度）返回 0L → 返回 429")
        void pathLimitTriggered_shouldReturn429() {
            // 第一次调用返回 1L（IP维度通过），第二次返回 0L（接口维度限流）
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.just(1L), Flux.just(0L));

            MockServerWebExchange exchange = buildExchange("3.3.3.3", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            verify(chain, never()).filter(any());
        }
    }

    // ==================== 用户维度限流测试 ====================

    @Nested
    @DisplayName("用户维度限流测试")
    class UserLimitTest {

        @Test
        @DisplayName("登录用户（有 X-User-Id）+ userQps>0 → 使用 user 维度限流（key 含 rate_limit:user:）")
        void loggedInUser_shouldUseUserDimension() {
            // 准备：配置一条规则，启用用户维度限流
            RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
            rule.setPathPattern("/api/order/**");
            rule.setIpQps(10);
            rule.setPathQps(100);
            rule.setUserQps(5);  // 启用用户维度
            when(properties.getRules()).thenReturn(List.of(rule));

            // Lua 脚本返回 1L（放行）
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.just(1L));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list")
                            .header("X-Forwarded-For", "4.4.4.4")
                            .header("X-User-Id", "user-001")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：放行
            verify(chain).filter(any());

            // 验证：第一次 execute 的 key 是用户维度（rate_limit:user:user-001）
            // 注意：filter 会调用两次 checkLimit（第一道用户维度 + 第二道接口维度），所以 execute 被调用2次
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisTemplate, org.mockito.Mockito.times(2))
                    .execute(any(RedisScript.class), keysCaptor.capture(), any(Object[].class));
            // getAllValues() 返回所有调用的参数，第一次是第一道检查（用户维度）
            assertThat(keysCaptor.getAllValues().get(0).get(0)).startsWith("rate_limit:user:user-001");
        }

        @Test
        @DisplayName("未登录用户（无 X-User-Id）→ 使用 IP 维度限流（key 含 rate_limit:ip:）")
        void anonymousUser_shouldUseIpDimension() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.just(1L));

            MockServerWebExchange exchange = buildExchange("5.5.5.5", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：第一次 execute 的 key 是 IP 维度
            // 注意：filter 会调用两次 checkLimit（第一道IP维度 + 第二道接口维度），所以 execute 被调用2次
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisTemplate, org.mockito.Mockito.times(2))
                    .execute(any(RedisScript.class), keysCaptor.capture(), any(Object[].class));
            assertThat(keysCaptor.getAllValues().get(0).get(0)).startsWith("rate_limit:ip:5.5.5.5");
        }
    }

    // ==================== 降级策略测试（弱依赖） ====================

    @Nested
    @DisplayName("降级策略测试（Redis 异常时放行）")
    class FallbackTest {

        @Test
        @DisplayName("Redis 执行抛异常 → 降级放行，不返回 429")
        void redisException_shouldFallbackAndPass() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.error(new RuntimeException("Redis 连接失败")));

            MockServerWebExchange exchange = buildExchange("6.6.6.6", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：降级放行
            verify(chain).filter(any());
            // 验证：未返回 429
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    // ==================== 自动拉黑测试（RL-11） ====================

    @Nested
    @DisplayName("自动拉黑测试（触发限流超过阈值自动拉黑）")
    class AutoBlockTest {

        @Test
        @DisplayName("autoBlockThreshold=0 → 禁用自动拉黑，不调用 increment")
        void autoBlockDisabled_shouldNotCallIncrement() {
            // 阈值=0 禁用自动拉黑
            when(blacklistConfig.getAutoBlockThreshold()).thenReturn(0);
            // 限流触发
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.just(0L));

            MockServerWebExchange exchange = buildExchange("7.7.7.7", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：返回 429
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // 验证：未调用 increment（自动拉黑被禁用）
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("触发限流 + 计数 < 阈值 → 不拉黑，只计数")
        void belowThreshold_shouldCountButNotBlock() {
            when(blacklistConfig.getAutoBlockThreshold()).thenReturn(10);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.just(0L));  // 限流
            // 计数=5，未达阈值10
            when(valueOps.increment(anyString())).thenReturn(Mono.just(5L));

            MockServerWebExchange exchange = buildExchange("8.8.8.8", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：返回 429
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // 验证：调用了 increment 计数
            verify(valueOps).increment(anyString());
            // 验证：未调用 opsForSet().add（未拉黑）
            verify(redisTemplate, never()).opsForSet();
        }

        @Test
        @DisplayName("触发限流 + 计数 >= 阈值 → 自动拉黑（调用 opsForSet().add）")
        void exceedThreshold_shouldAutoBlock() {
            when(blacklistConfig.getAutoBlockThreshold()).thenReturn(10);
            when(blacklistConfig.getAutoBlockDuration()).thenReturn(3600L);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(Flux.just(0L));  // 限流
            // 计数=10，达到阈值
            when(valueOps.increment(anyString())).thenReturn(Mono.just(10L));
            // mock opsForSet() 返回 setOps，add 返回成功
            when(redisTemplate.opsForSet()).thenReturn(setOps);
            when(setOps.add(anyString(), anyString())).thenReturn(Mono.just(1L));
            // expire 返回成功
            when(redisTemplate.expire(anyString(), any(Duration.class)))
                    .thenReturn(Mono.just(true));

            MockServerWebExchange exchange = buildExchange("9.9.9.9", "/api/test");

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：返回 429
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // 验证：调用了 increment 计数
            verify(valueOps).increment(anyString());
            // 验证：调用了 opsForSet().add 拉黑
            verify(setOps).add(anyString(), anyString());
        }
    }
}
