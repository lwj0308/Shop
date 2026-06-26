package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.admin.service.AdminDeptService;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.admin.mapper.AdminDeptMapper;
import com.shop.admin.mapper.AdminUserMapper;
import com.shop.model.admin.dto.AdminDeptCreateDTO;
import com.shop.model.admin.dto.AdminDeptUpdateDTO;
import com.shop.model.admin.entity.AdminDept;
import com.shop.model.admin.entity.AdminUser;
import com.shop.model.admin.vo.AdminDeptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门服务实现类
 * <p>
 * 实现部门树查询、部门CRUD等核心业务逻辑。
 * 部门用来组织管理员，配合角色的数据权限范围控制管理员能看到哪些数据。
 * 通过父子关系（parentId）构建树形部门结构。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDeptServiceImpl implements AdminDeptService {

    /** 部门Mapper，操作admin_dept表 */
    private final AdminDeptMapper adminDeptMapper;

    /** 管理员Mapper，用来检查部门下是否有管理员 */
    private final AdminUserMapper adminUserMapper;

    /**
     * 获取部门树
     * <p>
     * 1. 查询所有部门
     * 2. 将每个AdminDept转换为AdminDeptVO
     * 3. 构建树形结构：parentId=0的作为根节点，递归设置children
     * </p>
     */
    @Override
    public List<AdminDeptVO> getDeptTree() {
        // 查询所有部门，按排序号升序排列
        List<AdminDept> allDepts = adminDeptMapper.selectList(
                new LambdaQueryWrapper<AdminDept>().orderByAsc(AdminDept::getSort)
        );

        // 转换为VO列表
        List<AdminDeptVO> voList = allDepts.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 构建树形结构
        return buildTree(voList);
    }

    /**
     * 根据ID查询部门详情
     * <p>
     * 查找部门，找不到则抛出ADMIN_DEPT_NOT_FOUND异常。
     * </p>
     */
    @Override
    public AdminDeptVO getDeptById(Long id) {
        AdminDept dept = adminDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCode.ADMIN_DEPT_NOT_FOUND);
        }
        return convertToVO(dept);
    }

    /**
     * 新增部门
     * <p>
     * 1. 检查同一父部门下部门名称是否重复（同一个部门下不能有两个同名的子部门）
     * 2. 保存部门信息
     * </p>
     */
    @Override
    public void createDept(AdminDeptCreateDTO dto) {
        // 检查同一父部门下部门名称是否重复
        Long parentId = dto.getParentId() != null ? dto.getParentId() : 0L;
        Long count = adminDeptMapper.selectCount(
                new LambdaQueryWrapper<AdminDept>()
                        .eq(AdminDept::getParentId, parentId)
                        .eq(AdminDept::getName, dto.getName())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ADMIN_DEPT_NAME_EXISTS);
        }

        // 创建部门记录
        AdminDept dept = new AdminDept();
        dept.setParentId(parentId);
        dept.setName(dto.getName());
        dept.setSort(dto.getSort() != null ? dto.getSort() : 0);
        dept.setLeader(dto.getLeader());
        // 新增部门默认状态为正常(1)
        dept.setStatus(1);
        adminDeptMapper.insert(dept);

        log.info("新增部门成功，部门ID：{}，部门名称：{}", dept.getId(), dto.getName());
    }

    /**
     * 修改部门信息
     * <p>
     * 1. 查找部门，找不到则抛异常
     * 2. 如果修改了部门名称，检查同一父部门下是否重复
     * 3. 更新字段
     * </p>
     */
    @Override
    public void updateDept(Long id, AdminDeptUpdateDTO dto) {
        AdminDept dept = adminDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCode.ADMIN_DEPT_NOT_FOUND);
        }

        // 如果修改了部门名称，检查同一父部门下是否重复
        if (dto.getName() != null && StringUtils.hasText(dto.getName()) && !dto.getName().equals(dept.getName())) {
            Long count = adminDeptMapper.selectCount(
                    new LambdaQueryWrapper<AdminDept>()
                            .eq(AdminDept::getParentId, dept.getParentId())
                            .eq(AdminDept::getName, dto.getName())
                            .ne(AdminDept::getId, id)
            );
            if (count > 0) {
                throw new BusinessException(ErrorCode.ADMIN_DEPT_NAME_EXISTS);
            }
            dept.setName(dto.getName());
        }

        // 更新其他字段
        if (dto.getSort() != null) {
            dept.setSort(dto.getSort());
        }
        if (dto.getLeader() != null) {
            dept.setLeader(dto.getLeader());
        }
        if (dto.getStatus() != null) {
            dept.setStatus(dto.getStatus());
        }
        adminDeptMapper.updateById(dept);

        log.info("修改部门信息成功，部门ID：{}", id);
    }

    /**
     * 删除部门
     * <p>
     * 1. 查找部门，找不到则抛异常
     * 2. 检查是否有子部门（有子部门不能删）
     * 3. 检查是否有管理员属于该部门（有管理员不能删）
     * 4. 逻辑删除
     * </p>
     */
    @Override
    public void deleteDept(Long id) {
        AdminDept dept = adminDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCode.ADMIN_DEPT_NOT_FOUND);
        }

        // 检查是否有子部门
        Long childCount = adminDeptMapper.selectCount(
                new LambdaQueryWrapper<AdminDept>().eq(AdminDept::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.ADMIN_DEPT_HAS_CHILDREN);
        }

        // 检查是否有管理员属于该部门
        Long userCount = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getDeptId, id)
        );
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.ADMIN_DEPT_HAS_USERS);
        }

        // 逻辑删除
        adminDeptMapper.deleteById(id);

        log.info("删除部门成功，部门ID：{}", id);
    }

    /**
     * 构建树形结构
     * <p>
     * 从所有部门中找出根节点（parentId=0），然后递归设置children。
     * 使用Stream的filter+collect方式，简单直观。
     * </p>
     *
     * @param allDepts 所有部门VO列表
     * @return 树形结构的根节点列表
     */
    private List<AdminDeptVO> buildTree(List<AdminDeptVO> allDepts) {
        // 找出根节点（parentId=0的部门）
        List<AdminDeptVO> roots = allDepts.stream()
                .filter(d -> d.getParentId() == null || d.getParentId() == 0L)
                .collect(Collectors.toList());

        // 递归设置每个根节点的子节点
        for (AdminDeptVO root : roots) {
            root.setChildren(getChildren(root.getId(), allDepts));
        }

        return roots;
    }

    /**
     * 递归获取子节点
     * <p>
     * 从所有部门中找出parentId等于当前节点ID的部门，作为子节点。
     * 对每个子节点再递归查找它的子节点，直到没有更下级的节点为止。
     * </p>
     *
     * @param parentId 父节点ID
     * @param allDepts 所有部门VO列表
     * @return 子节点列表
     */
    private List<AdminDeptVO> getChildren(Long parentId, List<AdminDeptVO> allDepts) {
        List<AdminDeptVO> children = allDepts.stream()
                .filter(d -> parentId.equals(d.getParentId()))
                .collect(Collectors.toList());

        // 递归设置每个子节点的子节点
        for (AdminDeptVO child : children) {
            child.setChildren(getChildren(child.getId(), allDepts));
        }

        return children;
    }

    /**
     * 将AdminDept实体转换为AdminDeptVO
     *
     * @param dept 部门实体
     * @return 部门VO
     */
    private AdminDeptVO convertToVO(AdminDept dept) {
        AdminDeptVO vo = new AdminDeptVO();
        BeanUtils.copyProperties(dept, vo);
        return vo;
    }
}
