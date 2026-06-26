package com.shop.model.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单状态日志实体
 * <p>
 * 对应数据库 order_log 表，记录订单状态的每一次变化。
 * 比如"待付款→待发货"、"待发货→运输中"等，每次状态变化都记一条日志。
 * 这样就能追溯订单的完整生命周期，出了问题也方便排查。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_log")
public class OrderLog extends BaseEntity {

    /** 订单ID（这条日志属于哪个订单） */
    private Long orderId;

    /** 订单号（方便查询） */
    private String orderNo;

    /** 操作前的状态（变化之前是什么状态） */
    private Integer fromStatus;

    /** 操作后的状态（变化之后变成了什么状态） */
    private Integer toStatus;

    /** 操作类型（比如"支付"、"发货"、"取消"等） */
    private String action;

    /** 操作人ID（谁做的这个操作，可能是用户、商家或系统） */
    private Long operatorId;

    /** 操作人类型：1用户 2商家 3系统 */
    private Integer operatorType;

    /** 备注说明（额外的说明信息） */
    private String note;
}
