package com.shop.model.promotion.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 满减活动实体
 * <p>
 * 对应数据库 promotion 表，存储商家或平台创建的满减活动。
 * 满减活动规则简单：订单金额满 threshold 元，减 discountAmount 元。
 * </p>
 * <p>
 * merchant_id=0 表示平台活动（管理员创建），>0 表示商家活动。
 * scope_type=1 全店满减（所有商品参与），scope_type=2 指定商品满减（仅关联商品参与）。
 * </p>
 * <p>
 * 叠加规则：满减和优惠券可叠加。下单时先算满减，再用满减后金额作为优惠券门槛判断依据。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("promotion")
public class Promotion extends BaseEntity {

    /** 商家ID（0表示平台活动，>0表示对应商家创建的活动） */
    private Long merchantId;

    /** 活动名称（如"夏季满200减20"） */
    private String name;

    /** 满减门槛金额（满多少元，如 200.00） */
    private BigDecimal threshold;

    /** 优惠金额（减多少元，如 20.00） */
    private BigDecimal discountAmount;

    /** 参与范围：1全店 2指定商品（对应 PromotionScopeTypeEnum） */
    private Integer scopeType;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 状态：0待生效 1进行中 2已结束 3已下架（对应 PromotionStatusEnum） */
    private Integer status;

    /** 活动描述 */
    private String description;
}
