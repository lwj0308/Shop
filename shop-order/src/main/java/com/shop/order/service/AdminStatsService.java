package com.shop.order.service;

import com.shop.model.order.vo.AdminTodayStatsVO;

/**
 * 管理端统计服务接口
 * <p>
 * 给管理后台仪表盘提供全平台统计数据。
 * 和 MerchantStatsService 区分开：MerchantStatsService 是按商家统计，
 * 这里是统计全平台所有数据。
 * </p>
 */
public interface AdminStatsService {

    /**
     * 获取全平台今日统计
     * <p>统计今天全平台的订单数和销售额，给管理后台仪表盘用</p>
     *
     * @return 今日统计数据
     */
    AdminTodayStatsVO getTodayStats();
}
