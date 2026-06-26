package com.shop.admin.service;

import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminRoleCreateDTO;
import com.shop.model.admin.dto.AdminRoleQueryDTO;
import com.shop.model.admin.dto.AdminRoleUpdateDTO;
import com.shop.model.admin.vo.AdminRoleVO;

import java.util.List;

/**
 * 角色服务接口
 * <p>
 * 定义角色相关的业务方法，包括角色CRUD、权限分配等。
 * 角色是RBAC权限模型的核心，一个角色可以拥有多个权限，一个管理员可以拥有多个角色。
 * </p>
 */
public interface AdminRoleService {

    /**
     * 分页查询角色列表
     * <p>
     * 支持按角色名称、角色标识模糊搜索，按状态精确筛选。
     * </p>
     *
     * @param queryDTO 查询条件，包含角色名称、角色标识、状态、分页参数
     * @return 分页结果，包含角色VO列表和分页信息
     */
    PageResult<AdminRoleVO> getAdminRoleList(AdminRoleQueryDTO queryDTO);

    /**
     * 查询所有正常状态的角色
     * <p>
     * 用于下拉选择框，比如给管理员分配角色时，需要列出所有可选的角色。
     * 只返回状态为正常的角色，禁用的角色不显示。
     * </p>
     *
     * @return 角色VO列表
     */
    List<AdminRoleVO> getAllRoles();

    /**
     * 根据ID查询角色详情
     * <p>
     * 查询角色的基本信息和权限列表，找不到则抛出异常。
     * </p>
     *
     * @param id 角色ID
     * @return 角色详细信息（包含权限列表）
     */
    AdminRoleVO getAdminRoleById(Long id);

    /**
     * 新增角色
     * <p>
     * 创建一个新的角色，需要检查角色标识（roleKey）是否重复，
     * 同时保存角色和权限的关联关系。
     * </p>
     *
     * @param dto 新增参数，包含角色名称、角色标识、权限ID列表等
     */
    void createAdminRole(AdminRoleCreateDTO dto);

    /**
     * 修改角色信息
     * <p>
     * 修改角色的基本信息和权限分配，权限分配会先删除旧关联再插入新关联。
     * 超级管理员角色（id=1）不能修改。
     * </p>
     *
     * @param id  角色ID
     * @param dto 修改参数，包含角色名称、角色标识、权限ID列表等
     */
    void updateAdminRole(Long id, AdminRoleUpdateDTO dto);

    /**
     * 删除角色
     * <p>
     * 逻辑删除角色，超级管理员角色（id=1）不能删除。
     * 如果有管理员正在使用该角色，也不允许删除。
     * </p>
     *
     * @param id 角色ID
     */
    void deleteAdminRole(Long id);
}
