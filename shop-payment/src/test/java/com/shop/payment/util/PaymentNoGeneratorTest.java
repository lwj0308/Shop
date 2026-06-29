package com.shop.payment.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付单号生成器（PaymentNoGenerator）的单元测试
 * <p>
 * 这个测试类验证支付单号能不能正确生成，以及生成的单号是不是唯一的。
 * 简单理解：每次调用 generate() 都应该返回一个以 "PAY" 开头的唯一编号，
 * 不能重复，否则两笔支付可能用同一个单号，造成数据混乱。
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).startsWith("PAY")
 * </p>
 */
@DisplayName("PaymentNoGenerator 支付单号生成器测试")
class PaymentNoGeneratorTest {

    // ==================== generate 生成支付单号 ====================

    @Nested
    @DisplayName("generate 生成支付单号")
    class GenerateTest {

        @Test
        @DisplayName("generate()：返回非空字符串，不能是null或空串")
        void generate_returnsNonEmptyString() {
            // 调用生成方法
            String paymentNo = PaymentNoGenerator.generate();

            // 验证：返回值不为null，且不是空字符串
            assertThat(paymentNo).isNotNull();
            assertThat(paymentNo).isNotEmpty();
        }

        @Test
        @DisplayName("generate()：返回的支付单号以PAY开头，方便识别这是支付单号")
        void generate_startsPayPrefix() {
            String paymentNo = PaymentNoGenerator.generate();

            // 验证：以PAY开头（比如 PAY1234567890123456789）
            assertThat(paymentNo).startsWith("PAY");
        }

        @Test
        @DisplayName("多次调用生成不同ID：连续调用两次不能返回相同的单号")
        void generate_multipleCalls_returnDifferentIds() {
            // 连续调用两次
            String no1 = PaymentNoGenerator.generate();
            String no2 = PaymentNoGenerator.generate();

            // 验证：两次生成的单号不一样（雪花算法保证唯一）
            assertThat(no1).isNotEqualTo(no2);
        }

        @Test
        @DisplayName("1000次调用全部唯一：批量生成1000个单号，不能有重复")
        void generate_1000Calls_allUnique() {
            // 用Set存放生成的单号，Set会自动去重
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(PaymentNoGenerator.generate());
            }

            // 验证：1000次调用应该生成1000个不同的单号
            // 如果Set的size小于1000，说明有重复（雪花算法出问题了）
            assertThat(ids).hasSize(1000);
        }
    }
}
