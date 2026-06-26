package com.shop.merchant.service.impl;

import com.shop.common.result.Result;
import com.shop.merchant.feign.OrderFeignClient;
import com.shop.merchant.service.MerchantStatsService;
import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 商家统计服务实现类
 * <p>
 * 通过Feign远程调用订单服务获取统计数据，做一层聚合封装。
 * 统计是辅助功能，调用失败时返回null或空列表，不影响商家主流程。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantStatsServiceImpl implements MerchantStatsService {

    /** 订单服务Feign客户端，远程调用订单服务的统计接口 */
    private final OrderFeignClient orderFeignClient;

    /**
     * 获取商家工作台统计数据
     * <p>
     * 调用订单服务获取今日销售额、今日订单数、待发货订单数。
     * 调用失败（服务降级或返回非200）时返回null，前端显示暂无数据即可。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 工作台统计数据，调用失败返回null
     */
    @Override
    public MerchantDashboardStatsVO getDashboardStats(Long merchantId) {
        Result<MerchantDashboardStatsVO> result = orderFeignClient.getDashboardStats(merchantId);
        if (result == null || !result.isSuccess()) {
            log.warn("获取工作台统计失败: merchantId={}, result={}", merchantId, result);
            return null;
        }
        return result.getData();
    }

    /**
     * 获取销售趋势
     * <p>
     * 调用订单服务获取最近N天每天的销售颂数据，用于画趋势图。
     * 调用失败时返回空列表，前端图表显示空白即可。
     * </p>
     *
     * @param merchantId 商家ID
     * @param days       统计天数
     * @return 销售趋势列表，调用失败返回空列表
     */
    @Override
    public List<SalesTrendItemVO> getSalesTrend(Long merchantId, int days) {
        Result<List<SalesTrendItemVO>> result = orderFeignClient.getSalesTrend(merchantId, days);
        if (result == null || !result.isSuccess()) {
            log.warn("获取销售趋势失败: merchantId={}, days={}, result={}", merchantId, days, result);
            return Collections.emptyList();
        }
        return result.getData();
    }

    /**
     * 获取数据中心概览
     * <p>
     * 调用订单服务获取指定时间段的整体经营数据，包括总销售额、总订单数、客单价、退款率。
     * 调用失败时返回null，前端显示暂无数据即可。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @return 数据中心概览，调用失败返回null
     */
    @Override
    public MerchantDataOverviewVO getDataOverview(Long merchantId, String startDate, String endDate) {
        Result<MerchantDataOverviewVO> result = orderFeignClient.getDataOverview(merchantId, startDate, endDate);
        if (result == null || !result.isSuccess()) {
            log.warn("获取数据中心概览失败: merchantId={}, startDate={}, endDate={}, result={}", merchantId, startDate, endDate, result);
            return null;
        }
        return result.getData();
    }

    /**
     * 获取商品销量排行
     * <p>
     * 调用订单服务获取指定时间段内销量Top的商品列表。
     * 调用失败时返回空列表，前端显示暂无数据即可。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @param limit      返回条数
     * @return 商品销量排行列表，调用失败返回空列表
     */
    @Override
    public List<ProductRankItemVO> getProductRank(Long merchantId, String startDate, String endDate, int limit) {
        Result<List<ProductRankItemVO>> result = orderFeignClient.getProductRank(merchantId, startDate, endDate, limit);
        if (result == null || !result.isSuccess()) {
            log.warn("获取商品销量排行失败: merchantId={}, startDate={}, endDate={}, limit={}, result={}", merchantId, startDate, endDate, limit, result);
            return Collections.emptyList();
        }
        return result.getData();
    }
}
