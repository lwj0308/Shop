package com.shop.order.util;

import com.shop.order.config.SnowflakeIdConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单号生成器 OrderNoGenerator 的单元测试
 * <p>
 * 这个测试类验证订单号和退款单号能不能正确生成。
 * 简单理解：OrderNoGenerator 内部用雪花算法生成唯一数字，
 * generate() 直接返回数字字符串作为订单号，
 * generateRefundNo() 在前面加 "RF" 前缀作为退款单号。
 * </p>
 * <p>
 * 测试策略：用真实的 SnowflakeIdWorker（不 Mock），测试 OrderNoGenerator 的真实行为。
 * 这样能同时验证订单号生成器和底层雪花算法的集成是否正常。
 * </p>
 */
@DisplayName("订单号生成器 OrderNoGenerator 测试")
class OrderNoGeneratorTest {

    /** 被测试的订单号生成器 */
    private OrderNoGenerator orderNoGenerator;

    /**
     * 每个测试方法执行前，创建一个全新的 OrderNoGenerator
     * <p>
     * 小白理解：用真实的雪花算法生成器（workerId=1, datacenterId=1），
     * 不用 Mock，因为我们要测试真实的生成逻辑。
     * </p>
     */
    @BeforeEach
    void setUp() {
        SnowflakeIdConfig.SnowflakeIdWorker worker = new SnowflakeIdConfig.SnowflakeIdWorker(1, 1);
        orderNoGenerator = new OrderNoGenerator(worker);
    }

    // ==================== 1. generate 生成订单号 ====================

    @Nested
    @DisplayName("generate 生成订单号")
    class GenerateTest {

        @Test
        @DisplayName("生成的订单号应为非空字符串")
        void generate_returnsNonBlankString() {
            // 场景：正常生成一个订单号
            String orderNo = orderNoGenerator.generate();

            // 验证：订单号不为 null、不为空字符串
            assertThat(orderNo).isNotNull();
            assertThat(orderNo).isNotBlank();
        }

        @Test
        @DisplayName("生成的订单号应为纯数字（雪花算法ID转字符串）")
        void generate_returnsNumericString() {
            // 场景：订单号是雪花算法 ID 转成的字符串，应该是纯数字
            String orderNo = orderNoGenerator.generate();

            // 验证：订单号是纯数字（正则匹配）
            assertThat(orderNo).matches("\\d+");
        }
    }

    // ==================== 2. generateRefundNo 生成退款单号 ====================

    @Nested
    @DisplayName("generateRefundNo 生成退款单号")
    class GenerateRefundNoTest {

        @Test
        @DisplayName("退款单号应以\"RF\"前缀开头")
        void generateRefundNo_startsWithRFPrefix() {
            // 场景：生成一个退款单号
            // 小白理解：退款单号和订单号用同一个雪花算法，加 "RF" 前缀区分
            String refundNo = orderNoGenerator.generateRefundNo();

            // 验证：以 "RF" 开头
            assertThat(refundNo).isNotNull();
            assertThat(refundNo).startsWith("RF");
        }

        @Test
        @DisplayName("退款单号去掉\"RF\"前缀后应为纯数字")
        void generateRefundNo_afterPrefixIsNumeric() {
            // 场景：退款单号格式是 "RF" + 雪花ID
            String refundNo = orderNoGenerator.generateRefundNo();

            // 验证：去掉 "RF" 前缀后是纯数字
            String idPart = refundNo.substring(2);
            assertThat(idPart).matches("\\d+");
        }
    }

    // ==================== 3. 唯一性测试 ====================

    @Nested
    @DisplayName("唯一性校验")
    class UniquenessTest {

        @Test
        @DisplayName("连续生成1000个订单号 → 全部唯一不重复")
        void generate_1000Times_allUnique() {
            // 场景：连续生成 1000 个订单号，验证全局唯一性
            // 小白理解：电商系统订单量很大，订单号绝对不能重复
            Set<String> orderNos = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                orderNos.add(orderNoGenerator.generate());
            }

            // 验证：1000 个订单号全部不重复
            assertThat(orderNos).hasSize(1000);
        }

        @Test
        @DisplayName("订单号和退款单号互不重复")
        void generate_orderNoAndRefundNo_notEqual() {
            // 场景：生成一个订单号和一个退款单号，两者不能相同
            // 小白理解：退款单号有 "RF" 前缀，天然和订单号不同
            String orderNo = orderNoGenerator.generate();
            String refundNo = orderNoGenerator.generateRefundNo();

            assertThat(orderNo).isNotEqualTo(refundNo);
        }

        @Test
        @DisplayName("连续生成的订单号递增（趋势递增，方便排序）")
        void generate_consecutiveCalls_incrementing() {
            // 场景：连续生成 3 个订单号，转成数字后应该递增
            // 小白理解：雪花算法生成的 ID 大致按时间递增，方便数据库索引和排序
            long no1 = Long.parseLong(orderNoGenerator.generate());
            long no2 = Long.parseLong(orderNoGenerator.generate());
            long no3 = Long.parseLong(orderNoGenerator.generate());

            assertThat(no2).isGreaterThan(no1);
            assertThat(no3).isGreaterThan(no2);
        }
    }
}
