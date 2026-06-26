package com.shop.model.order.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商家数据中心概览VO
 * <p>用于商家端数据中心页面展示指定时间段的统计</p>
 */
@Data
public class MerchantDataOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 总销售额（分） */
    private BigDecimal totalSales;

    /** 总订单数 */
    private Long totalOrders;

    /** 客单价（分） */
    private BigDecimal avgOrderAmount;

    /** 退款率（百分比，如 3.2 表示 3.2%） */
    private BigDecimal refundRate;
}
