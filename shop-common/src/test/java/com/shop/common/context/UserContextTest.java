package com.shop.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserContext 用户上下文工具类的单元测试
 * <p>
 * UserContext 用 ThreadLocal 存储当前登录用户ID，让 Service 层任何地方都能取到。
 * 重点验证：设置/获取/清除/登录判断 的行为，以及线程隔离性。
 * </p>
 */
@DisplayName("UserContext 用户上下文测试")
class UserContextTest {

    @AfterEach
    void cleanup() {
        // 每个测试结束后清理 ThreadLocal，防止影响其他测试
        UserContext.clear();
    }

    // ==================== 基本操作测试 ====================

    @Nested
    @DisplayName("基本操作")
    class BasicOperationTest {

        @Test
        @DisplayName("setUserId + getUserId：设置后应能取到")
        void setAndGetUserId() {
            UserContext.setUserId(1001L);

            assertThat(UserContext.getUserId()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("getUserId：未设置时返回null")
        void getUserIdWhenNotSet() {
            // 没设置过，应该返回 null
            assertThat(UserContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("clear：清除后getUserId返回null")
        void clearShouldRemoveUserId() {
            // 先设置再清除
            UserContext.setUserId(1001L);
            assertThat(UserContext.getUserId()).isEqualTo(1001L);

            UserContext.clear();
            assertThat(UserContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("setUserId(null)：设置为null后isLogin应为false")
        void setNullUserId() {
            UserContext.setUserId(null);

            assertThat(UserContext.getUserId()).isNull();
            assertThat(UserContext.isLogin()).isFalse();
        }
    }

    // ==================== 登录状态判断测试 ====================

    @Nested
    @DisplayName("isLogin 登录状态判断")
    class IsLoginTest {

        @Test
        @DisplayName("未设置用户ID：isLogin返回false")
        void isLoginWhenNotSet() {
            assertThat(UserContext.isLogin()).isFalse();
        }

        @Test
        @DisplayName("已设置用户ID：isLogin返回true")
        void isLoginWhenSet() {
            UserContext.setUserId(1001L);
            assertThat(UserContext.isLogin()).isTrue();
        }

        @Test
        @DisplayName("清除后：isLogin返回false")
        void isLoginAfterClear() {
            UserContext.setUserId(1001L);
            assertThat(UserContext.isLogin()).isTrue();

            UserContext.clear();
            assertThat(UserContext.isLogin()).isFalse();
        }
    }

    // ==================== 线程隔离性测试 ====================

    @Test
    @DisplayName("线程隔离：主线程设置的用户ID不应影响子线程")
    void threadIsolation() throws InterruptedException {
        // 主线程设置用户ID
        UserContext.setUserId(1001L);
        assertThat(UserContext.getUserId()).isEqualTo(1001L);

        // 用数组捕获子线程的结果（lambda 里不能直接用外部变量）
        Long[] childThreadUserId = new Long[1];

        // 启动子线程，它看不到主线程设置的 1001
        Thread thread = new Thread(() -> {
            // 子线程没设置过，应该是 null
            childThreadUserId[0] = UserContext.getUserId();
        });
        thread.start();
        thread.join(); // 等待子线程结束

        // 子线程应该拿不到主线程的值
        assertThat(childThreadUserId[0]).isNull();
        // 主线程的值不受影响
        assertThat(UserContext.getUserId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("线程隔离：子线程设置的值不影响主线程")
    void threadIsolationReverse() throws InterruptedException {
        // 主线程一开始没设置
        assertThat(UserContext.getUserId()).isNull();

        Thread thread = new Thread(() -> {
            // 子线程设置自己的用户ID
            UserContext.setUserId(9999L);
            assertThat(UserContext.getUserId()).isEqualTo(9999L);
            // 子线程结束时清理
            UserContext.clear();
        });
        thread.start();
        thread.join();

        // 主线程仍然是 null
        assertThat(UserContext.getUserId()).isNull();
    }
}
