package com.shop.model.seckill.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建秒杀活动请求DTO
 * <p>
 * 商家或管理员创建秒杀活动时提交的参数。
 * 需要指定秒杀的 SKU、秒杀价、库存、限购数量和时间窗口。
 * </p>
 */
@Data
public class SeckillCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID（SPU） */
    private Long productId;

    /** SKU ID（秒杀到规格级别） */
    private Long skuId;

    /** 秒杀价（必须 > 0 且 < 原价） */
    private BigDecimal seckillPrice;

    /** 原价（冗余展示用，前端从商品信息获取后传入） */
    private BigDecimal originalPrice;

    /** 秒杀库存总数（必须 > 0） */
    private Integer totalCount;

    /** 每人限购数量（默认1，必须 > 0） */
    private Integer limitCount;

    /** 秒杀开始时间 */
    private LocalDateTime startTime;

    /** 秒杀结束时间（必须 > 开始时间） */
    private LocalDateTime endTime;

    /** 活动描述 */
    private String description;
}
