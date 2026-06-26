package com.shop.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.admin.mapper.AdminPermissionMapper;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminRolePermissionMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.model.admin.entity.AdminPermission;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminRolePermission;
import com.shop.model.admin.entity.AdminUserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sa-Token权限认证接口实现
 * <p>
 * Sa-Token通过StpInterface接口获取当前登录账号的权限列表和角色列表。
 * 当代码中调用StpUtil.checkPermission()或StpUtil.checkRole()时，
 * Sa-Token会自动调用这个实现类来查询当前用户拥有的权限和角色。
 * </p>
 * <p>
 * RBAC模型：管理员 → 角色 → 权限
 * 一个管理员可以有多个角色，一个角色可以有多个权限。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminStpInterfaceImpl implements StpInterface {

    /** 管理员-角色关联Mapper，查询管理员拥有哪些角色 */
    private final AdminUserRoleMapper adminUserRoleMapper;

    /** 角色-权限关联Mapper，查询角色拥有哪些权限 */
    private final AdminRolePermissionMapper adminRolePermissionMapper;

    /** 角色Mapper，查询角色详情 */
    private final AdminRoleMapper adminRoleMapper;

    /** 权限Mapper，查询权限详情 */
    private final AdminPermissionMapper adminPermissionMapper;

    /**
     * 获取当前账号的权限列表（带缓存）
     * <p>
     * Sa-Token每次鉴权都会调用这个方法，如果不缓存，每次都要查4次数据库。
     * 我们把权限列表缓存到Sa-Token Session中，设置过期时间30分钟，
     * 这样30分钟内只需要查一次数据库，大大减少数据库压力。
     * </p>
     *
     * @param loginId   登录ID，即管理员ID
     * @param loginType 登录类型（Sa-Token支持多端登录，这里用不到但接口要求有这个参数）
     * @return 权限标识列表，比如["user:list", "user:add", "role:delete"]
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 先从Session缓存中获取，避免每次鉴权都查数据库
        List<String> cachedPermissions = (List<String>) StpUtil.getSessionByLoginId(loginId).get("permissionList");
        if (cachedPermissions != null) {
            return cachedPermissions;
        }

        // 缓存中没有，从数据库查询
        List<String> permissions = getAdminPermissions(Long.parseLong(loginId.toString()));

        // 存入Session缓存（Session本身会随Token过期而失效，无需单独设TTL）
        StpUtil.getSessionByLoginId(loginId).set("permissionList", permissions);

        return permissions;
    }

    /**
     * 获取当前账号的角色标识列表（带缓存）
     * <p>
     * 同权限列表一样，角色列表也缓存到Session中，避免重复查询数据库。
     * </p>
     *
     * @param loginId   登录ID，即管理员ID
     * @param loginType 登录类型
     * @return 角色标识列表，比如["admin", "operator"]
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 先从Session缓存中获取
        List<String> cachedRoles = (List<String>) StpUtil.getSessionByLoginId(loginId).get("roleList");
        if (cachedRoles != null) {
            return cachedRoles;
        }

        // 缓存中没有，从数据库查询
        List<String> roles = getAdminRoles(Long.parseLong(loginId.toString()));

        // 存入Session缓存（Session本身会随Token过期而失效，无需单独设TTL）
        StpUtil.getSessionByLoginId(loginId).set("roleList", roles);

        return roles;
    }

    /**
     * 从数据库查询管理员拥有的权限列表
     * <p>
     * 查询流程：管理员ID → admin_user_role表找到角色ID → admin_role_permission表找到权限ID → admin_permission表找到权限标识
     * 只返回状态为启用(1)的权限，禁用的权限不算数。
     * </p>
     *
     * @param adminId 管理员ID
     * @return 权限标识列表
     */
    private List<String> getAdminPermissions(Long adminId) {
        // 1. 根据管理员ID查出所有角色ID
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminId)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 取出角色ID列表
        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRole::getRoleId)
                .collect(Collectors.toList());

        // 3. 过滤掉禁用的角色，只保留启用的角色
        List<AdminRole> activeRoles = adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>()
                        .in(AdminRole::getId, roleIds)
                        .eq(AdminRole::getStatus, 1)
        );
        if (activeRoles.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 取出启用角色的ID列表
        List<Long> activeRoleIds = activeRoles.stream()
                .map(AdminRole::getId)
                .collect(Collectors.toList());

        // 5. 根据角色ID查出所有权限ID
        List<AdminRolePermission> rolePermissions = adminRolePermissionMapper.selectList(
                new LambdaQueryWrapper<AdminRolePermission>()
                        .in(AdminRolePermission::getRoleId, activeRoleIds)
        );
        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 6. 取出权限ID列表
        List<Long> permissionIds = rolePermissions.stream()
                .map(AdminRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());

        // 7. 查出权限详情，只返回启用状态的权限标识
        List<AdminPermission> permissions = adminPermissionMapper.selectList(
                new LambdaQueryWrapper<AdminPermission>()
                        .in(AdminPermission::getId, permissionIds)
                        .eq(AdminPermission::getStatus, 1)
        );

        return permissions.stream()
                .map(AdminPermission::getPermissionKey)
                .collect(Collectors.toList());
    }

    /**
     * 从数据库查询管理员拥有的角色列表
     * <p>
     * 查询流程：管理员ID → admin_user_role表找到角色ID → admin_role表找到角色标识
     * 只返回状态为启用(1)的角色，禁用的角色不算数。
     * </p>
     *
     * @param adminId 管理员ID
     * @return 角色标识列表
     */
    private List<String> getAdminRoles(Long adminId) {
        // 1. 根据管理员ID查出所有角色ID
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminId)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 取出角色ID列表
        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRole::getRoleId)
                .collect(Collectors.toList());

        // 3. 查出角色详情，只返回启用状态的角色标识
        List<AdminRole> roles = adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>()
                        .in(AdminRole::getId, roleIds)
                        .eq(AdminRole::getStatus, 1)
        );

        return roles.stream()
                .map(AdminRole::getRoleKey)
                .collect(Collectors.toList());
    }
}
