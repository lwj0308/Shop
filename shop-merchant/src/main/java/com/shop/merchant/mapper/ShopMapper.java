package com.shop.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.merchant.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 店铺信息Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写SQL就能操作shop表。
 * </p>
 */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
}
