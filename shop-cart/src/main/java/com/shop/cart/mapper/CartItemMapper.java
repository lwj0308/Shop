package com.shop.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车项Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作cart_item表。
 * </p>
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
