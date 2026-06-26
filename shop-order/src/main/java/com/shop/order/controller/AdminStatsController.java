package com.shop.order.controller;

import com.shop.common.result.Result;
import com.shop.model.order.vo.AdminTodayStatsVO;
import com.shop.order.service.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端统计控制器
 * <p>
 * 提供管理后台仪表盘所需的统计接口。
 * 这些接口是内部接口，供 shop-admin 通过 Feign 调用。
 * 路径以 /order/admin/stats 开头，在 SaTokenConfig 中排除鉴权。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/order/admin/stats")
@RequiredArgsConstructor
@Tag(name = "管理端统计", description = "管理后台仪表盘的统计数据查询")
public class AdminStatsController {

    /** 管理端统计服务 */
    private final AdminStatsService adminStatsService;

    /**
     * 获取全平台今日统计
     * <p>管理后台仪表盘第一眼看到的数据：今日订单数、今日销售额</p>
     *
     * @return 今日统计数据
     */
    @GetMapping("/today")
    @Operation(summary = "全平台今日统计", description = "返回今日订单数和今日销售额")
    public Result<AdminTodayStatsVO> getTodayStats() {
        AdminTodayStatsVO stats = adminStatsService.getTodayStats();
        return Result.success(stats);
    }
}
