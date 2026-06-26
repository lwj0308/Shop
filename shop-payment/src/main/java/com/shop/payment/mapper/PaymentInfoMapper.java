package com.shop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.payment.entity.PaymentInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作payment_info表。
 * </p>
 */
@Mapper
public interface PaymentInfoMapper extends BaseMapper<PaymentInfo> {
}
