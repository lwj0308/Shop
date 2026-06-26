package com.shop.model.order.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售趋势项VO
 * <p>用于图表展示每日销售额</p>
 */
@Data
public class SalesTrendItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 日期，格式 yyyy-MM-dd */
    private String date;

    /** 当日销售额（分） */
    private BigDecimal amount;
}
