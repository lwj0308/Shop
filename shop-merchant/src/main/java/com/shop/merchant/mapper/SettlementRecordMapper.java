package com.shop.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.merchant.entity.SettlementRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结算流水Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力。
 * 操作 settlement_record 表，记录每笔订单的结算信息。
 * </p>
 */
@Mapper
public interface SettlementRecordMapper extends BaseMapper<SettlementRecord> {
}
