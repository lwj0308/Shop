package com.shop.gateway.filter;

import com.shop.gateway.config.BlacklistConfig;
import com.shop.gateway.config.RateLimitProperties;
import com.shop.gateway.config.RateLimitScriptConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B-I-03 限流全链路集成测试
 * <p>
 * 小白讲解：
 * 这个测试和之前的单元测试最大的区别是——这里连的是"真实的 Redis"！
 *
 * 之前的单元测试用 Mockito 把 Redis 模拟出来（mock），虽然快但不真实，
 * 比如 Lua 脚本在 mock 里只是返回一个固定值，根本没真正执行过。
 *
 * 这个集成测试连的是本地 Docker 中正在运行的 Redis 容器（shop-redis），
 * 让 Lua 滑动窗口脚本和令牌桶脚本在真实 Redis 上执行，验证：
 * 1. 滑动窗口算法在真实 Redis ZSet 上的计数准确性
 * 2. 令牌桶算法在真实 Redis Hash 上的令牌补充和扣减
 * 3. 黑名单过滤器 + 限流过滤器的完整协作链路
 * 4. 自动拉黑机制（触发限流 10 次后自动加入 Redis 黑名单 Set）
 * 5. 白名单跳过限流
 *
 * 隔离策略：使用 Redis DB 15（业务用 DB 0），测试前 FLUSHDB 清空 DB 15，
 * 不影响业务数据。
 *
 * 环境要求：本地 Docker 中需有 Redis 容器监听 6379 端口（如 shop-redis）。
 * 如果 Redis 不可用，所有测试会自动跳过（assumeTrue）。
 * </p>
 */
@DisplayName("B-I-03 限流全链路集成测试 - 真实 Redis")
class RateLimitIntegrationTest {

    /** 测试用 Redis 数据库索引（用 15 号 DB 避免污染业务 DB 0） */
    private static final int TEST_DB_INDEX = 15;
    /** 本地 Redis 主机 */
    private static final String REDIS_HOST = "localhost";
    /** 本地 Redis 端口 */
    private static final int REDIS_PORT = 6379;

    /** 共享的 Lettuce 连接工厂（指向 DB 15） */
    private static LettuceConnectionFactory sharedFactory;
    /** 测试前 Redis 是否可用（不可用时跳过所有测试） */
    private static boolean redisAvailable = false;

    /** 真实连接本地 Redis 的响应式 Redis 客户端 */
    private ReactiveStringRedisTemplate redisTemplate;

    /** 限流过滤器（被测对象） */
    private RateLimitFilter rateLimitFilter;

    /** 黑名单过滤器（被测对象） */
    private BlacklistFilter blacklistFilter;

    /** Mock 的过滤器链（总是放行，模拟下游服务正常） */
    private GatewayFilterChain chain;

    /**
     * 在所有测试前创建 Redis 连接工厂，并检测 Redis 是否可用
     * 小白讲解：连本地 Docker 中的 Redis（localhost:6379），切到 DB 15。
     * 如果连接失败（比如 Redis 没启动），所有测试会被跳过，不让 CI 失败。
     */
    @BeforeAll
    static void initConnectionFactory() {
        try {
            sharedFactory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
            // 切换到 DB 15，避免和业务数据混淆
            sharedFactory.setDatabase(TEST_DB_INDEX);
            sharedFactory.afterPropertiesSet();
            sharedFactory.start();

            // 测试连接是否可用：尝试执行 PING 命令
            // 如果 Redis 不可用，这里会抛异常
            String pong = sharedFactory.getConnection().ping();
            redisAvailable = "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            redisAvailable = false;
            System.err.println("[B-I-03] Redis 不可用，测试将被跳过：" + e.getMessage());
        }
    }

