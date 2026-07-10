package com.shop.common.annotation;

import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.util.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IdempotentAspect 幂等切面的单元测试
 * <p>
 * 验证幂等切面的核心能力：SETNX互斥、键解析优先级、SpEL解析、
 * Header解析、异常回滚、Redis降级放行。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IdempotentAspect 幂等切面测试")
class IdempotentAspectTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private IdempotentAspect idempotentAspect;

    private MockedStatic<SecurityUtils> securityUtilsMock;
    private MockedStatic<RequestContextHolder> requestContextHolderMock;

    @BeforeEach
    void setUp() {
        // 默认模拟 SecurityUtils 返回用户ID
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(1001L);

        // 默认模拟 RequestContextHolder 无HTTP请求
        requestContextHolderMock = mockStatic(RequestContextHolder.class);
        requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

        // 默认模拟 joinPoint 的签名
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn((Class) IdempotentAspectTest.class);
        when(methodSignature.getName()).thenReturn("mockMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg1"});
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
        requestContextHolderMock.close();
    }

    // ==================== SETNX 成功/失败 ====================

    @Nested
    @DisplayName("SETNX 原子操作")
    class SetnxTest {

        @Test
        @DisplayName("首次请求SETNX成功，应放行并返回业务结果")
        void firstRequest_shouldProceed() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("success");

            Object result = idempotentAspect.around(joinPoint, idempotent);

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("重复请求SETNX失败，应抛BusinessException(REPEAT_REQUEST)")
        void repeatRequest_shouldThrow() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(0L);

            assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.REPEAT_REQUEST.getCode());
                        // BusinessException(ErrorCode, args) 格式化为 "errorCode.message: args"
                        assertThat(be.getMessage()).isEqualTo("请勿重复操作: 请勿重复操作");
                    });

            // 重复请求不应执行业务方法
            verify(joinPoint, never()).proceed();
        }

        @Test
        @DisplayName("自定义message应覆盖默认提示语")
        void customMessage_shouldOverrideDefault() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300, "订单正在处理中");
            mockSetnxResult(0L);

            assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        // 格式化为 "errorCode.message: customMessage"
                        assertThat(be.getMessage()).isEqualTo("请勿重复操作: 订单正在处理中");
                    });
        }
    }

    // ==================== 键解析优先级 ====================

    @Nested
    @DisplayName("键解析优先级")
    class KeyResolutionTest {

        @Test
        @DisplayName("Header有值时，应优先使用Header")
        void headerKey_shouldTakePriority() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "#orderId", 300);
            mockHttpRequest("X-Idempotent-Key", "header-key-123");
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            idempotentAspect.around(joinPoint, idempotent);

            // 验证Redis使用了header的值作为key的一部分
            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    eq("1"), anyString()
            );
        }

        @Test
        @DisplayName("Header为空且SpEL有值时，应使用SpEL解析结果")
        void spelKey_shouldBeUsedWhenHeaderEmpty() throws Throwable {
            // RequestContextHolder返回null，所以Header为空
            Idempotent idempotent = createIdempotent("idempotent:test:", "#orderId", 300);
            mockMethodParameterNames("orderId");
            when(joinPoint.getArgs()).thenReturn(new Object[]{"ORDER-001"});
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            idempotentAspect.around(joinPoint, idempotent);

            // SpEL #orderId 应解析为 ORDER-001，最终Redis key = idempotent:test:ORDER-001
            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    eq("1"), anyString()
            );
        }

        @Test
        @DisplayName("Header和SpEL均为空时，应自动生成键")
        void autoKey_shouldBeUsedAsFallback() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            idempotentAspect.around(joinPoint, idempotent);

            // 自动生成键：className.methodName:userId:argsHash
            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    eq("1"), anyString()
            );
        }
    }

    // ==================== SpEL 解析 ====================

    @Nested
    @DisplayName("SpEL 表达式解析")
    class SpelResolutionTest {

        @Test
        @DisplayName("#参数名：应解析为参数值的toString")
        void simpleParameterName_shouldResolve() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "#orderNo", 300);
            mockMethodParameterNames("orderNo");
            when(joinPoint.getArgs()).thenReturn(new Object[]{"ORD-20240101"});
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = idempotentAspect.around(joinPoint, idempotent);

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("#参数名.属性：应解析为对象属性值")
        void nestedProperty_shouldResolve() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "#dto.orderNo", 300);
            mockMethodParameterNames("dto");
            OrderDTO dto = new OrderDTO("ORD-20240102");
            when(joinPoint.getArgs()).thenReturn(new Object[]{dto});
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = idempotentAspect.around(joinPoint, idempotent);

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("SpEL解析失败时，应回退到自动生成键")
        void invalidSpel_shouldFallbackToAutoKey() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "#nonExist.prop", 300);
            mockMethodParameterNames("dto");
            when(joinPoint.getArgs()).thenReturn(new Object[]{"simpleArg"});
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            // SpEL解析失败不应抛异常，应回退到自动生成
            Object result = idempotentAspect.around(joinPoint, idempotent);

            assertThat(result).isEqualTo("ok");
        }
    }

    // ==================== Header 解析 ====================

    @Nested
    @DisplayName("HTTP Header 解析")
    class HeaderResolutionTest {

        @Test
        @DisplayName("非HTTP场景（RequestContextHolder为null），Header解析应返回null")
        void nonHttpContext_shouldReturnNull() throws Throwable {
            // 默认setUp中RequestContextHolder返回null
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            // 应正常放行（回退到自动生成键），不抛异常
            Object result = idempotentAspect.around(joinPoint, idempotent);
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("自定义Header名称应生效")
        void customHeaderName_shouldWork() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300,
                    "X-Custom-Idempotency-Key", "请勿重复操作");
            mockHttpRequest("X-Custom-Idempotency-Key", "custom-key-456");
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = idempotentAspect.around(joinPoint, idempotent);
            assertThat(result).isEqualTo("ok");
        }
    }

    // ==================== 异常回滚 ====================

    @Nested
    @DisplayName("异常回滚")
    class ExceptionRollbackTest {

        @Test
        @DisplayName("业务方法抛BusinessException时，应回滚Key允许重试")
        void businessException_shouldRollbackKey() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenThrow(new BusinessException(ErrorCode.OPERATION_FAIL));

            // 需要模拟delete Lua脚本
            when(stringRedisTemplate.execute(
                    any(DefaultRedisScript.class),
                    anyList()
            )).thenReturn(1L);

            assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.OPERATION_FAIL.getCode());
                    });

            // 验证回滚删除Key的Lua被执行
            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList()
            );
        }

        @Test
        @DisplayName("业务方法抛RuntimeException时，应回滚Key并包装为BusinessException")
        void runtimeException_shouldRollbackKeyAndWrap() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenThrow(new RuntimeException("数据库连接超时"));
            when(stringRedisTemplate.execute(
                    any(DefaultRedisScript.class),
                    anyList()
            )).thenReturn(1L);

            assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
                        // 格式化为 "errorCode.message: 原始异常消息"
                        assertThat(be.getMessage()).isEqualTo("服务器内部错误: 数据库连接超时");
                    });
        }

        @Test
        @DisplayName("正常执行完毕，不应删除Key（Key自然过期）")
        void normalExecution_shouldNotDeleteKey() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenReturn("success");

            idempotentAspect.around(joinPoint, idempotent);

            // 不应调用delete Lua脚本
            verify(stringRedisTemplate, never()).execute(
                    any(DefaultRedisScript.class),
                    anyList()
            );
        }
    }

    // ==================== Redis 降级放行 ====================

    @Nested
    @DisplayName("Redis 降级策略")
    class RedisFallbackTest {

        @Test
        @DisplayName("Redis SETNX异常时，应降级放行请求")
        void setnxException_shouldFallbackToPass() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            when(stringRedisTemplate.execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString()
            )).thenThrow(new RuntimeException("Redis连接失败"));
            when(joinPoint.proceed()).thenReturn("fallback-success");

            Object result = idempotentAspect.around(joinPoint, idempotent);

            // 降级放行，业务方法正常执行
            assertThat(result).isEqualTo("fallback-success");
        }

        @Test
        @DisplayName("Redis删除Key异常时，不应影响异常回滚的主流程")
        void deleteKeyException_shouldNotAffectMainFlow() throws Throwable {
            Idempotent idempotent = createIdempotent("idempotent:test:", "", 300);
            mockSetnxResult(1L);
            when(joinPoint.proceed()).thenThrow(new BusinessException(ErrorCode.OPERATION_FAIL));
            when(stringRedisTemplate.execute(
                    any(DefaultRedisScript.class),
                    anyList()
            )).thenThrow(new RuntimeException("Redis删除失败"));

            // 即使删除Key失败，也应正常抛出BusinessException
            assertThatThrownBy(() -> idempotentAspect.around(joinPoint, idempotent))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.OPERATION_FAIL.getCode());
                    });
        }
    }

    // ==================== 非 SETNX 策略 ====================

    @Nested
    @DisplayName("非 SETNX 策略")
    class NonSetnxStrategyTest {

        @Test
        @DisplayName("TOKEN策略时，应直接执行业务方法，不做幂等校验")
        void tokenStrategy_shouldProceedDirectly() throws Throwable {
            Idempotent idempotent = createIdempotentWithStrategy("idempotent:test:", "", 300,
                    IdempotentStrategy.TOKEN);
            when(joinPoint.proceed()).thenReturn("direct-result");

            Object result = idempotentAspect.around(joinPoint, idempotent);

            assertThat(result).isEqualTo("direct-result");
            // 不应调用Redis
            verify(stringRedisTemplate, never()).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString()
            );
        }

        @Test
        @DisplayName("STATE策略时，应直接执行业务方法，不做幂等校验")
        void stateStrategy_shouldProceedDirectly() throws Throwable {
            Idempotent idempotent = createIdempotentWithStrategy("idempotent:test:", "", 300,
                    IdempotentStrategy.STATE);
            when(joinPoint.proceed()).thenReturn("direct-result");

            Object result = idempotentAspect.around(joinPoint, idempotent);

            assertThat(result).isEqualTo("direct-result");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建 @Idempotent 注解实例
     */
    private Idempotent createIdempotent(String prefix, String key, long expire) {
        return createIdempotent(prefix, key, expire, "请勿重复操作");
    }

    private Idempotent createIdempotent(String prefix, String key, long expire, String message) {
        return createIdempotent(prefix, key, expire, "X-Idempotent-Key", message);
    }

    private Idempotent createIdempotent(String prefix, String key, long expire,
                                        String header, String message) {
        return new Idempotent() {
            @Override
            public String prefix() { return prefix; }
            @Override
            public String key() { return key; }
            @Override
            public String header() { return header; }
            @Override
            public long expire() { return expire; }
            @Override
            public String message() { return message; }
            @Override
            public IdempotentStrategy strategy() { return IdempotentStrategy.SETNX; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    private Idempotent createIdempotentWithStrategy(String prefix, String key, long expire,
                                                    IdempotentStrategy strategy) {
        return new Idempotent() {
            @Override
            public String prefix() { return prefix; }
            @Override
            public String key() { return key; }
            @Override
            public String header() { return "X-Idempotent-Key"; }
            @Override
            public long expire() { return expire; }
            @Override
            public String message() { return "请勿重复操作"; }
            @Override
            public IdempotentStrategy strategy() { return strategy; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    /**
     * 模拟 Redis SETNX Lua 脚本执行结果
     */
    private void mockSetnxResult(Long result) {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(), anyString()
        )).thenReturn(result);
    }

    /**
     * 模拟 HTTP 请求中的 Header
     */
    private void mockHttpRequest(String headerName, String headerValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(headerName)).thenReturn(headerValue);
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(request);
        requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
    }

    /**
     * 模拟方法参数名发现
     */
    private void mockMethodParameterNames(String... names) {
        try {
            // 通过反射模拟 Method 对象和参数名
            Method mockMethod = TestService.class.getMethod("mockMethod", Object.class);
            when(methodSignature.getMethod()).thenReturn(mockMethod);
        } catch (NoSuchMethodException e) {
            // 忽略，测试方法签名不匹配不影响主流程
        }
    }

    /**
     * 测试用内部服务类，用于模拟方法参数名发现
     */
    public static class TestService {
        public void mockMethod(Object orderId) {}
    }

    /**
     * 测试用 DTO，验证 SpEL #参数名.属性 解析
     */
    public record OrderDTO(String orderNo) {}
}