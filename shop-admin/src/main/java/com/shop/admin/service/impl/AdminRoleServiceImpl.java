package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.admin.service.AdminRoleService;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminRolePermissionMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.model.admin.dto.AdminRoleCreateDTO;
import com.shop.model.admin.dto.AdminRoleQueryDTO;
import com.shop.model.admin.dto.AdminRoleUpdateDTO;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminRolePermission;
import com.shop.model.admin.entity.AdminUserRole;
import com.shop.model.admin.vo.AdminPermissionVO;
import com.shop.model.admin.vo.AdminRoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现类
 * <p>
 * 实现角色CRUD、权限分配等核心业务逻辑。
 * 角色是RBAC权限模型的核心，一个角色可以拥有多个权限，一个管理员可以拥有多个角色。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    /** 角色Mapper，操作admin_role表 */
    private final AdminRoleMapper adminRoleMapper;

    /** 角色-权限关联Mapper，操作admin_role_permission表 */
    private final AdminRolePermissionMapper adminRolePermissionMapper;

    /** 管理员-角色关联Mapper，用来检查角色是否被管理员使用 */
    private final AdminUserRoleMapper adminUserRoleMapper;

    /** 超级管理员角色ID，这个角色不能被修改或删除 */
    private static final Long SUPER_ADMIN_ROLE_ID = 1L;

    /**
     * 分页查询角色列表
     * <p>
     * 1. 根据查询条件构建LambdaQueryWrapper（角色名称模糊、角色标识模糊、状态精确）
     * 2. 执行分页查询
     * 3. 将每个AdminRole转换为AdminRoleVO
     * </p>
     */
    @Override
    public PageResult<AdminRoleVO> getAdminRoleList(AdminRoleQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<AdminRole> wrapper = new LambdaQueryWrapper<>();
        // 角色名称模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getRoleName()), AdminRole::getRoleName, queryDTO.getRoleName());
        // 角色标识模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getRoleKey()), AdminRole::getRoleKey, queryDTO.getRoleKey());
        // 状态精确筛选
        wrapper.eq(queryDTO.getStatus() != null, AdminRole::getStatus, queryDTO.getStatus());
        // 按创建时间倒序排列
        wrapper.orderByDesc(AdminRole::getCreateTime);

        // 执行分页查询
        Page<AdminRole> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<AdminRole> resultPage = adminRoleMapper.selectPage(page, wrapper);

        // 将AdminRole列表转换为AdminRoleVO列表
        List<AdminRoleVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.from(resultPage, voList);
    }

    /**
     * 查询所有正常状态的角色
     * <p>
     * 用于下拉选择框，只返回状态为正常(1)的角色。
     * </p>
     */
    @Override
    public List<AdminRoleVO> getAllRoles() {
        List<AdminRole> roles = adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>()
                        .eq(AdminRole::getStatus, 1)
                        .orderByAsc(AdminRole::getId)
        );
        return roles.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID查询角色详情
     * <p>
     * 查找角色，找不到则抛出ADMIN_ROLE_NOT_FOUND异常。
     * 查到后转换为VO，并填充权限列表。
     * </p>
     */
    @Override
    public AdminRoleVO getAdminRoleById(Long id) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND);
        }
        return convertToVOWithPermissions(role);
    }

    /**
     * 新增角色
     * <p>
     * 1. 检查角色标识（roleKey）是否已存在（角色标识是唯一的）
     * 2. 保存角色基本信息
     * 3. 保存角色和权限的关联关系
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAdminRole(AdminRoleCreateDTO dto) {
        // 检查角色标识是否已存在
        Long count = adminRoleMapper.selectCount(
                new LambdaQueryWrapper<AdminRole>().eq(AdminRole::getRoleKey, dto.getRoleKey())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_KEY_EXISTS);
        }

        // 创建角色记录
        AdminRole role = new AdminRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setDataScope(dto.getDataScope());
        role.setRemark(dto.getRemark());
        // 新增角色默认状态为正常(1)
        role.setStatus(1);
        adminRoleMapper.insert(role);

        // 保存角色和权限的关联关系
        saveRolePermissions(role.getId(), dto.getPermissionIds());

        log.info("新增角色成功，角色ID：{}，角色标识：{}", role.getId(), dto.getRoleKey());
    }

    /**
     * 修改角色信息
     * <p>
     * 1. 查找角色，找不到则抛异常
     * 2. 超级管理员角色（id=1）不能修改
     * 3. 更新角色基本信息
     * 4. 更新权限关联关系（先删旧的再插新的）
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdminRole(Long id, AdminRoleUpdateDTO dto) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND);
        }

        // 超级管理员角色不能修改
        if (SUPER_ADMIN_ROLE_ID.equals(id)) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_IMMUTABLE);
        }

        // 更新基本信息
        if (dto.getRoleName() != null) {
            role.setRoleName(dto.getRoleName());
        }
        if (dto.getRoleKey() != null) {
            role.setRoleKey(dto.getRoleKey());
        }
        if (dto.getDataScope() != null) {
            role.setDataScope(dto.getDataScope());
        }
        if (dto.getStatus() != null) {
            role.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            role.setRemark(dto.getRemark());
        }
        adminRoleMapper.updateById(role);

        // 更新权限关联关系（先删旧的再插新的）
        if (dto.getPermissionIds() != null) {
            adminRolePermissionMapper.delete(
                    new LambdaQueryWrapper<AdminRolePermission>().eq(AdminRolePermission::getRoleId, id)
            );
            saveRolePermissions(id, dto.getPermissionIds());
        }

        log.info("修改角色信息成功，角色ID：{}", id);
    }

    /**
     * 删除角色
     * <p>
     * 1. 查找角色，找不到则抛异常
     * 2. 超级管理员角色（id=1）不能删除
     * 3. 如果有管理员正在使用该角色，不允许删除
     * 4. 逻辑删除
     * </p>
     */
    @Override
    public void deleteAdminRole(Long id) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND);
        }

        // 超级管理员角色不能删除
        if (SUPER_ADMIN_ROLE_ID.equals(id)) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_IMMUTABLE);
        }

        // 检查是否有管理员正在使用该角色
        Long userCount = adminUserRoleMapper.selectCount(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getRoleId, id)
        );
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_IMMUTABLE);
        }

        // 逻辑删除
        adminRoleMapper.deleteById(id);

        log.info("删除角色成功，角色ID：{}", id);
    }

    /**
     * 批量保存角色-权限关联（优化版，一次插入所有关联记录）
     * <p>
     * 优化前：每个权限关联单独执行一次INSERT，10个权限就10次SQL
     * 优化后：使用MyBatis-Plus的批量插入，10个权限只需1次SQL
     * </p>
     *
     * @param roleId        角色ID
     * @param permissionIds 权限ID列表
     */
    private void saveRolePermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        List<AdminRolePermission> rolePermissions = permissionIds.stream().map(permissionId -> {
            AdminRolePermission rolePermission = new AdminRolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            return rolePermission;
        }).collect(Collectors.toList());

        // 使用MyBatis-Plus的Db工具类批量插入
        com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(rolePermissions);
    }

    /**
     * 将AdminRole实体转换为AdminRoleVO（不含权限列表）
     * <p>
     * 用于列表查询，不需要展示权限详情，减少数据库查询。
     * </p>
     *
     * @param role 角色实体
     * @return 角色VO（不含权限列表）
     */
    private AdminRoleVO convertToVO(AdminRole role) {
        AdminRoleVO vo = new AdminRoleVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }

    /**
     * 将AdminRole实体转换为AdminRoleVO（含权限列表）
     * <p>
     * 用于详情查询，需要展示该角色拥有的所有权限。
     * </p>
     *
     * @param role 角色实体
     * @return 角色VO（含权限列表）
     */
    private AdminRoleVO convertToVOWithPermissions(AdminRole role) {
        AdminRoleVO vo = new AdminRoleVO();
        BeanUtils.copyProperties(role, vo);

        // 查询该角色关联的权限ID列表
        List<AdminRolePermission> rolePermissions = adminRolePermissionMapper.selectList(
                new LambdaQueryWrapper<AdminRolePermission>().eq(AdminRolePermission::getRoleId, role.getId())
        );

        if (!rolePermissions.isEmpty()) {
            // 将权限ID列表设置到VO中（用简单的AdminPermissionVO只带id，前端回显用）
            List<AdminPermissionVO> permissionVOs = rolePermissions.stream().map(rp -> {
                AdminPermissionVO pvo = new AdminPermissionVO();
                pvo.setId(rp.getPermissionId());
                return pvo;
            }).collect(Collectors.toList());
            vo.setPermissions(permissionVOs);
        } else {
            vo.setPermissions(Collections.emptyList());
        }

        return vo;
    }
}
