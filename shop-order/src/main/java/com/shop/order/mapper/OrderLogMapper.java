package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.order.entity.OrderLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单状态日志Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作order_log表。
 * </p>
 */
@Mapper
public interface OrderLogMapper extends BaseMapper<OrderLog> {
}
