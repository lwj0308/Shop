package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.product.entity.ProductSpecValue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规格值Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作product_spec_value表。
 * </p>
 */
@Mapper
public interface ProductSpecValueMapper extends BaseMapper<ProductSpecValue> {
}
