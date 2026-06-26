package com.shop.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.admin.entity.AdminRolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写SQL就能操作admin_role_permission表。
 * </p>
 */
@Mapper
public interface AdminRolePermissionMapper extends BaseMapper<AdminRolePermission> {
}