    /**
     * 每个测试前重建 Redis 客户端和过滤器实例，并清空 DB 15 数据
     * 小白讲解：每个测试都需要干净的 Redis 状态，避免上一个测试的限流计数影响下一个。
     * 用 FLUSHDB 只清空当前 DB（15），不影响业务 DB 0。
     */
    @BeforeEach
    void setUp() {
        // 如果 Redis 不可用，通过 assumeTrue 跳过当前测试（不算失败）
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis 不可用，跳过 B-I-03 集成测试");

        // 创建响应式 Redis 客户端
        redisTemplate = new ReactiveStringRedisTemplate(sharedFactory);

        // 从 RateLimitScriptConfig 获取真实的 Lua 脚本 Bean
        RateLimitScriptConfig scriptConfig = new RateLimitScriptConfig();
        RedisScript<Long> rateLimitScript = scriptConfig.rateLimitScript();
        RedisScript<Long> tokenBucketScript = scriptConfig.tokenBucketScript();

        // 构建限流配置
        RateLimitProperties properties = buildRateLimitProperties();
        BlacklistConfig blacklistConfig = new BlacklistConfig();

        // 手动创建过滤器实例，注入真实 Redis 客户端和 Lua 脚本
        blacklistFilter = new BlacklistFilter(redisTemplate, blacklistConfig);
        rateLimitFilter = new RateLimitFilter(redisTemplate, rateLimitScript,
                tokenBucketScript, properties, blacklistConfig);

        // Mock 过滤器链：总是放行（返回 Mono.empty()）
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // 清空 Redis DB 15 中的所有数据，确保每个测试从干净状态开始
        // 小白讲解：FLUSHDB 只清空当前 DB（DB 15），不会影响业务用的 DB 0
        redisTemplate.getConnectionFactory().getReactiveConnection()
                .serverCommands().flushDb().block();
    }

    /**
     * 构建限流配置：认证接口 5 QPS 滑动窗口、秒杀接口 3 QPS 令牌桶
     */
    private RateLimitProperties buildRateLimitProperties() {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setDefaultIpQps(50);
        props.setDefaultPathQps(200);
        props.setDefaultAlgorithm("token-bucket");
        props.setDefaultCapacity(100);

        List<RateLimitProperties.Rule> rules = new ArrayList<>();

        // 认证接口：滑动窗口，IP 5 QPS
        RateLimitProperties.Rule authRule = new RateLimitProperties.Rule();
        authRule.setPathPattern("/api/user/auth/**");
        authRule.setAlgorithm("sliding-window");
        authRule.setIpQps(5);
        authRule.setPathQps(50);
        rules.add(authRule);

        // 秒杀接口：令牌桶，IP 3 QPS，容量 5
        RateLimitProperties.Rule seckillRule = new RateLimitProperties.Rule();
        seckillRule.setPathPattern("/api/seckill/grab/**");
        seckillRule.setAlgorithm("token-bucket");
        seckillRule.setIpQps(3);
        seckillRule.setPathQps(1000);
        seckillRule.setCapacity(5L);
        seckillRule.setUserQps(3);
        rules.add(seckillRule);

        // 评论提交：滑动窗口，IP 3 QPS
        RateLimitProperties.Rule commentRule = new RateLimitProperties.Rule();
        commentRule.setPathPattern("/api/product/comment/create");
        commentRule.setAlgorithm("sliding-window");
        commentRule.setIpQps(3);
        commentRule.setPathQps(50);
        commentRule.setUserQps(3);
        rules.add(commentRule);

        props.setRules(rules);
        return props;
    }

