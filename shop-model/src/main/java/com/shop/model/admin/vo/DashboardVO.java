package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 仪表盘数据响应
 * <p>
 * 管理后台首页仪表盘展示的核心数据，让管理员一眼就能看到今天的经营概况。
 * 包括今日订单数、今日销售额、今日新增用户数、在线商家数。
 * </p>
 */
@Data
public class DashboardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 今日订单数量 */
    private Long todayOrderCount;

    /** 今日销售金额 */
    private BigDecimal todaySalesAmount;

    /** 今日新增用户数量 */
    private Long todayNewUserCount;

    /** 在线商家数量 */
    private Long onlineMerchantCount;
}
