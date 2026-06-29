package com.shop.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀活动 Mapper
 * <p>
 * MyBatis-Plus 的 BaseMapper 已提供基本 CRUD，复杂的查询在 Service 层用 LambdaQueryWrapper 实现。
 * </p>
 */
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {
}
