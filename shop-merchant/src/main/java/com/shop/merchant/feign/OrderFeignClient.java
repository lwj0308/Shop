package com.shop.merchant.feign;

import com.shop.common.result.Result;
import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 订单服务Feign客户端（商家统计专用）
 * <p>
 * 商家服务通过Feign远程调用订单服务的统计接口，获取商家自己的经营数据。
 * 包括：工作台概览、销售趋势、数据中心概览、商品销量排行。
 * </p>
 * <p>
 * 使用fallbackFactory实现降级：当订单服务不可用时，走降级逻辑返回友好提示，
 * 统计是辅助功能，不能因为统计服务挂了影响商家主流程。
 * </p>
 */
@FeignClient(
        name = "shop-order",
        contextId = "merchantOrder",
        path = "/order/merchant/stats",
        fallbackFactory = OrderFeignClientFallbackFactory.class
)
public interface OrderFeignClient {

    /**
     * 获取商家工作台统计数据
     * <p>
     * 商家登录后在工作台首页看到的今日数据概览，包括今日销售额、今日订单数、待发货订单数。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 工作台统计数据
     */
    @GetMapping("/dashboard")
    Result<MerchantDashboardStatsVO> getDashboardStats(@RequestParam("merchantId") Long merchantId);

    /**
     * 获取销售趋势
     * <p>
     * 商家在工作台看到的销售趋势图数据，返回最近N天每天的销售额，用于画折线图。
     * </p>
     *
     * @param merchantId 商家ID
     * @param days       统计天数，默认7天
     * @return 销售趋势列表，每项包含日期和当日销售额
     */
    @GetMapping("/sales-trend")
    Result<List<SalesTrendItemVO>> getSalesTrend(
            @RequestParam("merchantId") Long merchantId,
            @RequestParam(value = "days", defaultValue = "7") int days);

    /**
     * 获取数据中心概览
     * <p>
     * 商家在数据中心页面查看指定时间段的整体经营数据，包括总销售额、总订单数、客单价、退款率。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @return 数据中心概览
     */
    @GetMapping("/overview")
    Result<MerchantDataOverviewVO> getDataOverview(
            @RequestParam("merchantId") Long merchantId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate);

    /**
     * 获取商品销量排行
     * <p>
     * 商家查看指定时间段内销量Top的商品，了解哪些商品卖得好。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @param limit      返回条数，默认5条
     * @return 商品销量排行列表
     */
    @GetMapping("/product-rank")
    Result<List<ProductRankItemVO>> getProductRank(
            @RequestParam("merchantId") Long merchantId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "limit", defaultValue = "5") int limit);
}
