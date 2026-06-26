package com.shop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.payment.entity.PaymentCallback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付回调日志Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作payment_callback表。
 * </p>
 */
@Mapper
public interface PaymentCallbackMapper extends BaseMapper<PaymentCallback> {
}
