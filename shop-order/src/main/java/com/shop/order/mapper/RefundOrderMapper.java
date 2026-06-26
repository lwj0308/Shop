package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.order.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退款单Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作refund_order表。
 * </p>
 */
@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {
}
