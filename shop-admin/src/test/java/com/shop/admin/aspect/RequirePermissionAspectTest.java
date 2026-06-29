package com.shop.admin.aspect;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.admin.annotation.Logical;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminSecurityEventService;
import com.shop.admin.util.IpUtils;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RequirePermissionAspect 权限校验切面的单元测试
 * <p>
 * 这个测试类验证切面能不能正确地拦截 @RequirePermission 注解的方法，
 * 在方法执行前校验当前管理员是否有所需权限。
 * 简单理解：超级管理员直接放行；普通管理员按 AND/OR 逻辑校验权限；
 * 校验不通过就记录安全事件并抛 FORBIDDEN 异常。
 * </p>
 * <p>
 * 测试要点：
 * 1. 用 @Mock 模拟 AdminSecurityEventService（不真连数据库）
 * 2. 用 mockStatic 模拟 StpUtil（Sa-Token）和 IpUtils 这些静态方法
 * 3. 超级管理员（角色含"admin"）跳过校验
 * 4. AND 逻辑要全部权限，OR 逻辑只要一个
 * </p>
 */
@DisplayName("RequirePermissionAspect 权限校验切面测试")
@ExtendWith(MockitoExtension.class)
class RequirePermissionAspectTest {

    /** 被测切面，依赖通过 @InjectMocks 自动注入 */
    @InjectMocks
    private RequirePermissionAspect aspect;

    /** mock的安全事件服务，验证越权时是否记录了事件 */
    @Mock
    private AdminSecurityEventService adminSecurityEventService;

    /**
     * 创建一个 mock 的 @RequirePermission 注解实例
     * <p>
     * 注解本质是接口，用 Mockito mock 出来，指定 value() 和 logical() 的返回值。
     * </p>
     */
    private RequirePermission mockRequirePermission(String[] value, Logical logical) {
        RequirePermission rp = mock(RequirePermission.class);
        when(rp.value()).thenReturn(value);
        when(rp.logical()).thenReturn(logical);
        return rp;
    }

    // ==================== 超级管理员测试 ====================

    @Nested
    @DisplayName("超级管理员（角色含admin）")
    class SuperAdminTest {

        @Test
        @DisplayName("admin角色：跳过权限校验，直接proceed，不记录安全事件")
        void adminRole_shouldBypassPermissionCheck() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            Object expectedResult = "ok";
            when(joinPoint.proceed()).thenReturn(expectedResult);

            // 即使声明需要权限，admin角色也直接放行
            RequirePermission rp = mockRequirePermission(new String[]{"user:list", "user:delete"}, Logical.AND);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
                // 角色列表包含"admin" → 超级管理员
                stpMock.when(StpUtil::getRoleList).thenReturn(Arrays.asList("admin", "user"));

                Object result = aspect.around(joinPoint, rp);

