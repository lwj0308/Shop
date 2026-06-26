package com.shop.model.promotion.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建满减活动请求DTO
 * <p>
 * 商家或管理员创建满减活动时提交的参数。
 * 商家创建时 merchantId 由后端从登录态获取，管理员创建时 merchantId=0（平台活动）。
 * </p>
 * <p>
 * 当 scopeType=2（指定商品）时，productIds 不能为空，需要指定参与活动的商品。
 * </p>
 */
@Data
public class PromotionCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    /** 活动描述 */
    private String description;

    /**
     * 参与商品ID列表
     * 仅当 scopeType=2（指定商品）时需要传，scopeType=1（全店）时忽略
     */
    private List<Long> productIds;
}
