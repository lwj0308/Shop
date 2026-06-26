package com.shop.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.merchant.entity.MerchantQualification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家资质Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写SQL就能操作merchant_qualification表。
 * </p>
 */
@Mapper
public interface MerchantQualificationMapper extends BaseMapper<MerchantQualification> {
}
