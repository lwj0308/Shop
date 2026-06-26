package com.shop.admin.service;

import com.shop.model.admin.dto.AdminDeptCreateDTO;
import com.shop.model.admin.dto.AdminDeptUpdateDTO;
import com.shop.model.admin.vo.AdminDeptVO;

import java.util.List;

/**
 * 部门服务接口
 * <p>
 * 定义部门相关的业务方法，包括部门树查询、部门CRUD等。
 * 部门用来组织管理员，配合角色的数据权限范围控制管理员能看到哪些数据。
 * 通过父子关系（parentId）构建树形部门结构。
 * </p>
 */
public interface AdminDeptService {

    /**
     * 获取部门树
     * <p>
     * 查询所有部门，按父子关系构建成树形结构返回。
     * 顶级部门的parentId为0，作为树的根节点。
     * 前端拿到树形数据后可以递归渲染成部门树。
     * </p>
     *
     * @return 部门树列表（只有根节点，子节点在children里）
     */
    List<AdminDeptVO> getDeptTree();

    /**
     * 根据ID查询部门详情
     * <p>
     * 查询单个部门的详细信息，找不到则抛出异常。
     * </p>
     *
     * @param id 部门ID
     * @return 部门详细信息
     */
    AdminDeptVO getDeptById(Long id);

    /**
     * 新增部门
     * <p>
     * 创建一个新的部门，需要检查同一父部门下部门名称是否重复。
     * </p>
     *
     * @param dto 新增参数，包含部门名称、父级部门ID、排序号、负责人等
     */
    void createDept(AdminDeptCreateDTO dto);

    /**
     * 修改部门信息
     * <p>
     * 修改部门的基本信息，如果修改了部门名称需要检查同一父部门下是否重复。
     * </p>
     *
     * @param id  部门ID
     * @param dto 修改参数，包含部门名称、排序号、负责人、状态等
     */
    void updateDept(Long id, AdminDeptUpdateDTO dto);

    /**
     * 删除部门
     * <p>
     * 逻辑删除部门，如果该部门下还有子部门或有管理员属于该部门，则不允许删除。
     * </p>
     *
     * @param id 部门ID
     */
    void deleteDept(Long id);
}