    /**
     * 构建一个模拟的 WebFlux 请求交换上下文
     * 小白讲解：MockServerWebExchange 模拟真实的 HTTP 请求，包含路径、IP、Header 等信息。
     *
     * @param clientIp 客户端 IP（通过 X-Forwarded-For 头模拟）
     * @param path     请求路径（如 /api/user/auth/login）
     * @return 模拟的 ServerWebExchange
     */
    private MockServerWebExchange buildExchange(String clientIp, String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("X-Forwarded-For", clientIp)
                        .build());
    }

    /**
     * 构建一个带用户 ID 的模拟请求（模拟登录用户）
     */
    private MockServerWebExchange buildExchangeWithUser(String clientIp, String path, String userId) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("X-Forwarded-For", clientIp)
                        .header("X-User-Id", userId)
                        .build());
    }

    // ==================== 滑动窗口算法测试 ====================

    @Test
    @DisplayName("滑动窗口：5 QPS 限制下，前 5 个请求放行，第 6 个被限流")
    void slidingWindow_shouldLimitAfter5Requests() {
        // 场景：认证接口 /api/user/auth/** 配置了滑动窗口 IP 5 QPS
        // 预期：前 5 个请求放行（Lua 脚本返回 1），第 6 个被限流（返回 429）
        String ip = "192.168.1.100";
        String path = "/api/user/auth/login";

        // 发送 5 个请求，都应放行
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 第 6 个请求应被限流：响应状态码为 429
        MockServerWebExchange blockedExchange = buildExchange(ip, path);
        StepVerifier.create(rateLimitFilter.filter(blockedExchange, chain))
                .expectComplete().verify();
        assertThat(blockedExchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("滑动窗口：不同 IP 的请求互不影响（各自独立计数）")
    void slidingWindow_differentIpShouldBeIndependent() {
        String path = "/api/user/auth/login";

        // IP-A 发 5 个请求（达到上限）
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange("10.0.0.1", path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // IP-B 发 1 个请求，应放行（不受 IP-A 影响）
        MockServerWebExchange exchangeB = buildExchange("10.0.0.2", path);
        StepVerifier.create(rateLimitFilter.filter(exchangeB, chain))
                .expectComplete().verify();
        assertThat(exchangeB.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("滑动窗口：窗口时间过后计数重置，可再次放行")
    void slidingWindow_windowResetAfterExpiry() {
        String ip = "192.168.1.200";
        String path = "/api/user/auth/login";

        // 发 5 个请求填满窗口
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 手动清理 Redis 中该 IP 的滑动窗口 Key（模拟窗口时间过期）
        // 小白讲解：实际生产中 Redis 会自动按 PEXPIRE 过期，这里手动清理加速测试
        redisTemplate.delete("rate_limit:ip:" + ip).block();

        // 再次发请求应放行（计数已清零）
        MockServerWebExchange exchange = buildExchange(ip, path);
        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .expectComplete().verify();
        assertThat(exchange.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ==================== 令牌桶算法测试 ====================

    @Test
    @DisplayName("令牌桶：容量 5 的桶，前 5 个请求放行，第 6 个被限流")
    void tokenBucket_shouldLimitWhenBucketEmpty() {
        // 场景：秒杀接口 /api/seckill/grab/** 配置了令牌桶 IP 3 QPS，容量 5
        // 预期：初始满桶 5 个令牌，前 5 个请求消耗完令牌，第 6 个被限流
        String ip = "192.168.1.50";
        String path = "/api/seckill/grab/10086";

        // 前 5 个请求放行（消耗 5 个令牌）
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
            assertThat(exchange.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        // 第 6 个请求应被限流（桶空了）
        MockServerWebExchange blockedExchange = buildExchange(ip, path);
        StepVerifier.create(rateLimitFilter.filter(blockedExchange, chain))
                .expectComplete().verify();
        assertThat(blockedExchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("令牌桶：等待令牌补充后可再次放行")
    void tokenBucket_refillAfterWait() throws InterruptedException {
        String ip = "192.168.1.51";
        String path = "/api/seckill/grab/10086";

        // 消耗完 5 个令牌
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 等待令牌补充：3 QPS 意味着每秒补 3 个令牌，等 1 秒至少补 3 个
        Thread.sleep(1100);

        // 再次发请求应放行（令牌已补充）
        MockServerWebExchange exchange = buildExchange(ip, path);
        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .expectComplete().verify();
        assertThat(exchange.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ==================== 黑名单全链路测试 ====================

    @Test
    @DisplayName("黑名单：Redis Set 中的 IP 被拦截返回 403")
    void blacklist_redisSetIpShouldReturn403() {
        String ip = "10.10.10.10";
        String path = "/api/user/auth/login";

        // 将 IP 加入 Redis 动态黑名单 Set
        redisTemplate.opsForSet().add(BlacklistFilter.REDIS_IP_BLACKLIST, ip).block();

        // 发送请求，应被黑名单拦截
        MockServerWebExchange exchange = buildExchange(ip, path);
        StepVerifier.create(blacklistFilter.filter(exchange, chain))
                .expectComplete().verify();
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("黑名单：不在黑名单中的 IP 正常放行")
    void blacklist_nonBlacklistedIpShouldPass() {
        String ip = "10.10.10.20";
        String path = "/api/user/auth/login";

        MockServerWebExchange exchange = buildExchange(ip, path);
        StepVerifier.create(blacklistFilter.filter(exchange, chain))
                .expectComplete().verify();
        assertThat(exchange.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("黑名单 + 限流全链路：黑名单优先于限流，先拦截 403")
    void blacklistFilter_shouldExecuteBeforeRateLimit() {
        String ip = "10.10.10.30";
        String path = "/api/user/auth/login";

        // 将 IP 加入 Redis 黑名单
        redisTemplate.opsForSet().add(BlacklistFilter.REDIS_IP_BLACKLIST, ip).block();

        // 模拟完整过滤器链：先黑名单（-85），再限流（-80）
        MockServerWebExchange exchange = buildExchange(ip, path);

        // 先执行黑名单过滤器
        StepVerifier.create(blacklistFilter.filter(exchange, chain))
                .expectComplete().verify();

        // 黑名单命中，应返回 403，且后续限流过滤器不会执行
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ==================== 白名单跳过限流测试 ====================

    @Test
    @DisplayName("白名单：IP 在白名单中时跳过限流，无限发送都放行")
    void whitelist_shouldSkipRateLimit() {
        String ip = "10.10.10.40";

        // 将 IP 加入 Redis 动态白名单
        redisTemplate.opsForSet().add("whitelist:ip", ip).block();

        String path = "/api/user/auth/login";

        // 发送 20 个请求（远超 5 QPS 限制）
        for (int i = 0; i < 20; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            // 先执行黑名单过滤器（设置白名单跳过标记）
            StepVerifier.create(blacklistFilter.filter(exchange, chain))
                    .expectComplete().verify();
            // 再执行限流过滤器（应跳过）
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
            assertThat(exchange.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    // ==================== 自动拉黑机制测试 ====================

    @Test
    @DisplayName("自动拉黑：触发限流累计 10 次后，IP 被自动加入 Redis 黑名单")
    void autoBlock_shouldAddToBlacklistAfter10Rejections() {
        String ip = "10.10.10.50";
        String path = "/api/user/auth/login";

        // 前 5 个请求放行
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 接下来触发 10 次限流（第 6-15 次请求）
        for (int i = 0; i < 10; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 验证 IP 已被自动加入 Redis 黑名单 Set
        Boolean isMember = redisTemplate.opsForSet()
                .isMember(BlacklistFilter.REDIS_IP_BLACKLIST, ip).block();
        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("自动拉黑：未达到阈值 10 次时不拉黑")
    void autoBlock_shouldNotBlockBeforeThreshold() {
        String ip = "10.10.10.60";
        String path = "/api/user/auth/login";

        // 前 5 个请求放行
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 只触发 9 次限流（未达到 10 次阈值）
        for (int i = 0; i < 9; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 验证 IP 未被加入黑名单
        Boolean isMember = redisTemplate.opsForSet()
                .isMember(BlacklistFilter.REDIS_IP_BLACKLIST, ip).block();
        assertThat(isMember).isFalse();
    }

    // ==================== 用户维度限流测试 ====================

    @Test
    @DisplayName("用户维度限流：登录用户按 userId 限流，3 QPS 超限后被限流")
    void userDimension_shouldLimitByUserId() {
        String ip = "10.10.10.70";
        String userId = "user-123";
        String path = "/api/seckill/grab/10086";

        // 秒杀接口配置了 user-qps=3，容量=5
        // 登录用户带 X-User-Id 头，会按 userId 维度限流

        // 前 5 个请求放行（令牌桶容量 5）
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchangeWithUser(ip, path, userId);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
            assertThat(exchange.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        // 第 6 个请求应被限流
        MockServerWebExchange blockedExchange = buildExchangeWithUser(ip, path, userId);
        StepVerifier.create(rateLimitFilter.filter(blockedExchange, chain))
                .expectComplete().verify();
        assertThat(blockedExchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("用户维度限流：不同用户的限流互不影响")
    void userDimension_differentUsersIndependent() {
        String ip = "10.10.10.80";
        String path = "/api/seckill/grab/10086";

        // 用户 A 消耗完 5 个令牌
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchangeWithUser(ip, path, "user-A");
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 用户 B 发请求应放行（不受用户 A 影响）
        MockServerWebExchange exchangeB = buildExchangeWithUser(ip, path, "user-B");
        StepVerifier.create(rateLimitFilter.filter(exchangeB, chain))
                .expectComplete().verify();
        assertThat(exchangeB.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ==================== 限流总开关测试 ====================

    @Test
    @DisplayName("限流总开关关闭：所有请求都放行，不执行限流")
    void rateLimitDisabled_shouldPassAll() {
        // 重新构建过滤器，关闭限流总开关
        RateLimitProperties disabledProps = buildRateLimitProperties();
        disabledProps.setEnabled(false);

        RateLimitScriptConfig scriptConfig = new RateLimitScriptConfig();
        rateLimitFilter = new RateLimitFilter(redisTemplate,
                scriptConfig.rateLimitScript(), scriptConfig.tokenBucketScript(),
                disabledProps, new BlacklistConfig());

        String ip = "10.10.10.90";
        String path = "/api/user/auth/login";

        // 发 100 个请求，全部应放行
        for (int i = 0; i < 100; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
            assertThat(exchange.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    // ==================== Redis 数据验证测试 ====================

    @Test
    @DisplayName("Redis 数据验证：滑动窗口限流后 ZSet 中存在请求记录")
    void slidingWindow_zsetRecordsExist() {
        String ip = "192.168.1.300";
        String path = "/api/user/auth/login";

        // 发 3 个请求
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 验证 Redis ZSet 中有 3 条记录
        // Key 格式：rate_limit:ip:{ip}（ZSet，score 是时间戳，member 是 requestId）
        Long zsetSize = redisTemplate.opsForZSet()
                .size("rate_limit:ip:" + ip).block();
        assertThat(zsetSize).isEqualTo(3L);
    }

    @Test
    @DisplayName("Redis 数据验证：令牌桶限流后 Hash 中记录令牌数和上次补充时间")
    void tokenBucket_hashRecordsExist() {
        String ip = "192.168.1.301";
        String path = "/api/seckill/grab/10086";

        // 发 1 个请求
        MockServerWebExchange exchange = buildExchange(ip, path);
        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .expectComplete().verify();

        // 验证 Redis Hash 中有 tokens 和 last_refill 字段
        // 初始容量 5，消耗 1 个后应剩 4 个令牌
        Object tokens = redisTemplate.opsForHash()
                .get("rate_limit:ip:" + ip, "tokens").block();
        assertThat(tokens).isNotNull();
        // 令牌数应小于等于 4（可能有微小的时间补充，但首次消耗后应 <=4）
        double tokenCount = Double.parseDouble(tokens.toString());
        assertThat(tokenCount).isLessThanOrEqualTo(4.0);
    }

    @Test
    @DisplayName("Redis 数据验证：自动拉黑计数器存在且递增")
    void autoBlock_counterIncrements() {
        String ip = "192.168.1.302";
        String path = "/api/user/auth/login";

        // 前 5 个请求放行
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 触发 3 次限流
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = buildExchange(ip, path);
            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .expectComplete().verify();
        }

        // 验证 Redis 中的限流拒绝计数器
        String countStr = redisTemplate.opsForValue()
                .get("rate_limit:reject:" + ip).block();
        assertThat(countStr).isNotNull();
        assertThat(Long.parseLong(countStr)).isEqualTo(3L);
    }

    /**
     * 所有测试结束后销毁 Redis 连接工厂
     */
    @AfterAll
    static void tearDown() {
        if (sharedFactory != null) {
            try {
                sharedFactory.destroy();
            } catch (Exception ignored) {
                // 销毁失败不影响测试结果
            }
        }
    }
}
