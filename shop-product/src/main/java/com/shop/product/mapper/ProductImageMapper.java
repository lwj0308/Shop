package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.product.entity.ProductImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品图片Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作product_image表。
 * </p>
 */
@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImage> {
}
