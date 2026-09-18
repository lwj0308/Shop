package com.shop.merchant.service.impl;

import com.shop.common.result.Result;
import com.shop.merchant.feign.OrderFeignClient;
import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商家统计服务实现类（MerchantStatsServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证商家工作台、数据中心、销售趋势、商品排行这些统计功能。
 * 简单理解：商家服务自己不存数据，统计都靠远程调用订单服务（OrderFeignClient）拿，
 * 我们把 OrderFeignClient "假装"一下（Mock），让它返回我们想要的结果，
 * 然后看 MerchantStatsServiceImpl 处理得对不对。
 * </p>
 * <p>
 * 被测类的逻辑很简单：调 Feign → 看 Result 成不成 → 成就返回数据，不成就返回 null 或空列表。
 * 所以每个方法测3个场景：
 * - Feign 返回 null（极端情况，比如序列化失败）
 * - Feign 返回 Result.fail（订单服务报错或降级了）
 * - Feign 返回 Result.success（正常情况）
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * </p>
 * <p>
 * 测试覆盖的4个方法：getDashboardStats、getSalesTrend、getDataOverview、getProductRank
 * </p>
 */
@DisplayName("商家统计服务 MerchantStatsServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class MerchantStatsServiceImplTest {

    /** 假装订单服务Feign客户端，不真的远程调用 */
    @Mock
    private OrderFeignClient orderFeignClient;

    /** 被测试的统计服务，Mockito 会自动把上面这个 Mock 注入进来 */
    @InjectMocks
    private MerchantStatsServiceImpl merchantStatsService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long MERCHANT_ID = 1001L;                  // 商家ID
    private static final String START_DATE = "2026-01-01";          // 查询开始日期
    private static final String END_DATE = "2026-01-31";            // 查询结束日期
    private static final int TREND_DAYS = 7;                        // 趋势统计天数
    private static final int RANK_LIMIT = 5;                        // 排行榜返回条数

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造工作台统计数据
     * 小白理解：今天卖了1234.56元、下了10单、有3单等着发货
     *
     * @return 构造好的MerchantDashboardStatsVO
     */
    private MerchantDashboardStatsVO buildDashboardStats() {
        MerchantDashboardStatsVO vo = new MerchantDashboardStatsVO();
        vo.setTodaySales(new BigDecimal("123456"));
        vo.setTodayOrders(10L);
        vo.setPendingShip(3L);
        return vo;
    }

    /**
     * 构造一条销售趋势数据
     *
     * @param date   日期，格式 yyyy-MM-dd
     * @param amount 当日销售额（分）
     * @return 构造好的SalesTrendItemVO
     */
    private SalesTrendItemVO buildSalesTrendItem(String date, String amount) {
        SalesTrendItemVO item = new SalesTrendItemVO();
        item.setDate(date);
        item.setAmount(new BigDecimal(amount));
        return item;
    }

    /**
     * 构造数据中心概览数据
     * 小白理解：总销售额9999.99元、总订单100单、客单价99.99元、退款率3.2%
     *
     * @return 构造好的MerchantDataOverviewVO
     */
    private MerchantDataOverviewVO buildDataOverview() {
        MerchantDataOverviewVO vo = new MerchantDataOverviewVO();
        vo.setTotalSales(new BigDecimal("999999"));
        vo.setTotalOrders(100L);
        vo.setAvgOrderAmount(new BigDecimal("9999"));
        vo.setRefundRate(new BigDecimal("3.2"));
        return vo;
    }

    /**
     * 构造一条商品排行数据
     *
     * @param name  商品名称
     * @param count 销量
     * @param amount 销售额（分）
     * @return 构造好的ProductRankItemVO
     */
    private ProductRankItemVO buildProductRankItem(String name, int count, String amount) {
        ProductRankItemVO item = new ProductRankItemVO();
        item.setProductName(name);
        item.setSalesCount(count);
        item.setSalesAmount(new BigDecimal(amount));
        return item;
    }

    // ==================== 1. getDashboardStats 获取工作台统计 ====================

    @Nested
    @DisplayName("getDashboardStats 获取工作台统计")
    class GetDashboardStatsTest {

        @Test
        @DisplayName("Feign返回null → 返回null（前端显示暂无数据）")
        void getDashboardStats_resultNull_returnsNull() {
            // 场景：订单服务返回null（极端情况，比如序列化失败）
            when(orderFeignClient.getDashboardStats(MERCHANT_ID)).thenReturn(null);

            // 执行查询
            MerchantDashboardStatsVO result = merchantStatsService.getDashboardStats(MERCHANT_ID);

            // 验证：返回null，让前端显示"暂无数据"
            assertThat(result).isNull();
            // 验证：确实调用了Feign
            verify(orderFeignClient).getDashboardStats(MERCHANT_ID);
        }

        @Test
        @DisplayName("Feign返回失败 → 返回null（前端显示暂无数据）")
        void getDashboardStats_resultFail_returnsNull() {
            // 场景：订单服务返回失败（比如服务降级了）
            when(orderFeignClient.getDashboardStats(MERCHANT_ID))
                    .thenReturn(Result.fail("统计服务暂不可用"));

            // 执行查询
            MerchantDashboardStatsVO result = merchantStatsService.getDashboardStats(MERCHANT_ID);

            // 验证：失败时也返回null
            assertThat(result).isNull();
            verify(orderFeignClient).getDashboardStats(MERCHANT_ID);
        }

        @Test
        @DisplayName("Feign返回成功 → 返回工作台统计数据")
        void getDashboardStats_success_returnsData() {
            // 场景：订单服务正常返回统计数据
            MerchantDashboardStatsVO stats = buildDashboardStats();
            when(orderFeignClient.getDashboardStats(MERCHANT_ID)).thenReturn(Result.success(stats));

            // 执行查询
            MerchantDashboardStatsVO result = merchantStatsService.getDashboardStats(MERCHANT_ID);

            // 验证：返回了正确的统计数据
            assertThat(result).isNotNull();
            assertThat(result.getTodaySales()).isEqualByComparingTo("123456");
            assertThat(result.getTodayOrders()).isEqualTo(10L);
            assertThat(result.getPendingShip()).isEqualTo(3L);
            verify(orderFeignClient).getDashboardStats(MERCHANT_ID);
        }
    }

    // ==================== 2. getSalesTrend 获取销售趋势 ====================

    @Nested
    @DisplayName("getSalesTrend 获取销售趋势")
    class GetSalesTrendTest {

        @Test
        @DisplayName("Feign返回null → 返回空列表（前端图表显示空白）")
        void getSalesTrend_resultNull_returnsEmptyList() {
            // 场景：订单服务返回null
            when(orderFeignClient.getSalesTrend(MERCHANT_ID, TREND_DAYS)).thenReturn(null);

            // 执行查询
            List<SalesTrendItemVO> result = merchantStatsService.getSalesTrend(MERCHANT_ID, TREND_DAYS);

            // 验证：返回空列表，前端图表就显示空白
            assertThat(result).isEmpty();
            verify(orderFeignClient).getSalesTrend(MERCHANT_ID, TREND_DAYS);
        }

        @Test
        @DisplayName("Feign返回失败 → 返回空列表")
        void getSalesTrend_resultFail_returnsEmptyList() {
            // 场景：订单服务返回失败
            when(orderFeignClient.getSalesTrend(MERCHANT_ID, TREND_DAYS))
                    .thenReturn(Result.fail("统计服务暂不可用"));

            // 执行查询
            List<SalesTrendItemVO> result = merchantStatsService.getSalesTrend(MERCHANT_ID, TREND_DAYS);

            // 验证：失败时也返回空列表
            assertThat(result).isEmpty();
            verify(orderFeignClient).getSalesTrend(MERCHANT_ID, TREND_DAYS);
        }

        @Test
        @DisplayName("Feign返回成功 → 返回销售趋势列表")
        void getSalesTrend_success_returnsData() {
            // 场景：订单服务正常返回最近7天的销售趋势
            List<SalesTrendItemVO> trend = List.of(
                    buildSalesTrendItem("2026-01-01", "10000"),
                    buildSalesTrendItem("2026-01-02", "20000")
            );
            when(orderFeignClient.getSalesTrend(MERCHANT_ID, TREND_DAYS)).thenReturn(Result.success(trend));

            // 执行查询
            List<SalesTrendItemVO> result = merchantStatsService.getSalesTrend(MERCHANT_ID, TREND_DAYS);

            // 验证：返回了2条趋势数据，日期和金额都对得上
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDate()).isEqualTo("2026-01-01");
            assertThat(result.get(0).getAmount()).isEqualByComparingTo("10000");
            assertThat(result.get(1).getDate()).isEqualTo("2026-01-02");
            assertThat(result.get(1).getAmount()).isEqualByComparingTo("20000");
            verify(orderFeignClient).getSalesTrend(MERCHANT_ID, TREND_DAYS);
        }
    }

    // ==================== 3. getDataOverview 获取数据中心概览 ====================

    @Nested
    @DisplayName("getDataOverview 获取数据中心概览")
    class GetDataOverviewTest {

        @Test
        @DisplayName("Feign返回null → 返回null")
        void getDataOverview_resultNull_returnsNull() {
            // 场景：订单服务返回null
            when(orderFeignClient.getDataOverview(MERCHANT_ID, START_DATE, END_DATE)).thenReturn(null);

            // 执行查询
            MerchantDataOverviewVO result = merchantStatsService.getDataOverview(MERCHANT_ID, START_DATE, END_DATE);

            // 验证：返回null
            assertThat(result).isNull();
            verify(orderFeignClient).getDataOverview(MERCHANT_ID, START_DATE, END_DATE);
        }

        @Test
        @DisplayName("Feign返回失败 → 返回null")
        void getDataOverview_resultFail_returnsNull() {
            // 场景：订单服务返回失败
            when(orderFeignClient.getDataOverview(MERCHANT_ID, START_DATE, END_DATE))
                    .thenReturn(Result.fail("统计服务暂不可用"));

            // 执行查询
            MerchantDataOverviewVO result = merchantStatsService.getDataOverview(MERCHANT_ID, START_DATE, END_DATE);

            // 验证：失败时也返回null
            assertThat(result).isNull();
            verify(orderFeignClient).getDataOverview(MERCHANT_ID, START_DATE, END_DATE);
        }

        @Test
        @DisplayName("Feign返回成功 → 返回数据中心概览")
        void getDataOverview_success_returnsData() {
            // 场景：订单服务正常返回数据中心概览
            MerchantDataOverviewVO overview = buildDataOverview();
            when(orderFeignClient.getDataOverview(MERCHANT_ID, START_DATE, END_DATE))
                    .thenReturn(Result.success(overview));

            // 执行查询
            MerchantDataOverviewVO result = merchantStatsService.getDataOverview(MERCHANT_ID, START_DATE, END_DATE);

            // 验证：返回了正确的概览数据
            assertThat(result).isNotNull();
            assertThat(result.getTotalSales()).isEqualByComparingTo("999999");
            assertThat(result.getTotalOrders()).isEqualTo(100L);
            assertThat(result.getAvgOrderAmount()).isEqualByComparingTo("9999");
            assertThat(result.getRefundRate()).isEqualByComparingTo("3.2");
            verify(orderFeignClient).getDataOverview(MERCHANT_ID, START_DATE, END_DATE);
        }
    }

    // ==================== 4. getProductRank 获取商品销量排行 ====================

    @Nested
    @DisplayName("getProductRank 获取商品销量排行")
    class GetProductRankTest {

        @Test
        @DisplayName("Feign返回null → 返回空列表")
        void getProductRank_resultNull_returnsEmptyList() {
            // 场景：订单服务返回null
            when(orderFeignClient.getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT)).thenReturn(null);

            // 执行查询
            List<ProductRankItemVO> result = merchantStatsService.getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT);

            // 验证：返回空列表
            assertThat(result).isEmpty();
            verify(orderFeignClient).getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT);
        }

        @Test
        @DisplayName("Feign返回失败 → 返回空列表")
        void getProductRank_resultFail_returnsEmptyList() {
            // 场景：订单服务返回失败
            when(orderFeignClient.getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT))
                    .thenReturn(Result.fail("统计服务暂不可用"));

            // 执行查询
            List<ProductRankItemVO> result = merchantStatsService.getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT);

            // 验证：失败时也返回空列表
            assertThat(result).isEmpty();
            verify(orderFeignClient).getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT);
        }

        @Test
        @DisplayName("Feign返回成功 → 返回商品销量排行列表")
        void getProductRank_success_returnsData() {
            // 场景：订单服务正常返回商品销量Top榜
            List<ProductRankItemVO> rank = List.of(
                    buildProductRankItem("商品A", 100, "50000"),
                    buildProductRankItem("商品B", 80, "40000")
            );
            when(orderFeignClient.getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT))
                    .thenReturn(Result.success(rank));

            // 执行查询
            List<ProductRankItemVO> result = merchantStatsService.getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT);

            // 验证：返回了2条排行数据，按销量降序排列
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getProductName()).isEqualTo("商品A");
            assertThat(result.get(0).getSalesCount()).isEqualTo(100);
            assertThat(result.get(0).getSalesAmount()).isEqualByComparingTo("50000");
            assertThat(result.get(1).getProductName()).isEqualTo("商品B");
            assertThat(result.get(1).getSalesCount()).isEqualTo(80);
            verify(orderFeignClient).getProductRank(MERCHANT_ID, START_DATE, END_DATE, RANK_LIMIT);
        }
    }
}
