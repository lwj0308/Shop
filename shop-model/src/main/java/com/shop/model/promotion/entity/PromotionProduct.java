package com.shop.model.promotion.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 满减活动商品关联实体
 * <p>
 * 对应数据库 promotion_product 表。
 * 仅当满减活动的 scope_type=2（指定商品）时才有数据，记录哪些商品参与了该活动。
 * </p>
 * <p>
 * sku_id 为 null 表示该商品的所有 SKU 都参与；指定 sku_id 表示只有该 SKU 参与。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("promotion_product")
public class PromotionProduct extends BaseEntity {

    /** 满减活动ID（关联 promotion 表的 id） */
    private Long promotionId;

    /** 商品ID（SPU） */
    private Long productId;

    /** SKU ID（null表示该商品所有SKU参与） */
    private Long skuId;
}
