package com.shop.admin.service;

import com.shop.model.admin.dto.AdminPermissionCreateDTO;
import com.shop.model.admin.vo.AdminPermissionVO;

import java.util.List;

/**
 * 权限服务接口
 * <p>
 * 定义权限/菜单相关的业务方法，包括权限树查询、权限CRUD等。
 * 权限分三种类型：目录（一级分类）、菜单（可点击的页面）、按钮（页面上的操作权限）。
 * 通过父子关系（parentId）构建树形菜单结构。
 * </p>
 */
public interface AdminPermissionService {

    /**
     * 获取权限树
     * <p>
     * 查询所有权限，按父子关系构建成树形结构返回。
     * 顶级权限的parentId为0，作为树的根节点。
     * 前端拿到树形数据后可以递归渲染成菜单树。
     * </p>
     *
     * @return 权限树列表（只有根节点，子节点在children里）
     */
    List<AdminPermissionVO> getPermissionTree();

    /**
     * 根据ID查询权限详情
     * <p>
     * 查询单个权限的详细信息，找不到则抛出异常。
     * </p>
     *
     * @param id 权限ID
     * @return 权限详细信息
     */
    AdminPermissionVO getPermissionById(Long id);

    /**
     * 新增权限
     * <p>
     * 创建一个新的权限/菜单，比如新增一个"用户管理"菜单或"新增用户"按钮权限。
     * </p>
     *
     * @param dto 新增参数，包含权限名称、类型、父级ID、路由路径等
     */
    void createPermission(AdminPermissionCreateDTO dto);

    /**
     * 修改权限
     * <p>
     * 修改权限/菜单的信息，比如修改名称、图标、排序等。
     * </p>
     *
     * @param id  权限ID
     * @param dto 修改参数，和新增参数一样
     */
    void updatePermission(Long id, AdminPermissionCreateDTO dto);

    /**
     * 删除权限
     * <p>
     * 删除权限/菜单，如果该权限下还有子权限，则不允许删除。
     * 需要先删除子权限，才能删除父权限。
     * </p>
     *
     * @param id 权限ID
     */
    void deletePermission(Long id);
}
