package com.shop.model.order.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商家仪表盘统计VO
 * <p>用于商家端工作台展示今日数据概览</p>
 */
@Data
public class MerchantDashboardStatsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 今日销售额（分） */
    private BigDecimal todaySales;

    /** 今日订单数 */
    private Long todayOrders;

    /** 待发货订单数 */
    private Long pendingShip;
}
