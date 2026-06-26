package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.product.entity.Brand;
import org.apache.ibatis.annotations.Mapper;

/**
 * 品牌Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作brand表。
 * </p>
 */
@Mapper
public interface BrandMapper extends BaseMapper<Brand> {
}
