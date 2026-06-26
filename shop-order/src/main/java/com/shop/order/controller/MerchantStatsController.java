package com.shop.order.controller;

import com.shop.common.result.Result;
import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import com.shop.order.service.MerchantStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家统计控制器
 * <p>
 * 提供商家仪表盘和数据中心所需的统计接口。
 * 这些接口是内部接口，供 shop-merchant 通过 Feign 调用。
 * shop-merchant 负责鉴权并获取 merchantId，然后传过来调用。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/order/merchant/stats")
@RequiredArgsConstructor
@Tag(name = "商家统计", description = "商家仪表盘和数据中心的统计数据查询")
public class MerchantStatsController {

    /** 商家统计服务 */
    private final MerchantStatsService merchantStatsService;

    /**
     * 获取商家仪表盘统计
     * <p>商家打开工作台第一眼看到的数据：今日销售额、今日订单数、待发货数</p>
     *
     * @param merchantId 商家ID
     * @return 仪表盘统计数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "商家仪表盘统计", description = "返回今日销售额、今日订单数、待发货订单数")
    public Result<MerchantDashboardStatsVO> getDashboardStats(@RequestParam Long merchantId) {
        MerchantDashboardStatsVO stats = merchantStatsService.getDashboardStats(merchantId);
        return Result.success(stats);
    }

    /**
     * 获取商家销售趋势
     * <p>返回最近N天的每日销售额，用于画折线图</p>
     *
     * @param merchantId 商家ID
     * @param days       最近几天，默认7天
     * @return 每日销售额列表
     */
    @GetMapping("/sales-trend")
    @Operation(summary = "商家销售趋势", description = "返回最近N天的每日销售额，用于折线图展示")
    public Result<List<SalesTrendItemVO>> getSalesTrend(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "7") int days) {
        List<SalesTrendItemVO> trend = merchantStatsService.getSalesTrend(merchantId, days);
        return Result.success(trend);
    }

    /**
     * 获取商家数据中心概览
     * <p>返回指定时间范围内的总销售额、总订单数、客单价、退款率</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @return 数据中心概览
     */
    @GetMapping("/overview")
    @Operation(summary = "商家数据中心概览", description = "返回指定时间范围内的总销售额、订单数、客单价、退款率")
    public Result<MerchantDataOverviewVO> getDataOverview(
            @RequestParam Long merchantId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        // 把字符串日期转成 LocalDateTime：startDate 转为当天 00:00:00，endDate 转为当天 23:59:59
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
        MerchantDataOverviewVO overview = merchantStatsService.getDataOverview(merchantId, start, end);
        return Result.success(overview);
    }

    /**
     * 获取商家商品销量排行
     * <p>返回销量Top商品，让商家知道哪些商品卖得最好</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始日期，格式 yyyy-MM-dd
     * @param endDate    结束日期，格式 yyyy-MM-dd
     * @param limit      取前几名，默认5
     * @return 商品销量排行列表
     */
    @GetMapping("/product-rank")
    @Operation(summary = "商家商品销量排行", description = "返回指定时间范围内的销量Top商品")
    public Result<List<ProductRankItemVO>> getProductRank(
            @RequestParam Long merchantId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "5") int limit) {
        // 把字符串日期转成 LocalDateTime：startDate 转为当天 00:00:00，endDate 转为当天 23:59:59
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
        List<ProductRankItemVO> rank = merchantStatsService.getProductRank(merchantId, start, end, limit);
        return Result.success(rank);
    }
}
