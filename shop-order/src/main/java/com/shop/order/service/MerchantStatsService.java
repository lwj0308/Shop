package com.shop.order.service;

import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家统计服务
 * <p>
 * 提供商家仪表盘和数据中心所需的统计数据查询。
 * 这些数据供 shop-merchant 服务通过 Feign 调用，展示在商家端的工作台和数据中心页面。
 * </p>
 */
public interface MerchantStatsService {

    /**
     * 获取商家仪表盘统计（今日销售额、今日订单数、待发货数）
     * <p>商家打开工作台第一眼看到的数据概览</p>
     *
     * @param merchantId 商家ID
     * @return 仪表盘统计数据
     */
    MerchantDashboardStatsVO getDashboardStats(Long merchantId);

    /**
     * 获取商家销售趋势（最近N天每日销售额）
     * <p>用于画折线图，让商家直观看到最近生意走势</p>
     *
     * @param merchantId 商家ID
     * @param days       最近几天
     * @return 每日销售额列表
     */
    List<SalesTrendItemVO> getSalesTrend(Long merchantId, int days);

    /**
     * 获取商家数据中心概览（指定时间范围内的统计）
     * <p>商家在数据中心选择时间段后，展示总销售额、订单数、客单价、退款率</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @return 数据中心概览
     */
    MerchantDataOverviewVO getDataOverview(Long merchantId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 获取商家商品销量排行
     * <p>展示销量Top商品，让商家知道哪些商品卖得最好</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @param limit      取前几名
     * @return 商品销量排行列表
     */
    List<ProductRankItemVO> getProductRank(Long merchantId, LocalDateTime startDate, LocalDateTime endDate, int limit);
}
