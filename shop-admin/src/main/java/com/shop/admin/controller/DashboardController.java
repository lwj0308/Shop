package com.shop.admin.controller;

import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.DashboardService;
import com.shop.common.result.Result;
import com.shop.model.admin.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘控制器
 * <p>
 * 管理后台首页仪表盘数据概览接口，聚合多个微服务的数据，
 * 让管理员一眼就能看到今天的经营概况。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "仪表盘", description = "仪表盘数据概览接口")
@RequiredArgsConstructor
public class DashboardController {

    /** 仪表盘服务，聚合多个微服务的数据 */
    private final DashboardService dashboardService;

    /**
     * 获取仪表盘概览数据
     * <p>
     * 返回今日订单数、今日销售额、今日新增用户数、在线商家数等核心指标。
     * 数据从多个微服务聚合而来，如果某个服务不可用，对应指标会显示0。
     * </p>
     *
     * @return 仪表盘数据
     */
    @RequirePermission("admin:dashboard:view")
    @Operation(summary = "获取仪表盘概览", description = "获取今日订单数、销售额、新增用户数、在线商家数")
    @GetMapping("/overview")
    public Result<DashboardVO> getOverview() {
        DashboardVO vo = dashboardService.getDashboardOverview();
        return Result.success(vo);
    }
}
