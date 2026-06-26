package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.admin.service.AdminPermissionService;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.admin.mapper.AdminPermissionMapper;
import com.shop.model.admin.dto.AdminPermissionCreateDTO;
import com.shop.model.admin.entity.AdminPermission;
import com.shop.model.admin.vo.AdminPermissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限服务实现类
 * <p>
 * 实现权限树查询、权限CRUD等核心业务逻辑。
 * 权限分三种类型：目录（一级分类）、菜单（可点击的页面）、按钮（页面上的操作权限）。
 * 通过父子关系（parentId）构建树形菜单结构。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl implements AdminPermissionService {

    /** 权限Mapper，操作admin_permission表 */
    private final AdminPermissionMapper adminPermissionMapper;

    /**
     * 获取权限树
     * <p>
     * 1. 查询所有权限
     * 2. 将每个AdminPermission转换为AdminPermissionVO
     * 3. 构建树形结构：parentId=0的作为根节点，递归设置children
     * </p>
     */
    @Override
    public List<AdminPermissionVO> getPermissionTree() {
        // 查询所有权限，按排序号升序排列
        List<AdminPermission> allPermissions = adminPermissionMapper.selectList(
                new LambdaQueryWrapper<AdminPermission>().orderByAsc(AdminPermission::getSort)
        );

        // 转换为VO列表
        List<AdminPermissionVO> voList = allPermissions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 构建树形结构
        return buildTree(voList);
    }

    /**
     * 根据ID查询权限详情
     * <p>
     * 查找权限，找不到则抛出ADMIN_PERMISSION_NOT_FOUND异常。
     * </p>
     */
    @Override
    public AdminPermissionVO getPermissionById(Long id) {
        AdminPermission permission = adminPermissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.ADMIN_PERMISSION_NOT_FOUND);
        }
        return convertToVO(permission);
    }

    /**
     * 新增权限
     * <p>
     * 创建一个新的权限/菜单，直接保存即可。
     * </p>
     */
    @Override
    public void createPermission(AdminPermissionCreateDTO dto) {
        AdminPermission permission = new AdminPermission();
        permission.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        permission.setName(dto.getName());
        permission.setType(dto.getType());
        permission.setPermissionKey(dto.getPermissionKey());
        permission.setPath(dto.getPath());
        permission.setIcon(dto.getIcon());
        permission.setSort(dto.getSort() != null ? dto.getSort() : 0);
        // 新增权限默认状态为正常(1)
        permission.setStatus(1);
        adminPermissionMapper.insert(permission);

        log.info("新增权限成功，权限ID：{}，权限名称：{}", permission.getId(), dto.getName());
    }

    /**
     * 修改权限
     * <p>
     * 1. 查找权限，找不到则抛异常
     * 2. 更新字段
     * </p>
     */
    @Override
    public void updatePermission(Long id, AdminPermissionCreateDTO dto) {
        AdminPermission permission = adminPermissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.ADMIN_PERMISSION_NOT_FOUND);
        }

        // 更新字段
        if (dto.getParentId() != null) {
            permission.setParentId(dto.getParentId());
        }
        if (dto.getName() != null) {
            permission.setName(dto.getName());
        }
        if (dto.getType() != null) {
            permission.setType(dto.getType());
        }
        if (dto.getPermissionKey() != null) {
            permission.setPermissionKey(dto.getPermissionKey());
        }
        if (dto.getPath() != null) {
            permission.setPath(dto.getPath());
        }
        if (dto.getIcon() != null) {
            permission.setIcon(dto.getIcon());
        }
        if (dto.getSort() != null) {
            permission.setSort(dto.getSort());
        }
        adminPermissionMapper.updateById(permission);

        log.info("修改权限成功，权限ID：{}", id);
    }

    /**
     * 删除权限
     * <p>
     * 1. 查找权限，找不到则抛异常
     * 2. 检查是否有子权限（有子权限不能删，得先删子权限）
     * 3. 删除权限
     * </p>
     */
    @Override
    public void deletePermission(Long id) {
        AdminPermission permission = adminPermissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.ADMIN_PERMISSION_NOT_FOUND);
        }

        // 检查是否有子权限
        Long childCount = adminPermissionMapper.selectCount(
                new LambdaQueryWrapper<AdminPermission>().eq(AdminPermission::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL);
        }

        // 删除权限（物理删除，因为admin_permission表没有逻辑删除字段）
        adminPermissionMapper.deleteById(id);

        log.info("删除权限成功，权限ID：{}", id);
    }

    /**
     * 构建树形结构
     * <p>
     * 从所有权限中找出根节点（parentId=0），然后递归设置children。
     * 使用Stream的filter+collect方式，简单直观。
     * </p>
     *
     * @param allPermissions 所有权限VO列表
     * @return 树形结构的根节点列表
     */
    private List<AdminPermissionVO> buildTree(List<AdminPermissionVO> allPermissions) {
        // 找出根节点（parentId=0的权限）
        List<AdminPermissionVO> roots = allPermissions.stream()
                .filter(p -> p.getParentId() == null || p.getParentId() == 0L)
                .collect(Collectors.toList());

        // 递归设置每个根节点的子节点
        for (AdminPermissionVO root : roots) {
            root.setChildren(getChildren(root.getId(), allPermissions));
        }

        return roots;
    }

    /**
     * 递归获取子节点
     * <p>
     * 从所有权限中找出parentId等于当前节点ID的权限，作为子节点。
     * 对每个子节点再递归查找它的子节点，直到没有更下级的节点为止。
     * </p>
     *
     * @param parentId       父节点ID
     * @param allPermissions 所有权限VO列表
     * @return 子节点列表
     */
    private List<AdminPermissionVO> getChildren(Long parentId, List<AdminPermissionVO> allPermissions) {
        List<AdminPermissionVO> children = allPermissions.stream()
                .filter(p -> parentId.equals(p.getParentId()))
                .collect(Collectors.toList());

        // 递归设置每个子节点的子节点
        for (AdminPermissionVO child : children) {
            child.setChildren(getChildren(child.getId(), allPermissions));
        }

        return children;
    }

    /**
     * 将AdminPermission实体转换为AdminPermissionVO
     *
     * @param permission 权限实体
     * @return 权限VO
     */
    private AdminPermissionVO convertToVO(AdminPermission permission) {
        AdminPermissionVO vo = new AdminPermissionVO();
        BeanUtils.copyProperties(permission, vo);
        return vo;
    }
}
