package com.shop.merchant.feign;

import com.shop.common.result.Result;
import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单服务Feign降级工厂（OrderFeignClientFallbackFactory）的单元测试
 * <p>
 * 这个测试类用来验证：当订单服务挂掉时，FallbackFactory 生成的"降级版"客户端会怎么响应。
 * 简单理解：商家统计功能依赖订单服务（shop-order）拿数据，
 * 如果订单服务挂了，统计功能要能优雅降级，不能让商家工作台直接报错。
 * </p>
 * <p>
 * 订单服务的降级策略（全部是查询类接口，统一处理）：
 * - 所有统计接口降级时都返回 Result.fail("统计服务暂不可用")
 * - 不抛异常（统计是辅助功能，不能因为统计挂了影响商家主流程）
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isFalse()
 * </p>
 * <p>
 * 测试覆盖的4个方法：getDashboardStats、getSalesTrend、getDataOverview、getProductRank
 * </p>
 */
@DisplayName("订单服务降级工厂 OrderFeignClientFallbackFactory 单元测试")
class OrderFeignClientFallbackFactoryTest {

    /** 被测试的降级工厂，直接new出来即可（它没有依赖需要Mock） */
    private final OrderFeignClientFallbackFactory fallbackFactory = new OrderFeignClientFallbackFactory();

    /**
     * 创建降级实例
     * <p>
     * 小白理解：模拟订单服务挂掉的场景，调用 FallbackFactory.create(失败原因) 拿到一个降级版的客户端。
     * 这个降级实例的每个方法都已经被重写过，会按预定的策略返回降级响应。
     * </p>
     *
     * @return 降级版OrderFeignClient实例
     */
    private OrderFeignClient createFallback() {
        // cause参数是失败原因，Feign框架会自动传入真实的异常，这里用RuntimeException模拟
        return fallbackFactory.create(new RuntimeException("订单服务不可用"));
    }

    // ==================== 统计接口降级：统一返回失败提示 ====================

    @Nested
    @DisplayName("统计接口降级：统一返回Result.fail")
    class StatsFallbackTest {

        @Test
        @DisplayName("getDashboardStats 工作台统计降级 → 返回失败提示")
        void getDashboardStats_returnsFail() {
            // 场景：订单服务挂了，商家打开工作台首页
            OrderFeignClient fallback = createFallback();

            // 调用降级方法
            Result<MerchantDashboardStatsVO> result = fallback.getDashboardStats(1L);

            // 验证：返回失败，提示"统计服务暂不可用"，不抛异常
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("统计服务暂不可用");
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("getSalesTrend 销售趋势降级 → 返回失败提示")
        void getSalesTrend_returnsFail() {
            // 场景：订单服务挂了，商家查看销售趋势图
            OrderFeignClient fallback = createFallback();

            // 调用降级方法
            Result<List<SalesTrendItemVO>> result = fallback.getSalesTrend(1L, 7);

            // 验证：返回失败提示
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("统计服务暂不可用");
        }

        @Test
        @DisplayName("getDataOverview 数据概览降级 → 返回失败提示")
        void getDataOverview_returnsFail() {
            // 场景：订单服务挂了，商家查看数据中心
            OrderFeignClient fallback = createFallback();

            // 调用降级方法
            Result<MerchantDataOverviewVO> result = fallback.getDataOverview(1L, "2026-01-01", "2026-01-31");

            // 验证：返回失败提示
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("统计服务暂不可用");
        }

        @Test
        @DisplayName("getProductRank 商品排行降级 → 返回失败提示")
        void getProductRank_returnsFail() {
            // 场景：订单服务挂了，商家查看商品销量排行
            OrderFeignClient fallback = createFallback();

            // 调用降级方法
            Result<List<ProductRankItemVO>> result = fallback.getProductRank(1L, "2026-01-01", "2026-01-31", 5);

            // 验证：返回失败提示
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("统计服务暂不可用");
        }
    }
}
