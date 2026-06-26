package com.shop.admin.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.admin.service.AdminUserService;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.util.SecurityUtils;
import com.shop.admin.mapper.AdminDeptMapper;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminUserMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.model.admin.dto.AdminPasswordUpdateDTO;
import com.shop.model.admin.dto.AdminUserCreateDTO;
import com.shop.model.admin.dto.AdminUserQueryDTO;
import com.shop.model.admin.dto.AdminUserUpdateDTO;
import com.shop.model.admin.entity.AdminDept;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminUser;
import com.shop.model.admin.entity.AdminUserRole;
import com.shop.model.admin.vo.AdminRoleVO;
import com.shop.model.admin.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理员服务实现类
 * <p>
 * 实现管理员CRUD、密码修改、状态切换等核心业务逻辑。
 * 管理员是后台系统的操作者，通过角色和权限控制能做什么操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** 管理员Mapper，操作admin_user表 */
    private final AdminUserMapper adminUserMapper;

    /** 管理员-角色关联Mapper，操作admin_user_role表 */
    private final AdminUserRoleMapper adminUserRoleMapper;

    /** 角色Mapper，查询角色信息用 */
    private final AdminRoleMapper adminRoleMapper;

    /** 部门Mapper，查询部门名称用 */
    private final AdminDeptMapper adminDeptMapper;

    /** 超级管理员ID，这个管理员不能被删除或禁用 */
    private static final Long SUPER_ADMIN_ID = 1L;

    /** 重置密码时使用的默认密码，管理员忘记密码时会被重置成这个 */
    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * 分页查询管理员列表
     * <p>
     * 1. 根据查询条件构建LambdaQueryWrapper（用户名模糊、昵称模糊、状态精确、部门精确）
     * 2. 执行分页查询
     * 3. 将每个AdminUser转换为AdminUserVO，并填充角色列表和部门名称
     * </p>
     */
    @Override
    public PageResult<AdminUserVO> getAdminUserList(AdminUserQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        // 用户名模糊搜索（输入了才查，没输入就跳过这个条件）
        wrapper.like(StringUtils.hasText(queryDTO.getUsername()), AdminUser::getUsername, queryDTO.getUsername());
        // 昵称模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getNickname()), AdminUser::getNickname, queryDTO.getNickname());
        // 状态精确筛选
        wrapper.eq(queryDTO.getStatus() != null, AdminUser::getStatus, queryDTO.getStatus());
        // 部门精确筛选
        wrapper.eq(queryDTO.getDeptId() != null, AdminUser::getDeptId, queryDTO.getDeptId());
        // 按创建时间倒序排列，最新的排在前面
        wrapper.orderByDesc(AdminUser::getCreateTime);

        // 执行分页查询
        Page<AdminUser> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<AdminUser> resultPage = adminUserMapper.selectPage(page, wrapper);
        List<AdminUser> records = resultPage.getRecords();

        // 批量预查询，解决N+1问题
        // 1. 收集所有需要查询的userId和deptId
        List<Long> userIds = records.stream().map(AdminUser::getId).collect(Collectors.toList());
        List<Long> deptIds = records.stream().map(AdminUser::getDeptId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 2. 批量查询用户-角色关联
        Map<Long, List<Long>> userRoleMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AdminUserRole> allUserRoles = adminUserRoleMapper.selectList(
                    new LambdaQueryWrapper<AdminUserRole>().in(AdminUserRole::getUserId, userIds)
            );
            // 按userId分组
            userRoleMap = allUserRoles.stream()
                    .collect(Collectors.groupingBy(AdminUserRole::getUserId,
                            Collectors.mapping(AdminUserRole::getRoleId, Collectors.toList())));
        }

        // 3. 批量查询角色
        List<Long> allRoleIds = userRoleMap.values().stream()
                .flatMap(List::stream).distinct().collect(Collectors.toList());
        Map<Long, AdminRole> roleMap = new HashMap<>();
        if (!allRoleIds.isEmpty()) {
            List<AdminRole> roles = adminRoleMapper.selectList(
                    new LambdaQueryWrapper<AdminRole>().in(AdminRole::getId, allRoleIds)
            );
            roleMap = roles.stream().collect(Collectors.toMap(AdminRole::getId, r -> r));
        }

        // 4. 批量查询部门
        Map<Long, AdminDept> deptMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            List<AdminDept> depts = adminDeptMapper.selectList(
                    new LambdaQueryWrapper<AdminDept>().in(AdminDept::getId, deptIds)
            );
            deptMap = depts.stream().collect(Collectors.toMap(AdminDept::getId, d -> d));
        }

        // 5. 转换VO（使用预查询的数据，不再逐个查数据库）
        Map<Long, List<Long>> finalUserRoleMap = userRoleMap;
        Map<Long, AdminRole> finalRoleMap = roleMap;
        Map<Long, AdminDept> finalDeptMap = deptMap;
        List<AdminUserVO> voList = records.stream()
                .map(user -> convertToVO(user, finalUserRoleMap, finalRoleMap, finalDeptMap))
                .collect(Collectors.toList());

        return PageResult.from(resultPage, voList);
    }

    /**
     * 根据ID查询管理员详情
     * <p>
     * 查找管理员，找不到则抛出ADMIN_NOT_FOUND异常。
     * 查到后转换为VO，并填充角色列表和部门名称。
     * </p>
     */
    @Override
    public AdminUserVO getAdminUserById(Long id) {
        AdminUser adminUser = adminUserMapper.selectById(id);
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }
        return convertToVO(adminUser);
    }

    /**
     * 新增管理员
     * <p>
     * 1. 检查用户名是否已存在（不能有两个同名的管理员）
     * 2. 用BCrypt加密密码（明文密码不能直接存数据库）
     * 3. 保存管理员基本信息
     * 4. 保存管理员和角色的关联关系（一个管理员可以有多个角色）
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAdminUser(AdminUserCreateDTO dto) {
        // 检查用户名是否已存在
        Long count = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ADMIN_USERNAME_EXISTS);
        }

        // 创建管理员记录
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(dto.getUsername());
        // 密码用BCrypt加密存储，防止泄露
        adminUser.setPassword(BCrypt.hashpw(dto.getPassword()));
        adminUser.setNickname(dto.getNickname());
        adminUser.setEmail(dto.getEmail());
        adminUser.setPhone(dto.getPhone());
        adminUser.setDeptId(dto.getDeptId());
        // 新增管理员默认状态为正常(1)
        adminUser.setStatus(1);
        adminUserMapper.insert(adminUser);

        // 保存管理员和角色的关联关系
        saveUserRoles(adminUser.getId(), dto.getRoleIds());

        log.info("新增管理员成功，管理员ID：{}，用户名：{}", adminUser.getId(), dto.getUsername());
    }

    /**
     * 修改管理员信息
     * <p>
     * 1. 查找管理员，找不到则抛异常
     * 2. 更新基本信息（用户名和密码不能在这里改）
     * 3. 更新角色关联关系（先删旧的再插新的）
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdminUser(Long id, AdminUserUpdateDTO dto) {
        AdminUser adminUser = adminUserMapper.selectById(id);
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        // 更新基本信息
        if (dto.getNickname() != null) {
            adminUser.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            adminUser.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            adminUser.setPhone(dto.getPhone());
        }
        if (dto.getDeptId() != null) {
            adminUser.setDeptId(dto.getDeptId());
        }
        if (dto.getStatus() != null) {
            adminUser.setStatus(dto.getStatus());
        }
        adminUserMapper.updateById(adminUser);

        // 更新角色关联关系（先删旧的再插新的）
        if (dto.getRoleIds() != null) {
            // 先删除该管理员的所有角色关联
            adminUserRoleMapper.delete(
                    new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, id)
            );
            // 再插入新的角色关联
            saveUserRoles(id, dto.getRoleIds());
        }

        log.info("修改管理员信息成功，管理员ID：{}", id);
    }

    /**
     * 删除管理员
     * <p>
     * 1. 查找管理员，找不到则抛异常
     * 2. 超级管理员（id=1）不能删除
     * 3. 逻辑删除（不是真删，只是标记deleted=1）
     * </p>
     */
    @Override
    public void deleteAdminUser(Long id) {
        AdminUser adminUser = adminUserMapper.selectById(id);
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        // 超级管理员不能删除
        if (SUPER_ADMIN_ID.equals(id)) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_IMMUTABLE);
        }

        // 逻辑删除
        adminUserMapper.deleteById(id);

        log.info("删除管理员成功，管理员ID：{}", id);
    }

    /**
     * 修改管理员密码
     * <p>
     * 1. 查找管理员，找不到则抛异常
     * 2. 验证旧密码是否正确（用BCrypt比对）
     * 3. 新密码用BCrypt加密后更新
     * </p>
     */
    @Override
    public void updatePassword(Long id, AdminPasswordUpdateDTO dto) {
        AdminUser adminUser = adminUserMapper.selectById(id);
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        // 验证旧密码是否正确
        if (!BCrypt.checkpw(dto.getOldPassword(), adminUser.getPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_OLD_PASSWORD_ERROR);
        }

        // 新密码用BCrypt加密后更新
        adminUser.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        adminUserMapper.updateById(adminUser);

        log.info("管理员修改密码成功，管理员ID：{}", id);
    }

    /**
     * 重置管理员密码
     * <p>
     * 1. 查找管理员，找不到则抛异常
     * 2. 把密码重置为默认密码（123456），用BCrypt加密后更新
     * 注意：这里不验证旧密码，因为是管理员忘记密码时由其他管理员帮忙重置的。
     * </p>
     */
    @Override
    public void resetPassword(Long id) {
        // 查找管理员，找不到就报错
        AdminUser adminUser = adminUserMapper.selectById(id);
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        // 把密码重置为默认密码，用BCrypt加密后存入数据库
        adminUser.setPassword(BCrypt.hashpw(DEFAULT_PASSWORD));
        adminUserMapper.updateById(adminUser);

        log.info("管理员密码重置成功，管理员ID：{}，用户名：{}", id, adminUser.getUsername());
    }

    /**
     * 修改管理员状态
     * <p>
     * 1. 查找管理员，找不到则抛异常
     * 2. 超级管理员（id=1）不能被禁用
     * 3. 更新状态
     * </p>
     */
    @Override
    public void updateStatus(Long id, Integer status) {
        AdminUser adminUser = adminUserMapper.selectById(id);
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        // 超级管理员不能被禁用
        if (SUPER_ADMIN_ID.equals(id)) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_IMMUTABLE);
        }

        adminUser.setStatus(status);
        adminUserMapper.updateById(adminUser);

        log.info("管理员状态修改成功，管理员ID：{}，新状态：{}", id, status);
    }

    /**
     * 获取当前登录管理员信息
     * <p>
     * 从SecurityUtils获取当前登录的管理员ID，查询详细信息返回。
     * 用于管理员登录后查看自己的信息。
     * </p>
     */
    @Override
    public AdminUserVO getCurrentAdminInfo() {
        Long currentUserId = SecurityUtils.requireLogin();
        AdminUser adminUser = adminUserMapper.selectById(currentUserId);
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }
        return convertToVO(adminUser);
    }

    /**
     * 批量保存用户-角色关联（优化版，一次插入所有关联记录）
     * <p>
     * 优化前：每个角色关联单独执行一次INSERT，5个角色就5次SQL
     * 优化后：使用MyBatis-Plus的批量插入，5个角色只需1次SQL
     * </p>
     *
     * @param userId  管理员ID
     * @param roleIds 角色ID列表
     */
    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<AdminUserRole> userRoles = roleIds.stream().map(roleId -> {
            AdminUserRole userRole = new AdminUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            return userRole;
        }).collect(Collectors.toList());

        // 使用MyBatis-Plus的Db工具类批量插入
        com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(userRoles);
    }

    /**
     * 查询管理员拥有的角色列表
     * <p>
     * 先从admin_user_role表查出该管理员关联的角色ID，
     * 再从admin_role表查出角色详情，转换为VO返回。
     * </p>
     *
     * @param userId 管理员ID
     * @return 角色VO列表
     */
    private List<AdminRoleVO> getUserRoles(Long userId) {
        // 查出该管理员关联的角色ID列表
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        // 根据角色ID列表查出角色详情
        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRole::getRoleId)
                .collect(Collectors.toList());
        List<AdminRole> roles = adminRoleMapper.selectBatchIds(roleIds);

        // 转换为VO
        return roles.stream().map(role -> {
            AdminRoleVO vo = new AdminRoleVO();
            BeanUtils.copyProperties(role, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 将AdminUser实体转换为AdminUserVO
     * <p>
     * 转换时填充角色列表和部门名称，方便前端直接显示。
     * </p>
     *
     * @param adminUser 管理员实体
     * @return 管理员VO（包含角色列表和部门名称）
     */
    private AdminUserVO convertToVO(AdminUser adminUser) {
        AdminUserVO vo = new AdminUserVO();
        BeanUtils.copyProperties(adminUser, vo);

        // 填充角色列表
        vo.setRoles(getUserRoles(adminUser.getId()));

        // 填充部门名称
        if (adminUser.getDeptId() != null) {
            AdminDept dept = adminDeptMapper.selectById(adminUser.getDeptId());
            if (dept != null) {
                vo.setDeptName(dept.getName());
            }
        }

        return vo;
    }

    /**
     * 将AdminUser实体转换为VO（批量查询版本，解决N+1问题）
     * <p>
     * 与单个查询版本不同，这个方法使用预查询好的Map来获取角色和部门信息，
     * 不需要再逐个查数据库，10条数据从31次SQL降到4次。
     * </p>
     *
     * @param adminUser    管理员实体
     * @param userRoleMap  用户-角色ID映射（预查询好的）
     * @param roleMap      角色ID-角色映射（预查询好的）
     * @param deptMap      部门ID-部门映射（预查询好的）
     * @return 管理员VO
     */
    private AdminUserVO convertToVO(AdminUser adminUser, Map<Long, List<Long>> userRoleMap,
                                     Map<Long, AdminRole> roleMap, Map<Long, AdminDept> deptMap) {
        AdminUserVO vo = new AdminUserVO();
        BeanUtils.copyProperties(adminUser, vo);

        // 设置角色信息（从预查询的Map中获取，不再查数据库）
        List<Long> roleIds = userRoleMap.getOrDefault(adminUser.getId(), Collections.emptyList());
        List<AdminRoleVO> roleVOList = roleIds.stream()
                .map(roleMap::get)
                .filter(Objects::nonNull)
                .map(role -> {
                    AdminRoleVO roleVO = new AdminRoleVO();
                    BeanUtils.copyProperties(role, roleVO);
                    return roleVO;
                })
                .collect(Collectors.toList());
        vo.setRoles(roleVOList);

        // 设置部门名称（从预查询的Map中获取）
        if (adminUser.getDeptId() != null) {
            AdminDept dept = deptMap.get(adminUser.getDeptId());
            if (dept != null) {
                vo.setDeptName(dept.getName());
            }
        }

        return vo;
    }
}
