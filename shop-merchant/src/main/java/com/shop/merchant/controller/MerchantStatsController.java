package com.shop.merchant.controller;

import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.service.MerchantService;
import com.shop.merchant.service.MerchantStatsService;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家统计控制器
 * <p>
 * 提供商家经营数据统计接口，包括工作台概览、销售趋势、数据中心概览、商品销量排行。
 * 所有接口都需要登录，通过SecurityUtils获取当前登录用户ID，再查出对应的商家ID，
 * 商家只能查看自己的统计数据，不能查看别人的。
 * </p>
 */
@Tag(name = "商家统计", description = "商家经营数据统计接口")
@RestController
@RequestMapping("/merchant/stats")
@RequiredArgsConstructor
public class MerchantStatsController {

    /** 商家统计服务，聚合订单服务的统计数据 */
    private final MerchantStatsService merchantStatsService;

    /** 商家服务，用于通过用户ID查找商家 */
    private final MerchantService merchantService;

    /**
     * 获取商家工作台统计数据
     * <p>
     * 商家登录后在工作台首页看到的今日数据概览，包括今日销售额、今日订单数、待发货订单数。
     * </p>
     *
     * @return 工作台统计数据
     */
    @Operation(summary = "获取工作台统计", description = "商家查看今日销售额、订单数、待发货数")
    @GetMapping("/dashboard")
    public Result<MerchantDashboardStatsVO> getDashboardStats() {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        MerchantDashboardStatsVO stats = merchantStatsService.getDashboardStats(merchant.getId());
        return Result.success(stats);
    }

    /**
     * 获取销售趋势
     * <p>
     * 返回最近N天每天的销售颂数据，用于工作台画销售趋势折线图。
     * </p>
     *
     * @param days 统计天数，默认7天
     * @return 销售趋势列表
     */
    @Operation(summary = "获取销售趋势", description = "商家查看最近N天的销售额趋势，用于画折线图")
    @GetMapping("/sales-trend")
    public Result<List<SalesTrendItemVO>> getSalesTrend(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        List<SalesTrendItemVO> trend = merchantStatsService.getSalesTrend(merchant.getId(), days);
        return Result.success(trend);
    }

    /**
     * 获取数据中心概览
     * <p>
     * 商家在数据中心页面查看指定时间段的整体经营数据，包括总销售额、总订单数、客单价、退款率。
     * </p>
     *
     * @param startDate 开始日期，格式 yyyy-MM-dd
     * @param endDate   结束日期，格式 yyyy-MM-dd
     * @return 数据中心概览
     */
    @Operation(summary = "获取数据中心概览", description = "商家查看指定时间段的总销售额、订单数、客单价、退款率")
    @GetMapping("/overview")
    public Result<MerchantDataOverviewVO> getDataOverview(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        MerchantDataOverviewVO overview = merchantStatsService.getDataOverview(merchant.getId(), startDate, endDate);
        return Result.success(overview);
    }

    /**
     * 获取商品销量排行
     * <p>
     * 商家查看指定时间段内销量Top的商品，了解哪些商品卖得好。
     * </p>
     *
     * @param startDate 开始日期，格式 yyyy-MM-dd
     * @param endDate   结束日期，格式 yyyy-MM-dd
     * @param limit     返回条数，默认5条
     * @return 商品销量排行列表
     */
    @Operation(summary = "获取商品销量排行", description = "商家查看指定时间段内销量Top的商品")
    @GetMapping("/product-rank")
    public Result<List<ProductRankItemVO>> getProductRank(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        List<ProductRankItemVO> rank = merchantStatsService.getProductRank(merchant.getId(), startDate, endDate, limit);
        return Result.success(rank);
    }
}
