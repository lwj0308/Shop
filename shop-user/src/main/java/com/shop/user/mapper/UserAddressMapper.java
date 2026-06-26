package com.shop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作user_address表。
 * </p>
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}