                assertThat(result).isEqualTo(expectedResult);
                verify(joinPoint, times(1)).proceed();
                // 超级管理员不会触发安全事件
                verify(adminSecurityEventService, never())
                    .recordSecurityEvent(anyString(), any(), anyString(), anyString(), anyString());
            }
        }
    }

    // ==================== AND 逻辑校验测试 ====================

    @Nested
    @DisplayName("AND逻辑（需要全部权限）")
    class AndLogicalTest {

        @Test
        @DisplayName("拥有全部权限：校验通过，proceed执行，返回结果透传")
        void hasAllPermissions_shouldProceed() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            Object expectedResult = "success";
            when(joinPoint.proceed()).thenReturn(expectedResult);

            RequirePermission rp = mockRequirePermission(new String[]{"user:list", "user:detail"}, Logical.AND);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
                stpMock.when(StpUtil::getRoleList).thenReturn(Collections.singletonList("user"));
                // 拥有全部所需权限（甚至更多）
                stpMock.when(StpUtil::getPermissionList).thenReturn(Arrays.asList("user:list", "user:detail", "user:add"));

                Object result = aspect.around(joinPoint, rp);

                assertThat(result).isSameAs(expectedResult);
                verify(joinPoint, times(1)).proceed();
                verify(adminSecurityEventService, never())
                    .recordSecurityEvent(anyString(), any(), anyString(), anyString(), anyString());
            }
        }

        @Test
        @DisplayName("缺少一个权限：校验失败，记录安全事件，抛FORBIDDEN异常，不执行proceed")
        void missingOnePermission_shouldRecordEventAndThrow() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

            RequirePermission rp = mockRequirePermission(new String[]{"user:list", "user:detail"}, Logical.AND);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
                stpMock.when(StpUtil::getRoleList).thenReturn(Collections.singletonList("user"));
                // 只有user:list，缺user:detail
                stpMock.when(StpUtil::getPermissionList).thenReturn(Collections.singletonList("user:list"));
                ipMock.when(IpUtils::getClientIp).thenReturn("10.0.0.1");

                // Act + Assert：应抛出BusinessException(FORBIDDEN)
                assertThatThrownBy(() -> aspect.around(joinPoint, rp))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.FORBIDDEN.getMessage());

                // 方法不应该被执行
                verify(joinPoint, never()).proceed();

                // 应该记录安全事件，捕获参数验证
                ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
                ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
                verify(adminSecurityEventService).recordSecurityEvent(
                    typeCaptor.capture(), userIdCaptor.capture(), usernameCaptor.capture(),
                    detailCaptor.capture(), ipCaptor.capture());

                // 验证安全事件的字段
                assertThat(typeCaptor.getValue()).isEqualTo("权限越权");
                assertThat(userIdCaptor.getValue()).isEqualTo(100L);
                // username用的是userId的字符串形式
                assertThat(usernameCaptor.getValue()).isEqualTo("100");
                // 详情应该包含所需的权限标识
                assertThat(detailCaptor.getValue()).contains("user:list").contains("user:detail");
                assertThat(ipCaptor.getValue()).isEqualTo("10.0.0.1");
            }
        }

        @Test
        @DisplayName("一个权限都没有：校验失败，抛FORBIDDEN异常")
        void hasNoPermission_shouldThrow() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

            RequirePermission rp = mockRequirePermission(new String[]{"user:list"}, Logical.AND);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
                stpMock.when(StpUtil::getRoleList).thenReturn(Collections.singletonList("user"));
                stpMock.when(StpUtil::getPermissionList).thenReturn(Collections.emptyList());
                ipMock.when(IpUtils::getClientIp).thenReturn("10.0.0.2");

                assertThatThrownBy(() -> aspect.around(joinPoint, rp))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.FORBIDDEN.getMessage());

                verify(joinPoint, never()).proceed();
                verify(adminSecurityEventService, times(1))
                    .recordSecurityEvent(anyString(), any(), anyString(), anyString(), anyString());
            }
        }
    }

    // ==================== OR 逻辑校验测试 ====================

    @Nested
    @DisplayName("OR逻辑（只需其中一个权限）")
    class OrLogicalTest {

        @Test
        @DisplayName("拥有其中一个权限：校验通过，proceed执行")
        void hasOnePermission_shouldProceed() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn("ok");

            RequirePermission rp = mockRequirePermission(new String[]{"user:list", "user:detail"}, Logical.OR);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
                stpMock.when(StpUtil::getRoleList).thenReturn(Collections.singletonList("user"));
                // 只有user:detail，OR逻辑下足够了
                stpMock.when(StpUtil::getPermissionList).thenReturn(Collections.singletonList("user:detail"));

                Object result = aspect.around(joinPoint, rp);

                assertThat(result).isEqualTo("ok");
                verify(joinPoint, times(1)).proceed();
                verify(adminSecurityEventService, never())
                    .recordSecurityEvent(anyString(), any(), anyString(), anyString(), anyString());
            }
        }

        @Test
        @DisplayName("拥有第一个权限：校验通过（验证OR的短路逻辑）")
        void hasFirstPermission_shouldProceed() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.proceed()).thenReturn("ok");

            RequirePermission rp = mockRequirePermission(new String[]{"user:list", "user:detail"}, Logical.OR);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
                stpMock.when(StpUtil::getRoleList).thenReturn(Collections.singletonList("user"));
                stpMock.when(StpUtil::getPermissionList).thenReturn(Collections.singletonList("user:list"));

                Object result = aspect.around(joinPoint, rp);

                assertThat(result).isEqualTo("ok");
                verify(joinPoint, times(1)).proceed();
            }
        }

        @Test
        @DisplayName("一个权限都没有：OR逻辑下也校验失败")
        void hasNoPermissionOrLogic_shouldThrow() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

            RequirePermission rp = mockRequirePermission(new String[]{"user:list", "user:detail"}, Logical.OR);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<IpUtils> ipMock = mockStatic(IpUtils.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
                stpMock.when(StpUtil::getRoleList).thenReturn(Collections.singletonList("user"));
                stpMock.when(StpUtil::getPermissionList).thenReturn(Collections.emptyList());
                ipMock.when(IpUtils::getClientIp).thenReturn("10.0.0.3");

                assertThatThrownBy(() -> aspect.around(joinPoint, rp))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.FORBIDDEN.getMessage());

                verify(joinPoint, never()).proceed();
                verify(adminSecurityEventService, times(1))
                    .recordSecurityEvent(anyString(), any(), anyString(), anyString(), anyString());
            }
        }
    }

    // ==================== 方法执行成功测试 ====================

    @Nested
    @DisplayName("方法执行成功")
    class MethodExecuteTest {

        @Test
        @DisplayName("权限校验通过后proceed被调用一次，返回结果原样透传")
        void permissionPass_proceedCalledOnceAndResultReturned() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            Object expectedResult = new Object();
            when(joinPoint.proceed()).thenReturn(expectedResult);

            RequirePermission rp = mockRequirePermission(new String[]{"user:list"}, Logical.AND);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
                stpMock.when(StpUtil::getRoleList).thenReturn(Collections.singletonList("user"));
                stpMock.when(StpUtil::getPermissionList).thenReturn(Collections.singletonList("user:list"));

                Object result = aspect.around(joinPoint, rp);

                // 返回值应该是proceed的返回值（同一个对象）
                assertThat(result).isSameAs(expectedResult);
                // proceed只被调用一次
                verify(joinPoint, times(1)).proceed();
            }
        }
    }

    // ==================== 未登录测试 ====================

    @Nested
    @DisplayName("未登录场景")
    class NotLoginTest {

        @Test
        @DisplayName("未登录：StpUtil.getLoginIdAsLong抛NotLoginException → 异常直接向上抛出，不执行proceed")
        void notLogin_shouldThrowNotLoginExceptionAndNotProceed() throws Throwable {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

            RequirePermission rp = mockRequirePermission(new String[]{"user:list"}, Logical.AND);

            // 用mock创建NotLoginException实例，避免依赖具体构造函数签名
            // （不同sa-token版本的NotLoginException构造函数参数不同）
            NotLoginException notLoginException = mock(NotLoginException.class);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                // 模拟未登录：getLoginIdAsLong抛NotLoginException
                stpMock.when(StpUtil::getLoginIdAsLong).thenThrow(notLoginException);

                // 切面没有捕获这个异常，会直接向上抛出
                assertThatThrownBy(() -> aspect.around(joinPoint, rp))
                    .isSameAs(notLoginException);

                // 未登录时方法不会被执行
                verify(joinPoint, never()).proceed();
                // 注意：切面在getLoginIdAsLong就抛异常了，还没走到记录安全事件的代码，
                // 所以这里不会记录安全事件（NotLoginException直接向上抛，由全局异常处理器统一处理）
                verify(adminSecurityEventService, never())
                    .recordSecurityEvent(anyString(), any(), anyString(), anyString(), anyString());
            }
        }
    }
}
