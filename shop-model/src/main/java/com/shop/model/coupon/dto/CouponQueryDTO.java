package com.shop.model.coupon.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 优惠券查询DTO
 * <p>
 * 查询优惠券列表时的筛选条件，支持按状态和类型筛选，支持分页。
 * 商家端查询时自动按 merchantId 过滤，管理端可查看所有（含平台券和商家券）。
 * </p>
 */
@Data
public class CouponQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 优惠券状态筛选（可选）：0待生效 1进行中 2已结束 3已下架 */
    private Integer status;

    /** 优惠券类型筛选（可选）：1满减 2折扣 3立减 */
    private Integer type;

    /** 页码（从1开始） */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
