package com.shop.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.merchant.entity.WithdrawOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提现申请Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力。
 * 操作 withdraw_order 表，记录商家的提现申请。
 * </p>
 */
@Mapper
public interface WithdrawOrderMapper extends BaseMapper<WithdrawOrder> {
}
