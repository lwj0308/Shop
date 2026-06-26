package com.shop.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.merchant.entity.MerchantSettlement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家结算账户Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写SQL就能操作merchant_settlement表。
 * </p>
 */
@Mapper
public interface MerchantSettlementMapper extends BaseMapper<MerchantSettlement> {
}
