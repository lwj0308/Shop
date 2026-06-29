package com.shop.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.promotion.entity.PromotionProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 满减活动商品关联 Mapper
 * <p>
 * 操作 promotion_product 表，记录指定商品满减活动（scopeType=2）关联的商品。
 * 全店满减活动（scopeType=1）不需要写入此表。
 * </p>
 */
@Mapper
public interface PromotionProductMapper extends BaseMapper<PromotionProduct> {
}
