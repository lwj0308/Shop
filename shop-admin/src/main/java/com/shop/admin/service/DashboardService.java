package com.shop.admin.service;

import com.shop.model.admin.vo.DashboardVO;

/**
 * 仪表盘服务接口
 * <p>
 * 聚合多个微服务的数据，给管理后台首页仪表盘提供数据概览。
 * 包括今日订单数、今日销售额、今日新增用户数、在线商家数。
 * </p>
 */
public interface DashboardService {

    /**
     * 获取仪表盘概览数据
     * <p>
     * 从多个微服务聚合数据：
     * - todayNewUserCount：调用用户服务获取今日新增用户数
     * - onlineMerchantCount：调用商家服务获取在线商家数
     * - todayOrderCount/todaySalesAmount：调用订单服务获取今日订单统计
     * </p>
     *
     * @return 仪表盘数据
     */
    DashboardVO getDashboardOverview();
}
