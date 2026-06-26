package com.shop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.coupon.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户优惠券 Mapper
 * <p>
 * MyBatis-Plus 的 BaseMapper 已提供基本 CRUD，复杂查询在 Service 层用 LambdaQueryWrapper 实现。
 * </p>
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
}
