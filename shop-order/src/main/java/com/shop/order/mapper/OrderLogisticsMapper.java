package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.order.entity.OrderLogistics;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物流信息Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作order_logistics表。
 * </p>
 */
@Mapper
public interface OrderLogisticsMapper extends BaseMapper<OrderLogistics> {
}
