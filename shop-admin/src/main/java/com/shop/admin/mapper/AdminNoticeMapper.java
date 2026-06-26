package com.shop.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.admin.entity.AdminNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告管理Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写SQL就能操作admin_notice表。
 * </p>
 */
@Mapper
public interface AdminNoticeMapper extends BaseMapper<AdminNotice> {
}
