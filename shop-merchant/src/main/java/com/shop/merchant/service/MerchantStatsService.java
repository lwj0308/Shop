package com.shop.merchant.service;

import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;

import java.util.List;

/**
 * 商家统计服务接口
 * <p>
 * 定义商家经营数据统计相关的方法。
 * 商家服务本身不存储订单数据，通过Feign调用订单服务获取统计数据，
 * 在这里做一层聚合封装，方便Controller调用。
 * </p>
 */
public interface MerchantStatsService {

    /**
     * 获取商家工作台统计数据
     * <p>
     * 商家登录后在工作台首页看到的今日数据概览，包括今日销售额、今日订单数、待发货订单数。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 工作台统计数据，调用失败返回null
     */
    MerchantDashboardStatsVO getDashboardStats(Long merchantId);

    /**
     * 获取销售趋势
     * <p>
     * 返回最近N天每天的销售额，用于工作台画销售趋势折线图。
     * </p>
     *
     * @param merchantId 商家ID
     * @param days       统计天数
     * @return 销售趋势列表，调用失败返回空列表
     */
    List<SalesTrendItemVO> getSalesTrend(Long merchantId, int days);

    /**
     * 获取数据中心概览
     * <p>
     * 商家在数据中心页面查看指定时间段的整体经营数据，包括总销售额、总订单数、客单价、退款率。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @return 数据中心概览，调用失败返回null
     */
    MerchantDataOverviewVO getDataOverview(Long merchantId, String startDate, String endDate);

    /**
     * 获取商品销量排行
     * <p>
     * 商家查看指定时间段内销量Top的商品，了解哪些商品卖得好。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @param limit      返回条数
     * @return 商品销量排行列表，调用失败返回空列表
     */
    List<ProductRankItemVO> getProductRank(Long merchantId, String startDate, String endDate, int limit);
}
