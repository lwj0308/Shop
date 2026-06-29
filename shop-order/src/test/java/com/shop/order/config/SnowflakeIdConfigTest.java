package com.shop.order.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 雪花算法 SnowflakeIdWorker 的单元测试
 * <p>
 * 这个测试类专门验证雪花算法能不能正确生成唯一的、递增的 ID。
 * 简单理解：雪花算法是 Twitter 开源的一种 ID 生成算法，
 * 生成的 ID 是一个 64 位的长整型数字，全局唯一且大致按时间递增。
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test 等注解
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isPositive() 比 assertTrue(x > 0) 更好懂
 * </p>
 */
@DisplayName("雪花算法 SnowflakeIdWorker 测试")
class SnowflakeIdConfigTest {

    // ==================== 1. 构造方法校验测试 ====================

    @Nested
    @DisplayName("构造方法参数校验")
    class ConstructorTest {

        @Test
        @DisplayName("workerId超过最大值31 → 抛出IllegalArgumentException")
        void constructor_workerIdExceedMax_throwsException() {
            // 场景：workerId 传了 32，超过了 5 位能表示的最大值 31
            // 验证：应该抛出 IllegalArgumentException，提示 workerId 超出范围
            assertThatThrownBy(() -> new SnowflakeIdConfig.SnowflakeIdWorker(32, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workerId");
        }

        @Test
        @DisplayName("workerId为负数 → 抛出IllegalArgumentException")
        void constructor_workerIdNegative_throwsException() {
            // 场景：workerId 传了 -1，不能为负数
            assertThatThrownBy(() -> new SnowflakeIdConfig.SnowflakeIdWorker(-1, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workerId");
        }

        @Test
        @DisplayName("datacenterId超过最大值31 → 抛出IllegalArgumentException")
        void constructor_datacenterIdExceedMax_throwsException() {
            // 场景：datacenterId 传了 32，超过了最大值 31
            assertThatThrownBy(() -> new SnowflakeIdConfig.SnowflakeIdWorker(1, 32))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("datacenterId");
        }

        @Test
        @DisplayName("workerId和datacenterId都在合法范围内 → 正常创建")
        void constructor_validParams_createdSuccessfully() {
            // 场景：workerId=0、datacenterId=0，都是合法边界值
            // 验证：能正常创建对象，不抛异常
            SnowflakeIdConfig.SnowflakeIdWorker worker = new SnowflakeIdConfig.SnowflakeIdWorker(0, 0);
            assertThat(worker).isNotNull();
            // 创建后应该能正常生成 ID
            assertThat(worker.nextId()).isPositive();
        }
    }

    // ==================== 2. nextId 生成ID测试 ====================

    @Nested
    @DisplayName("nextId 生成ID")
    class NextIdTest {

        @Test
        @DisplayName("生成的ID应为正数")
        void nextId_returnsPositiveNumber() {
            // 场景：正常生成一个 ID
            SnowflakeIdConfig.SnowflakeIdWorker worker = new SnowflakeIdConfig.SnowflakeIdWorker(1, 1);
            long id = worker.nextId();
            // 验证：ID 是正数（最高位符号位是0）
            assertThat(id).isPositive();
        }

        @Test
        @DisplayName("连续调用生成递增ID（后一个比前一个大）")
        void nextId_consecutiveCalls_incrementing() {
            // 场景：连续生成 3 个 ID，应该一个比一个大
            // 小白理解：同一毫秒内通过序列号递增保证顺序，跨毫秒时间戳变大 ID 也变大
            SnowflakeIdConfig.SnowflakeIdWorker worker = new SnowflakeIdConfig.SnowflakeIdWorker(1, 1);
            long id1 = worker.nextId();
            long id2 = worker.nextId();
            long id3 = worker.nextId();
            assertThat(id2).isGreaterThan(id1);
            assertThat(id3).isGreaterThan(id2);
        }

        @Test
        @DisplayName("连续生成1000个ID → 全部唯一不重复")
        void nextId_generate1000Ids_allUnique() {
            // 场景：连续生成 1000 个 ID，验证全局唯一性
            // 小白理解：雪花算法的核心价值就是保证 ID 不重复
            SnowflakeIdConfig.SnowflakeIdWorker worker = new SnowflakeIdConfig.SnowflakeIdWorker(1, 1);
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(worker.nextId());
            }
            // 验证：1000 个 ID 全部不重复
            assertThat(ids).hasSize(1000);
        }

        @Test
        @DisplayName("不同workerId生成的ID不同（区分机器）")
        void nextId_differentWorkerId_differentIds() {
            // 场景：两台不同机器（workerId 不同）同一时刻生成 ID，ID 应该不同
            // 小白理解：workerId 嵌入在 ID 里，所以不同机器的 ID 不会冲突
            SnowflakeIdConfig.SnowflakeIdWorker worker1 = new SnowflakeIdConfig.SnowflakeIdWorker(1, 1);
            SnowflakeIdConfig.SnowflakeIdWorker worker2 = new SnowflakeIdConfig.SnowflakeIdWorker(2, 1);
            long id1 = worker1.nextId();
            long id2 = worker2.nextId();
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    // ==================== 3. 时钟回拨检测测试 ====================

    @Nested
    @DisplayName("时钟回拨检测")
    class ClockBackwardsTest {

        @Test
        @DisplayName("时钟回拨（当前时间比上次还早） → 抛出RuntimeException")
        void nextId_clockBackwards_throwsException() throws Exception {
            // 场景：系统时钟被调慢了，导致当前时间比上次生成 ID 的时间还早
            // 小白理解：时钟回拨会导致 ID 重复（时间戳变小），所以必须拒绝生成
            SnowflakeIdConfig.SnowflakeIdWorker worker = new SnowflakeIdConfig.SnowflakeIdWorker(1, 1);
            // 先正常生成一个 ID，让内部的 lastTimestamp 被设置
            worker.nextId();

            // 用反射把 lastTimestamp 改成比当前时间大的值，模拟"时钟回拨"
            // 小白理解：lastTimestamp 是 private 字段，正常改不了，用反射可以强行修改
            Field lastTimestampField = SnowflakeIdConfig.SnowflakeIdWorker.class.getDeclaredField("lastTimestamp");
            lastTimestampField.setAccessible(true);
            lastTimestampField.setLong(worker, System.currentTimeMillis() + 10000);

            // 再次生成 ID 时，当前时间 < lastTimestamp，应该抛出时钟回拨异常
            assertThatThrownBy(worker::nextId)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("时钟回拨");
        }
    }
}
