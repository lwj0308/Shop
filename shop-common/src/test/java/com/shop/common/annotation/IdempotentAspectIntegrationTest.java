package com.shop.common.annotation;

import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.util.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * B-I-06 幂等性全链路集成测试
 * <p>
 * 小白讲解：
 * 之前的单元测试用 Mockito 把 Redis 模拟出来，只能验证"调用了 Redis"，
 * 但不能验证"Redis 实际存了什么"、"Key 真的过期了吗"这类真实行为。
 *
 * 这个集成测试连的是本地 Docker 中正在运行的 Redis 容器（shop-redis），
 * 让 IdempotentAspect 的 Lua 脚本在真实 Redis 上执行，验证：
 * 1. SETNX Lua 脚本在真实 Redis 上的原子性（首次返回 1，重复返回 0）
 * 2. Key 写入 Redis 后能被读到，且 TTL 正确设置
 * 3. 业务异常时 Key 被回滚删除（允许重试）
 * 4. 系统异常时 Key 被回滚删除
 * 5. Key 过期后可再次请求
 * 6. Header 解析幂等键的真实写入
 * 7. SpEL 解析幂等键的真实写入
 * 8. Redis 异常时降级放行（弱依赖）
 *
 * 隔离策略：使用 Redis DB 14（B-I-03 用 DB 15，业务用 DB 0），
 * 测试前 FLUSHDB 清空 DB 14，不影响业务数据。
 *
 * 环境要求：本地 Docker 中需有 Redis 容器监听 6379 端口（如 shop-redis）。
 * 如果 Redis 不可用，所有测试会自动跳过（assumeTrue）。
 * </p>
 */
@DisplayName("B-I-06 幂等性全链路集成测试 - 真实 Redis")
class IdempotentAspectIntegrationTest {

    /** 测试用 Redis 数据库索引（B-I-03 用 15，B-I-06 用 14，业务用 0） */
    private static final int TEST_DB_INDEX = 14;
    /** 本地 Redis 主机 */
    private static final String REDIS_HOST = "localhost";
    /** 本地 Redis 端口 */
    private static final int REDIS_PORT = 6379;

    /** 共享的 Lettuce 连接工厂（指向 DB 14） */
    private static LettuceConnectionFactory sharedFactory;
    /** 测试前 Redis 是否可用（不可用时跳过所有测试） */
    private static boolean redisAvailable = false;

    /** 真实连接本地 Redis 的字符串 Redis 客户端 */
    private StringRedisTemplate redisTemplate;

    /** 被测对象：幂等切面 */
    private IdempotentAspect idempotentAspect;

    /** Mock 的 AOP 连接点 */
    private ProceedingJoinPoint joinPoint;

    /** Mock 的方法签名 */
    private MethodSignature methodSignature;

    /** SecurityUtils 静态方法 mock（避免触发 Sa-Token） */
    private MockedStatic<SecurityUtils> securityUtilsMock;

    /** RequestContextHolder 静态方法 mock（控制 HTTP 上下文） */
    private MockedStatic<RequestContextHolder> requestContextHolderMock;

    /**
     * 在所有测试前创建 Redis 连接工厂，并检测 Redis 是否可用
     * 小白讲解：连本地 Docker 中的 Redis（localhost:6379），切到 DB 14。
     * 如果连接失败（比如 Redis 没启动），所有测试会被跳过，不让 CI 失败。
     */
    @BeforeAll
    static void initConnectionFactory() {
        try {
            sharedFactory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
            // 切换到 DB 14，避免和业务数据混淆
            sharedFactory.setDatabase(TEST_DB_INDEX);
            sharedFactory.afterPropertiesSet();
            sharedFactory.start();

            // 测试连接是否可用：尝试执行 PING 命令
            String pong = sharedFactory.getConnection().ping();
            redisAvailable = "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            redisAvailable = false;
            System.err.println("[B-I-06] Redis 不可用，测试将被跳过：" + e.getMessage());
        }
    }

