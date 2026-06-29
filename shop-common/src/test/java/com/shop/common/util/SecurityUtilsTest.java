package com.shop.common.util;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.context.UserContext;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * SecurityUtils 安全工具类的单元测试
 * <p>
 * SecurityUtils 封装了 Sa-Token 的常用操作。因为依赖 Sa-Token 的静态方法 StpUtil，
 * 这里用 Mockito 的 mockStatic 来模拟 Sa-Token 的行为，不需要真的启动登录流程。
 * </p>
 * <p>
 * 关键点：
 * - getCurrentUserId 优先从 Sa-Token 取，取不到才从 UserContext 取
 * - isLogin 只信任 Sa-Token，不信任 UserContext（防伪造）
 * - requireLogin 未登录时抛 BusinessException(UNAUTHORIZED)
 * </p>
 */
@DisplayName("SecurityUtils 安全工具类测试")
class SecurityUtilsTest {

    @AfterEach
    void cleanup() {
        // 清理 ThreadLocal，防止测试间互相影响
        UserContext.clear();
    }

    // ==================== getCurrentUserId 测试 ====================

    @Nested
    @DisplayName("getCurrentUserId 获取当前用户ID")
    class GetCurrentUserIdTest {

        @Test
        @DisplayName("Sa-Token已登录：应返回Sa-Token的用户ID")
        void saTokenLoggedIn() {
            // mock StpUtil 的静态方法
            try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
                // 模拟已登录
                mockedStpUtil.when(StpUtil::isLogin).thenReturn(true);
                mockedStpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

                Long userId = SecurityUtils.getCurrentUserId();

                assertThat(userId).isEqualTo(1001L);
            }
        }

        @Test
        @DisplayName("Sa-Token未登录但UserContext有值：降级返回UserContext的值")
        void saTokenNotLoggedInButUserContextHasValue() {
            try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
                // Sa-Token 未登录
                mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);

                // UserContext 有值（比如内部接口场景）
                UserContext.setUserId(2002L);

                Long userId = SecurityUtils.getCurrentUserId();

                // 应该降级从 UserContext 取
                assertThat(userId).isEqualTo(2002L);
            }
        }

        @Test
        @DisplayName("Sa-Token未登录且UserContext无值：返回null")
        void bothNotLoggedIn() {
            try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
                mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
                // UserContext 也没设置

                Long userId = SecurityUtils.getCurrentUserId();

                assertThat(userId).isNull();
            }
        }
    }

    // ==================== isLogin 测试 ====================

    @Nested
    @DisplayName("isLogin 判断是否登录")
    class IsLoginTest {

        @Test
        @DisplayName("Sa-Token已登录：返回true")
        void saTokenLoggedIn() {
            try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
                mockedStpUtil.when(StpUtil::isLogin).thenReturn(true);

                assertThat(SecurityUtils.isLogin()).isTrue();
            }
        }

        @Test
        @DisplayName("Sa-Token未登录：返回false，即使UserContext有值也不算登录")
        void saTokenNotLoggedIn() {
            try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
                mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);

                // 即使 UserContext 有值，isLogin 也只信任 Sa-Token
                UserContext.setUserId(1001L);

                assertThat(SecurityUtils.isLogin()).isFalse();
            }
        }
    }

    // ==================== requireLogin 测试 ====================

    @Nested
    @DisplayName("requireLogin 要求登录")
    class RequireLoginTest {

        @Test
        @DisplayName("已登录：返回用户ID")
        void loggedIn() {
            try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
                mockedStpUtil.when(StpUtil::isLogin).thenReturn(true);
                mockedStpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

                Long userId = SecurityUtils.requireLogin();

                assertThat(userId).isEqualTo(1001L);
            }
        }

        @Test
        @DisplayName("未登录：抛出BusinessException(UNAUTHORIZED)")
        void notLoggedIn() {
            try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
                mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);

                assertThatThrownBy(SecurityUtils::requireLogin)
                        .isInstanceOf(BusinessException.class)
                        .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED.getCode()));
            }
        }
    }

    // ==================== checkPermission / checkRole 测试 ====================

    @Test
    @DisplayName("checkPermission：应委托给StpUtil.checkPermission")
    void checkPermission() {
        try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
            // 验证 StpUtil.checkPermission 被调用，参数为 "user:delete"
            SecurityUtils.checkPermission("user:delete");

            mockedStpUtil.verify(() -> StpUtil.checkPermission("user:delete"));
        }
    }

    @Test
    @DisplayName("checkRole：应委托给StpUtil.checkRole")
    void checkRole() {
        try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
            SecurityUtils.checkRole("admin");

            mockedStpUtil.verify(() -> StpUtil.checkRole("admin"));
        }
    }

    // ==================== 私有构造方法测试 ====================

    @Test
    @DisplayName("私有构造方法：通过反射验证无法直接new（工具类设计）")
    void privateConstructor() throws Exception {
        // 工具类应该是私有构造方法，防止被实例化
        // 这里通过反射验证构造方法是私有的
        var constructor = SecurityUtils.class.getDeclaredConstructor();
        assertThat(constructor.canAccess(null)).isFalse();
    }
}
