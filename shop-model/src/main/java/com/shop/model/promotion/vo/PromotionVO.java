package com.shop.model.promotion.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 满减活动响应VO
 * <p>
 * 返回给前端的满减活动信息。
 * status 和 scopeType 字段额外提供 desc 描述，前端可以直接展示中文，不用再维护映射表。
 * </p>
 */
@Data
public class PromotionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 满减活动ID */
    private Long id;

    /** 商家ID（0表示平台活动） */
    private Long merchantId;

    /** 活动名称 */
    private String name;

    /** 满减门槛金额 */
    private BigDecimal threshold;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 参与范围：1全店 2指定商品 */
    private Integer scopeType;

    /** 参与范围描述（如"全店"、"指定商品"，由后端翻译） */
    private String scopeTypeDesc;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 状态：0待生效 1进行中 2已结束 3已下架 */
    private Integer status;

    /** 状态描述（如"进行中"、"已结束"，由后端翻译） */
    private String statusDesc;

    /** 活动描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 参与商品ID列表
     * 仅当 scopeType=2（指定商品）时返回，scopeType=1（全店）时为 null
     * 编辑活动时回显用
     */
    private List<Long> productIds;
}
