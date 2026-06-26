package com.shop.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.admin.entity.AdminDept;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门信息Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写SQL就能操作admin_dept表。
 * </p>
 */
@Mapper
public interface AdminDeptMapper extends BaseMapper<AdminDept> {
}