    /**
     * 每个测试前重建 Redis 客户端和切面实例，并清空 DB 14 数据
     * 小白讲解：每个测试都需要干净的 Redis 状态，避免上一个测试的 Key 影响下一个。
     * 用 FLUSHDB 只清空当前 DB（14），不影响业务 DB 0。
     */
    @BeforeEach
    void setUp() {
        // 如果 Redis 不可用，通过 assumeTrue 跳过当前测试（不算失败）
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis 不可用，跳过 B-I-06 集成测试");

        // 创建真实的字符串 Redis 客户端
        redisTemplate = new StringRedisTemplate(sharedFactory);

        // 手动创建幂等切面实例，注入真实 Redis 客户端
        idempotentAspect = new IdempotentAspect(redisTemplate);

        // 清空 Redis DB 14 中的所有数据，确保每个测试从干净状态开始
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        // Mock AOP 连接点和方法签名
        joinPoint = mock(ProceedingJoinPoint.class);
        methodSignature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn((Class) IdempotentAspectIntegrationTest.class);
        when(methodSignature.getName()).thenReturn("mockMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg1"});

        // Mock SecurityUtils 返回 null（模拟未登录场景，自动键用 "anonymous"）
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(null);

        // Mock RequestContextHolder 返回 null（模拟非 HTTP 上下文，跳过 Header 解析）
        requestContextHolderMock = mockStatic(RequestContextHolder.class);
        requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
    }

    /**
     * 每个测试后关闭静态 mock，避免影响其他测试
     */
    @AfterEach
    void tearDownStatic() {
        if (securityUtilsMock != null) {
            securityUtilsMock.close();
        }
        if (requestContextHolderMock != null) {
            requestContextHolderMock.close();
        }
    }

    /**
     * 创建 @Idempotent 注解实例
     *
     * @param prefix  Redis Key 前缀
     * @param key     SpEL 表达式（为空表示用 Header 或自动生成）
     * @param expire  过期时间（秒）
     * @param header  Header 名称
     * @param message 重复请求提示语
     * @return @Idempotent 注解实例
     */
    private Idempotent createIdempotent(String prefix, String key, long expire,
                                        String header, String message) {
        return new Idempotent() {
            @Override public String prefix() { return prefix; }
            @Override public String key() { return key; }
            @Override public String header() { return header; }
            @Override public long expire() { return expire; }
            @Override public String message() { return message; }
            @Override public IdempotentStrategy strategy() { return IdempotentStrategy.SETNX; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    /** 简化版：用默认 header 和 message 创建注解 */
    private Idempotent createIdempotent(String prefix, String key, long expire) {
        return createIdempotent(prefix, key, expire, "X-Idempotent-Key", "请勿重复操作");
    }

    // ==================== SETNX 原子操作测试 ====================

    @Test
    @DisplayName("首次请求 SETNX 成功：业务方法执行，Key 写入 Redis")
    void firstRequest_setnxSuccess_keyWrittenToRedis() throws Throwable {
        Idempotent idempotent = createIdempotent("idempotent:test:", "", 60);
        when(joinPoint.proceed()).thenReturn("success");

        Object result = idempotentAspect.around(joinPoint, idempotent);

        // 验证：业务方法被执行
        assertThat(result).isEqualTo("success");

        // 验证：Redis 中存在以 "idempotent:test:" 开头的 Key
        // 小白讲解：自动生成键格式 = className.methodName:userId:argsHash
        // 由于 mock 了 SecurityUtils 返回 null，userId 部分是 "anonymous"
        Set<String> keys = redisTemplate.keys("idempotent:test:*");
        assertThat(keys).isNotEmpty();
        // Key 应该是唯一的
        assertThat(keys).hasSize(1);
    }

    @Test
    @DisplayName("重复请求 SETNX 失败：抛 BusinessException(REPEAT_REQUEST)")
    void repeatRequest_setnxFail_throwsBusinessException() throws Throwable {
        Idempotent idempotent = createIdempotent("idempotent:repeat:", "", 60);
        when(joinPoint.proceed()).thenReturn("first-success");

        // 第一次请求：成功
        Object firstResult = idempotentAspect.around(joinPoint, idempotent);
        assertThat(firstResult).isEqualTo("first-success");

        // 第二次请求：相同 Key，应抛 BusinessException
        assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.REPEAT_REQUEST.getCode());
                });
    }

    @Test
    @DisplayName("自定义 message 应在重复请求异常中正确返回")
    void customMessage_shouldAppearInException() throws Throwable {
        Idempotent idempotent = createIdempotent(
                "idempotent:custom:", "", 60, "X-Idempotent-Key", "订单正在处理中");
        when(joinPoint.proceed()).thenReturn("ok");

        // 第一次请求：成功
        idempotentAspect.around(joinPoint, idempotent);

        // 第二次请求：应抛异常，消息包含自定义 message
        assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    // 格式：errorCode.message: customMessage
                    assertThat(be.getMessage()).contains("订单正在处理中");
                });
    }

    // ==================== Key TTL 验证测试 ====================

    @Test
    @DisplayName("Key 写入 Redis 后 TTL 正确设置（与注解 expire 一致）")
    void keyTtl_shouldMatchExpireConfig() throws Throwable {
        // 设置 expire=120 秒
        Idempotent idempotent = createIdempotent("idempotent:ttl:", "", 120);
        when(joinPoint.proceed()).thenReturn("ok");

        idempotentAspect.around(joinPoint, idempotent);

        // 验证：Key 的 TTL 应该在 110~120 秒之间（允许少量耗时误差）
        Set<String> keys = redisTemplate.keys("idempotent:ttl:*");
        assertThat(keys).hasSize(1);
        Long ttl = redisTemplate.getExpire(keys.iterator().next());
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(110L, 120L);
    }

    @Test
    @DisplayName("Key 过期后可再次请求（手动模拟过期，验证 SETNX 可重新获取）")
    void keyExpired_canRequestAgain() throws Throwable {
        Idempotent idempotent = createIdempotent("idempotent:expire:", "", 60);
        when(joinPoint.proceed()).thenReturn("first");

        // 第一次请求：成功
        Object firstResult = idempotentAspect.around(joinPoint, idempotent);
        assertThat(firstResult).isEqualTo("first");

        // 手动删除 Key 模拟过期
        redisTemplate.delete(redisTemplate.keys("idempotent:expire:*"));

        // 第二次请求：Key 已过期，应再次成功
        when(joinPoint.proceed()).thenReturn("second");
        Object secondResult = idempotentAspect.around(joinPoint, idempotent);
        assertThat(secondResult).isEqualTo("second");
    }

    // ==================== 异常回滚测试 ====================

    @Test
    @DisplayName("业务异常时 Key 被回滚删除，允许客户端重试")
    void businessException_shouldRollbackKey() throws Throwable {
        Idempotent idempotent = createIdempotent("idempotent:rollback-biz:", "", 60);
        when(joinPoint.proceed()).thenThrow(new BusinessException(ErrorCode.OPERATION_FAIL));

        // 业务方法抛 BusinessException
        assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class);

        // 验证：Key 已被删除（回滚）
        Set<String> keys = redisTemplate.keys("idempotent:rollback-biz:*");
        assertThat(keys).isEmpty();

        // 验证：可以再次请求（Key 已删除，SETNX 可重新获取）
        // 小白讲解：用 doReturn().when() 语法覆盖之前的 thenThrow stub
        // Mockito 中 thenThrow 设置后，必须用 doReturn 才能可靠覆盖
        org.mockito.Mockito.doReturn("retry-success").when(joinPoint).proceed();
        Object retryResult = idempotentAspect.around(joinPoint, idempotent);
        assertThat(retryResult).isEqualTo("retry-success");
    }

    @Test
    @DisplayName("系统异常（RuntimeException）时 Key 被回滚删除并包装为 BusinessException")
    void runtimeException_shouldRollbackKeyAndWrap() throws Throwable {
        Idempotent idempotent = createIdempotent("idempotent:rollback-sys:", "", 60);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("数据库连接超时"));

        // 业务方法抛 RuntimeException，应被包装为 BusinessException
        assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
                    assertThat(be.getMessage()).contains("数据库连接超时");
                });

        // 验证：Key 已被删除（回滚）
        Set<String> keys = redisTemplate.keys("idempotent:rollback-sys:*");
        assertThat(keys).isEmpty();
    }

    @Test
    @DisplayName("正常执行完毕后 Key 保留在 Redis（自然过期，不删除）")
    void normalExecution_shouldKeepKey() throws Throwable {
        Idempotent idempotent = createIdempotent("idempotent:keep:", "", 60);
        when(joinPoint.proceed()).thenReturn("success");

        idempotentAspect.around(joinPoint, idempotent);

        // 验证：Key 仍然存在（未被删除）
        Set<String> keys = redisTemplate.keys("idempotent:keep:*");
        assertThat(keys).hasSize(1);
    }

    // ==================== Header 解析测试 ====================

    @Test
    @DisplayName("Header 解析幂等键：X-Idempotent-Key 的值作为 Key 写入 Redis")
    void headerKey_shouldBeWrittenToRedis() throws Throwable {
        // Mock RequestContextHolder 返回带 Header 的请求
        String headerValue = "header-unique-key-001";
        mockHttpRequest("X-Idempotent-Key", headerValue);

        Idempotent idempotent = createIdempotent("idempotent:header:", "", 60);
        when(joinPoint.proceed()).thenReturn("ok");

        idempotentAspect.around(joinPoint, idempotent);

        // 验证：Redis 中存在 Key = idempotent:header:header-unique-key-001
        String expectedKey = "idempotent:header:" + headerValue;
        String value = redisTemplate.opsForValue().get(expectedKey);
        assertThat(value).isEqualTo("1");
    }

    @Test
    @DisplayName("相同 Header 值的重复请求被拦截")
    void sameHeaderKey_repeatRequest_shouldBeBlocked() throws Throwable {
        mockHttpRequest("X-Idempotent-Key", "dup-header-key");
        Idempotent idempotent = createIdempotent("idempotent:header-dup:", "", 60);
        when(joinPoint.proceed()).thenReturn("first");

        // 第一次请求：成功
        Object first = idempotentAspect.around(joinPoint, idempotent);
        assertThat(first).isEqualTo("first");

        // 第二次请求：相同 Header 值，应被拦截
        assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.REPEAT_REQUEST.getCode());
                });
    }

    @Test
    @DisplayName("不同 Header 值的请求互不影响（各自独立 SETNX）")
    void differentHeaderKey_shouldBeIndependent() throws Throwable {
        Idempotent idempotent = createIdempotent("idempotent:header-indep:", "", 60);
        when(joinPoint.proceed()).thenReturn("ok");

        // 第一次请求：Header-A
        mockHttpRequest("X-Idempotent-Key", "header-A");
        Object first = idempotentAspect.around(joinPoint, idempotent);
        assertThat(first).isEqualTo("ok");

        // 第二次请求：Header-B（不同的值，应放行）
        mockHttpRequest("X-Idempotent-Key", "header-B");
        Object second = idempotentAspect.around(joinPoint, idempotent);
        assertThat(second).isEqualTo("ok");

        // 验证：Redis 中存在两个 Key
        Set<String> keys = redisTemplate.keys("idempotent:header-indep:*");
        assertThat(keys).hasSize(2);
    }

    // ==================== SpEL 解析测试 ====================

    @Test
    @DisplayName("SpEL 表达式解析：#参数名 解析为参数值，作为 Key 写入 Redis")
    void spelKey_shouldResolveAndWriteToRedis() throws Throwable {
        // Mock 方法参数名发现
        try {
            java.lang.reflect.Method mockMethod = SpelTestService.class.getMethod("process", String.class);
            when(methodSignature.getMethod()).thenReturn(mockMethod);
        } catch (NoSuchMethodException e) {
            // 忽略，SpEL 解析失败会回退到自动生成
        }
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORD-20240101-001"});

        Idempotent idempotent = createIdempotent("idempotent:spel:", "#orderNo", 60);
        when(joinPoint.proceed()).thenReturn("ok");

        idempotentAspect.around(joinPoint, idempotent);

        // 验证：Redis 中存在 Key = idempotent:spel:ORD-20240101-001
        String expectedKey = "idempotent:spel:ORD-20240101-001";
        String value = redisTemplate.opsForValue().get(expectedKey);
        assertThat(value).isEqualTo("1");
    }

    // ==================== 不同 prefix 隔离测试 ====================

    @Test
    @DisplayName("不同 prefix 的相同业务键互不影响（prefix 隔离）")
    void differentPrefix_shouldBeIsolated() throws Throwable {
        mockHttpRequest("X-Idempotent-Key", "same-key");
        when(joinPoint.proceed()).thenReturn("ok");

        // prefix-A 请求
        Idempotent idempotentA = createIdempotent("idempotent:prefixA:", "", 60);
        Object resultA = idempotentAspect.around(joinPoint, idempotentA);
        assertThat(resultA).isEqualTo("ok");

        // prefix-B 相同 Header 值，应放行（不同 prefix 隔离）
        Idempotent idempotentB = createIdempotent("idempotent:prefixB:", "", 60);
        Object resultB = idempotentAspect.around(joinPoint, idempotentB);
        assertThat(resultB).isEqualTo("ok");

        // 验证：Redis 中存在两个 Key（prefixA 和 prefixB 各一个）
        Set<String> keysA = redisTemplate.keys("idempotent:prefixA:*");
        Set<String> keysB = redisTemplate.keys("idempotent:prefixB:*");
        assertThat(keysA).hasSize(1);
        assertThat(keysB).hasSize(1);
    }

    // ==================== Redis 降级放行测试 ====================

    @Test
    @DisplayName("Redis 异常时降级放行：业务方法仍执行，幂等是弱依赖")
    void redisException_shouldFallbackAndProceed() throws Throwable {
        // 创建一个连接到无效端口的 Redis 客户端，模拟 Redis 不可用
        LettuceConnectionFactory badFactory = new LettuceConnectionFactory("localhost", 16379);
        badFactory.setDatabase(TEST_DB_INDEX);
        badFactory.afterPropertiesSet();
        badFactory.start();

        try {
            StringRedisTemplate badTemplate = new StringRedisTemplate(badFactory);
            IdempotentAspect badAspect = new IdempotentAspect(badTemplate);

            Idempotent idempotent = createIdempotent("idempotent:fallback:", "", 60);
            when(joinPoint.proceed()).thenReturn("fallback-success");

            // 由于 Redis 不可用，应降级放行
            Object result = badAspect.around(joinPoint, idempotent);

            // 验证：业务方法被执行
            assertThat(result).isEqualTo("fallback-success");
        } finally {
            badFactory.destroy();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * Mock HTTP 请求中的 Header
     * 小白讲解：让 RequestContextHolder 返回一个带 Header 的假请求，
     * 这样 IdempotentAspect 就能从 Header 中读取幂等键。
     */
    private void mockHttpRequest(String headerName, String headerValue) {
        jakarta.servlet.http.HttpServletRequest request =
                mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader(headerName)).thenReturn(headerValue);
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(request);
        requestContextHolderMock.when(RequestContextHolder::getRequestAttributes)
                .thenReturn(attributes);
    }

    /**
     * 测试用内部服务类，用于模拟 SpEL 参数名发现
     */
    public static class SpelTestService {
        public void process(String orderNo) {}
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
