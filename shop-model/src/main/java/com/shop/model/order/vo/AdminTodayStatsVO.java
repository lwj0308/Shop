package com.shop.model.order.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 管理端今日统计VO
 * <p>
 * 给管理后台仪表盘用的，统计全平台今天的订单数据。
 * 和商家端的 MerchantDashboardStatsVO 区分开，因为管理端看的是全平台数据，
 * 而且不需要 pendingShip（待发货）这个字段。
 * </p>
 */
@Data
public class AdminTodayStatsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 今日全平台订单数 */
    private Long todayOrderCount;

    /** 今日全平台销售额（分） */
    private BigDecimal todaySalesAmount;
}
