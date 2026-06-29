package com.shop.admin.security;

import com.shop.admin.annotation.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DataScopeContextHolder 数据权限上下文的单元测试
 * <p>
 * 这个测试类验证 ThreadLocal 能不能正确地在 AOP 切面和 MyBatis 拦截器之间传递数据权限配置。
 * 简单理解：切面把 @DataScope 注解放进 ThreadLocal"中转站"，
 * 拦截器再从中转站取出来用，用完要清理（防止内存泄漏）。
 * </p>
 * <p>
 * 重点验证：set/get/clear 三个基本操作，以及线程隔离性
 * （主线程的值不能被子线程看到，反之亦然，否则会串号）。
 * </p>
 */
@DisplayName("DataScopeContextHolder 数据权限上下文测试")
class DataScopeContextHolderTest {

    @AfterEach
    void cleanup() {
        // 每个测试结束后清理 ThreadLocal，防止影响其他测试
        // 这一点很重要，因为 ThreadLocal 的值不会随测试结束自动消失
        DataScopeContextHolder.clear();
    }

    /**
     * 创建一个 mock 的 @DataScope 注解实例
     * （注解本质是接口，可以用 Mockito mock 出来用）
     */
    private DataScope mockDataScope(String deptAlias, String userAlias) {
        DataScope dataScope = mock(DataScope.class);
        when(dataScope.deptAlias()).thenReturn(deptAlias);
        when(dataScope.userAlias()).thenReturn(userAlias);
        return dataScope;
    }

    // ==================== 基本操作测试 ====================

    @Nested
    @DisplayName("基本操作：set/get/clear")
    class BasicOperationTest {

        @Test
        @DisplayName("set + get：设置后应该能取到同一个对象")
        void setAndGetShouldReturnSameInstance() {
            // 准备一个@DataScope注解实例
            DataScope dataScope = mockDataScope("d", "u");

            // 放进ThreadLocal
            DataScopeContextHolder.set(dataScope);

            // 取出来应该是同一个对象（用isSameAs验证引用相等）
            assertThat(DataScopeContextHolder.get()).isSameAs(dataScope);
        }

        @Test
        @DisplayName("get 未设置：返回null（表示当前方法不需要数据权限过滤）")
        void getWhenNotSetShouldReturnNull() {
            // 没设置过，应该返回 null
            // 实际场景：MyBatis拦截器拿到null就知道这个查询不需要数据权限过滤
            assertThat(DataScopeContextHolder.get()).isNull();
        }

        @Test
        @DisplayName("clear：清除后再get返回null")
        void clearShouldRemoveValue() {
            // 先设置
            DataScope dataScope = mockDataScope("d", "u");
            DataScopeContextHolder.set(dataScope);
            assertThat(DataScopeContextHolder.get()).isSameAs(dataScope);

            // 清除
            DataScopeContextHolder.clear();

            // 再取就是null了
            assertThat(DataScopeContextHolder.get()).isNull();
        }

        @Test
        @DisplayName("set 覆盖：连续set两次，get返回最后一次设置的值")
        void setTwiceShouldReturnLatestValue() {
            DataScope first = mockDataScope("d1", "u1");
            DataScope second = mockDataScope("d2", "u2");

            DataScopeContextHolder.set(first);
            DataScopeContextHolder.set(second);

            // 应该返回最后一次设置的
            assertThat(DataScopeContextHolder.get()).isSameAs(second);
        }

        @Test
        @DisplayName("set null：允许设置null，get返回null")
        void setNullShouldReturnNull() {
            DataScopeContextHolder.set(null);

            assertThat(DataScopeContextHolder.get()).isNull();
        }
    }

    // ==================== 线程隔离性测试 ====================

    @Nested
    @DisplayName("线程隔离：ThreadLocal的核心特性")
    class ThreadIsolationTest {

        @Test
        @DisplayName("线程隔离：主线程设置的值，子线程看不到")
        void mainThreadValueNotVisibleToChildThread() throws InterruptedException {
            // 主线程设置一个值
            DataScope dataScope = mockDataScope("d", "u");
            DataScopeContextHolder.set(dataScope);
            assertThat(DataScopeContextHolder.get()).isSameAs(dataScope);

            // 用数组捕获子线程的取值结果（lambda里不能直接用外部可变变量）
            DataScope[] childResult = new DataScope[1];

            // 启动子线程，它应该看不到主线程设置的值
            Thread thread = new Thread(() -> {
                childResult[0] = DataScopeContextHolder.get();
            });
            thread.start();
            thread.join(); // 等子线程跑完

            // 子线程拿到的是null（线程隔离）
            assertThat(childResult[0]).isNull();
            // 主线程的值不受影响
            assertThat(DataScopeContextHolder.get()).isSameAs(dataScope);
        }

        @Test
        @DisplayName("线程隔离：子线程设置的值，主线程看不到")
        void childThreadValueNotVisibleToMainThread() throws InterruptedException {
            // 主线程一开始没设置
            assertThat(DataScopeContextHolder.get()).isNull();

            DataScope childDataScope = mockDataScope("d2", "u2");

            // 启动子线程，在子线程里设置值
            Thread thread = new Thread(() -> {
                DataScopeContextHolder.set(childDataScope);
                // 子线程自己能看到
                assertThat(DataScopeContextHolder.get()).isSameAs(childDataScope);
                // 子线程结束时清理（养成好习惯，防止线程池复用时串号）
                DataScopeContextHolder.clear();
            });
            thread.start();
            thread.join();

            // 主线程依然是null（子线程的设置不影响主线程）
            assertThat(DataScopeContextHolder.get()).isNull();
        }

        @Test
        @DisplayName("线程隔离：两个子线程各自设置的值互不影响")
        void twoChildThreadsIsolatedFromEachOther() throws InterruptedException {
            DataScope ds1 = mockDataScope("d1", "u1");
            DataScope ds2 = mockDataScope("d2", "u2");

            // 用数组捕获两个子线程各自看到的值
            DataScope[] result1 = new DataScope[1];
            DataScope[] result2 = new DataScope[1];

            Thread t1 = new Thread(() -> {
                DataScopeContextHolder.set(ds1);
                // 睡一小会儿，确保和t2并发执行
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                result1[0] = DataScopeContextHolder.get();
                DataScopeContextHolder.clear();
            });

            Thread t2 = new Thread(() -> {
                DataScopeContextHolder.set(ds2);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                result2[0] = DataScopeContextHolder.get();
                DataScopeContextHolder.clear();
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // 两个子线程各自拿到自己设置的值，互不干扰
            assertThat(result1[0]).isSameAs(ds1);
            assertThat(result2[0]).isSameAs(ds2);
        }
    }
}
