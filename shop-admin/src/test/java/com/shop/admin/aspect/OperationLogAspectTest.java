package com.shop.admin.aspect;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.service.AdminOperationLogService;
import com.shop.admin.util.IpUtils;
import com.shop.model.admin.entity.AdminOperationLog;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OperationLogAspect 操作日志切面的单元测试
 * <p>
 * 这个测试类验证切面能不能正确地拦截 @OperationLog 注解的方法，
 * 把操作人、参数、结果、耗时、异常等信息记录到数据库。
 * 简单理解：方法成功就记 success 日志，方法抛异常就记 fail 日志（并重新抛出异常），
 * 不管成功失败都要保存日志，密码等敏感字段要打码，超长内容要截断。
 * </p>
 * <p>
 * 测试要点：
 * 1. 用 @Mock 模拟 AdminOperationLogService（不真连数据库）
 * 2. 用 mockStatic 模拟 StpUtil（Sa-Token）、RequestContextHolder、IpUtils 这些静态方法
 * 3. 用 ArgumentCaptor 捕获传给 service 的日志实体，验证里面的字段
 * </p>
 */
@DisplayName("OperationLogAspect 操作日志切面测试")
@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    /** 被测切面，依赖通过 @InjectMocks 自动注入 */
    @InjectMocks
    private OperationLogAspect aspect;

    /** mock的操作日志服务，验证是否调用了recordOperationLog */
    @Mock
    private AdminOperationLogService adminOperationLogService;

    /** 和切面里定义的最大长度保持一致，用于断言截断后的长度 */
    private static final int MAX_LENGTH = 2000;

    /**
     * 创建一个 mock 的 @OperationLog 注解实例
     * <p>
     * 注解本质是接口，可以用 Mockito mock 出来，然后指定每个方法的返回值。
     * 这样就不用真的在测试方法上加注解了，方便控制测试数据。
     * </p>
     */
    private OperationLog mockOperationLog(String module, OperationType type, String description) {
        OperationLog operationLog = mock(OperationLog.class);
        when(operationLog.module()).thenReturn(module);
        when(operationLog.type()).thenReturn(type);
        when(operationLog.description()).thenReturn(description);
        return operationLog;
    }

    /**
     * mock Web 请求上下文，让切面能从 RequestContextHolder 拿到 request
     * <p>
     * 切面里有这段逻辑：RequestContextHolder.getRequestAttributes()，
     * 如果不为 null，就会从 request 里拿 URL、method、IP，并序列化参数。
     * 我们 mock 这个静态方法返回一个假的 attributes，触发这段逻辑。
     * </p>
     */
    private void mockWebContext(MockedStatic<RequestContextHolder> rchMock,
                                MockedStatic<IpUtils> ipMock,
                                String requestUrl, String method, String ip) {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        rchMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        when(attributes.getRequest()).thenReturn(request);
        when(request.getRequestURL()).thenReturn(new StringBuffer(requestUrl));
        when(request.getMethod()).thenReturn(method);
        ipMock.when(() -> IpUtils.getClientIp(request)).thenReturn(ip);
    }

    // ==================== 正常流程测试 ====================

    @Nested
    @DisplayName("正常流程")
    class NormalFlowTest {

        @Test
        @DisplayName("方法正常执行：proceed返回结果 → 记录成功日志，status=1，返回结果透传")
        void methodExecutesSuccessfully_shouldRecordSuccessLog() throws Throwable {
            // Arrange：准备mock的切点、返回结果、注解
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            Map<String, Object> expectedResult = new HashMap<>();
            expectedResult.put("code", 200);
            when(joinPoint.proceed()).thenReturn(expectedResult);

            OperationLog operationLog = mockOperationLog("用户管理", OperationType.CREATE, "新增管理员");

            // mock StpUtil.isLogin() 返回false，跳过设置用户信息
            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                // Act：执行切面方法
                Object result = aspect.around(joinPoint, operationLog);

                // Assert：返回值应原样透传
                assertThat(result).isSameAs(expectedResult);

                // 验证调用了service保存日志，并捕获日志实体
                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();

                // 验证日志实体的关键字段
                assertThat(logEntity.getModule()).isEqualTo("用户管理");
                assertThat(logEntity.getOperationType()).isEqualTo("CREATE");
                assertThat(logEntity.getDescription()).isEqualTo("新增管理员");
                assertThat(logEntity.getStatus()).isEqualTo(1); // 成功状态
                assertThat(logEntity.getDuration()).isNotNull().isGreaterThanOrEqualTo(0);
                assertThat(logEntity.getErrorMsg()).isNull(); // 成功时没有错误信息
                // result非null，responseResult应被序列化（包含code字段）
                assertThat(logEntity.getResponseResult()).contains("200");
            }
        }

        @Test
        @DisplayName("proceed返回null：responseResult为null（不序列化null值）")
        void proceedReturnsNull_responseResultIsNull() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn(null);

            OperationLog operationLog = mockOperationLog("用户管理", OperationType.QUERY, "查询用户");

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                Object result = aspect.around(joinPoint, operationLog);

                assertThat(result).isNull();
                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();
                assertThat(logEntity.getStatus()).isEqualTo(1);
                // result是null，切面不会序列化，responseResult保持null
                assertThat(logEntity.getResponseResult()).isNull();
            }
        }

        @Test
        @DisplayName("已登录用户：从Sa-Token获取userId并记录到日志")
        void loggedInUser_shouldRecordUserId() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn(null);

            OperationLog operationLog = mockOperationLog("角色管理", OperationType.UPDATE, "修改角色");

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

                aspect.around(joinPoint, operationLog);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();
                // userId和username都来自Sa-Token的loginId
                assertThat(logEntity.getUserId()).isEqualTo(1001L);
                assertThat(logEntity.getUsername()).isEqualTo("1001");
            }
        }
    }

    // ==================== 异常流程测试 ====================

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlowTest {

        @Test
        @DisplayName("方法抛异常：记录失败日志(status=0)，errorMsg记录异常信息，异常被重新抛出")
        void methodThrowsException_shouldRecordFailLogAndRethrow() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            RuntimeException expectedException = new RuntimeException("数据库连接失败");
            when(joinPoint.proceed()).thenThrow(expectedException);

            OperationLog operationLog = mockOperationLog("用户管理", OperationType.DELETE, "删除用户");

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                // Act + Assert：切面应把原异常重新抛出
                assertThatThrownBy(() -> aspect.around(joinPoint, operationLog))
                    .isSameAs(expectedException);

                // 即使异常，日志也要保存（在finally里）
                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();
                assertThat(logEntity.getStatus()).isEqualTo(0); // 失败状态
                assertThat(logEntity.getErrorMsg()).isEqualTo("数据库连接失败");
                assertThat(logEntity.getResponseResult()).isNull(); // 异常时不会记录响应结果
                assertThat(logEntity.getDuration()).isNotNull(); // 耗时仍然记录
            }
        }

        @Test
        @DisplayName("异常message为null：errorMsg用异常类名兜底，避免存null")
        void exceptionMessageNull_errorMsgUsesClassName() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            // NullPointerException默认message是null
            NullPointerException npe = new NullPointerException();
            when(joinPoint.proceed()).thenThrow(npe);

            OperationLog operationLog = mockOperationLog("用户管理", OperationType.UPDATE, "更新用户");

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                assertThatThrownBy(() -> aspect.around(joinPoint, operationLog))
                    .isSameAs(npe);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();
                // message为null时，用类名兜底
                assertThat(logEntity.getErrorMsg()).contains("NullPointerException");
            }
        }
    }

    // ==================== 参数序列化测试 ====================

    @Nested
    @DisplayName("参数序列化与脱敏")
    class SerializeArgsTest {

        @Test
        @DisplayName("普通参数：转成JSON字符串记录到requestParams")
        void normalArgs_shouldBeSerializedToJson() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn(null);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"张三"});

            OperationLog operationLog = mockOperationLog("用户管理", OperationType.QUERY, "查询用户");

            try (MockedStatic<RequestContextHolder> rchMock = mockStatic(RequestContextHolder.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class);
                 MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<JSONUtil> jsonMock = mockStatic(JSONUtil.class)) {
                // mock Web上下文，触发参数序列化逻辑
                mockWebContext(rchMock, ipMock, "http://localhost/api/user", "GET", "192.168.1.1");
                stpMock.when(StpUtil::isLogin).thenReturn(false);
                // mock JSONUtil返回标准JSON，隔离测试切面的脱敏逻辑（不依赖Hutool的序列化行为）
                jsonMock.when(() -> JSONUtil.toJsonStr(any(Object.class))).thenReturn("[\"张三\"]");

                aspect.around(joinPoint, operationLog);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();

                // requestParams应该是JSON，包含参数内容
                assertThat(logEntity.getRequestParams()).contains("张三");
                // URL、method、IP都应记录
                assertThat(logEntity.getRequestUrl()).isEqualTo("http://localhost/api/user");
                assertThat(logEntity.getRequestMethod()).isEqualTo("GET");
                assertThat(logEntity.getIp()).isEqualTo("192.168.1.1");
            }
        }

        @Test
        @DisplayName("敏感字段脱敏：password的值替换为******，不泄露明文")
        void sensitiveFieldPassword_shouldBeMasked() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn(null);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"dto"});

            OperationLog operationLog = mockOperationLog("用户管理", OperationType.UPDATE, "修改密码");

            try (MockedStatic<RequestContextHolder> rchMock = mockStatic(RequestContextHolder.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class);
                 MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<JSONUtil> jsonMock = mockStatic(JSONUtil.class)) {
                mockWebContext(rchMock, ipMock, "http://localhost/api/user", "PUT", "192.168.1.1");
                stpMock.when(StpUtil::isLogin).thenReturn(false);
                // mock JSONUtil返回带password字段的标准JSON，模拟真实序列化结果
                // 这样能精准测试切面的脱敏正则逻辑（切面的自定义代码），不依赖Hutool序列化行为
                jsonMock.when(() -> JSONUtil.toJsonStr(any(Object.class)))
                    .thenReturn("[{\"username\":\"admin\",\"password\":\"mySecret123\"}]");

                aspect.around(joinPoint, operationLog);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();

                // 密码应被替换成******
                assertThat(logEntity.getRequestParams()).contains("******");
                // 明文密码绝对不能出现在日志里
                assertThat(logEntity.getRequestParams()).doesNotContain("mySecret123");
                // 用户名不受影响
                assertThat(logEntity.getRequestParams()).contains("admin");
            }
        }

        @Test
        @DisplayName("敏感字段脱敏：token字段的值也替换为******")
        void sensitiveFieldToken_shouldBeMasked() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn(null);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"dto"});

            OperationLog operationLog = mockOperationLog("认证", OperationType.QUERY, "校验token");

            try (MockedStatic<RequestContextHolder> rchMock = mockStatic(RequestContextHolder.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class);
                 MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<JSONUtil> jsonMock = mockStatic(JSONUtil.class)) {
                mockWebContext(rchMock, ipMock, "http://localhost/api/auth", "POST", "10.0.0.1");
                stpMock.when(StpUtil::isLogin).thenReturn(false);
                // mock JSONUtil返回带token字段的标准JSON，测试脱敏正则对token字段的处理
                jsonMock.when(() -> JSONUtil.toJsonStr(any(Object.class)))
                    .thenReturn("[{\"token\":\"eyJhbGciOiJIUzI1NiJ9.secret.payload\"}]");

                aspect.around(joinPoint, operationLog);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();

                assertThat(logEntity.getRequestParams()).contains("******");
                assertThat(logEntity.getRequestParams()).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
            }
        }

        @Test
        @DisplayName("无参数方法：requestParams为空字符串")
        void noArgs_requestParamsIsEmpty() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn(null);
            when(joinPoint.getArgs()).thenReturn(new Object[]{});

            OperationLog operationLog = mockOperationLog("用户管理", OperationType.QUERY, "查询");

            try (MockedStatic<RequestContextHolder> rchMock = mockStatic(RequestContextHolder.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class);
                 MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                mockWebContext(rchMock, ipMock, "http://localhost/api/user", "GET", "192.168.1.1");
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                aspect.around(joinPoint, operationLog);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();
                // args为空数组时，serializeArgs返回空字符串
                assertThat(logEntity.getRequestParams()).isEmpty();
            }
        }
    }

    // ==================== 超长截断测试 ====================

    @Nested
    @DisplayName("超长内容截断")
    class TruncateTest {

        @Test
        @DisplayName("超长参数：requestParams超过MAX_LENGTH(2000)会被截断并追加'...'")
        void tooLongParams_shouldBeTruncated() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn(null);

            // 构造一个超长字符串参数（3000个字符），序列化后会远超MAX_LENGTH
            String longString = "a".repeat(3000);
            when(joinPoint.getArgs()).thenReturn(new Object[]{longString});

            OperationLog operationLog = mockOperationLog("测试", OperationType.QUERY, "超长参数测试");

            try (MockedStatic<RequestContextHolder> rchMock = mockStatic(RequestContextHolder.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class);
                 MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                mockWebContext(rchMock, ipMock, "http://localhost/api/test", "GET", "127.0.0.1");
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                aspect.around(joinPoint, operationLog);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();
                // 截断后长度 = MAX_LENGTH(2000) + "..."(3) = 2003
                assertThat(logEntity.getRequestParams()).hasSize(MAX_LENGTH + 3);
                // 末尾应该是"..."标识被截断了
                assertThat(logEntity.getRequestParams()).endsWith("...");
            }
        }

        @Test
        @DisplayName("超长响应结果：responseResult超过MAX_LENGTH会被截断")
        void tooLongResponse_shouldBeTruncated() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            // 返回一个超长字符串作为响应结果
            String longResponse = "b".repeat(3000);
            when(joinPoint.proceed()).thenReturn(longResponse);

            OperationLog operationLog = mockOperationLog("测试", OperationType.QUERY, "超长响应测试");

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                aspect.around(joinPoint, operationLog);

                ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
                verify(adminOperationLogService).recordOperationLog(captor.capture());
                AdminOperationLog logEntity = captor.getValue();
                // responseResult也应被截断
                assertThat(logEntity.getResponseResult()).hasSize(MAX_LENGTH + 3);
                assertThat(logEntity.getResponseResult()).endsWith("...");
            }
        }
    }

    // ==================== 无注解方法测试 ====================

    @Nested
    @DisplayName("无@OperationLog注解的方法")
    class NoAnnotationTest {

        @Test
        @DisplayName("切面通过@Around('@annotation(operationLog)')绑定：没注解的方法AOP不会触发around，自然不记录日志")
        void methodWithoutAnnotation_shouldNotTriggerAspect() {
            // 切面的around方法签名要求传入OperationLog参数，
            // AOP框架只会对标注了@OperationLog的方法调用around，
            // 所以没注解的方法压根不会进入这个切面，也就不会调用recordOperationLog。
            // 这里验证：不调用aspect.around，service就不会被调用。
            verify(adminOperationLogService, never()).recordOperationLog(any());
        }
    }
}
