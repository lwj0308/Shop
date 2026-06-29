package com.shop.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.promotion.entity.Promotion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 满减活动 Mapper
 * <p>
 * MyBatis-Plus 的 BaseMapper 已提供基本 CRUD，复杂的查询在 Service 层用 LambdaQueryWrapper 实现。
 * </p>
 */
@Mapper
public interface PromotionMapper extends BaseMapper<Promotion> {
}
