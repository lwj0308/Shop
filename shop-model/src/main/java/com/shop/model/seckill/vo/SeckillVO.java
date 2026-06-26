package com.shop.model.seckill.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动响应VO
 * <p>
 * 返回给前端的秒杀活动信息。
 * status 字段额外提供 desc 描述，前端可以直接展示中文。
 * progress 字段表示秒杀进度（已售/总数），前端展示进度条用。
 * </p>
 */
@Data
public class SeckillVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 秒杀活动ID */
    private Long id;

    /** 商家ID（0表示平台活动） */
    private Long merchantId;

    /** 商品ID */
    private Long productId;

    /** SKU ID */
    private Long skuId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 秒杀库存总数 */
    private Integer totalCount;

    /** 剩余库存 */
    private Integer availableCount;

    /** 每人限购数量 */
    private Integer limitCount;

    /** 秒杀开始时间 */
    private LocalDateTime startTime;

    /** 秒杀结束时间 */
    private LocalDateTime endTime;

    /** 状态：0待生效 1进行中 2已结束 3已下架 */
    private Integer status;

    /** 状态描述（如"进行中"、"已结束"） */
    private String statusDesc;

    /** 活动描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 秒杀进度百分比（已售/总数 * 100）
     * 前端展示进度条用，如 75 表示已售 75%
     */
    private Integer progress;
}
