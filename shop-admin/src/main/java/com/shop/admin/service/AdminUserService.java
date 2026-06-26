package com.shop.admin.service;

import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminPasswordUpdateDTO;
import com.shop.model.admin.dto.AdminUserCreateDTO;
import com.shop.model.admin.dto.AdminUserQueryDTO;
import com.shop.model.admin.dto.AdminUserUpdateDTO;
import com.shop.model.admin.vo.AdminUserVO;

/**
 * 管理员服务接口
 * <p>
 * 定义管理员相关的业务方法，包括管理员CRUD、密码修改、状态切换等。
 * 管理员是后台系统的操作者，通过角色和权限控制能做什么操作。
 * </p>
 */
public interface AdminUserService {

    /**
     * 分页查询管理员列表
     * <p>
     * 支持按用户名、昵称模糊搜索，按状态和部门精确筛选。
     * 查询结果包含每个管理员的角色信息。
     * </p>
     *
     * @param queryDTO 查询条件，包含用户名、昵称、状态、部门ID、分页参数
     * @return 分页结果，包含管理员VO列表和分页信息
     */
    PageResult<AdminUserVO> getAdminUserList(AdminUserQueryDTO queryDTO);

    /**
     * 根据ID查询管理员详情
     * <p>
     * 查询管理员的基本信息和角色列表，找不到则抛出异常。
     * </p>
     *
     * @param id 管理员ID
     * @return 管理员详细信息（包含角色列表）
     */
    AdminUserVO getAdminUserById(Long id);

    /**
     * 新增管理员
     * <p>
     * 创建一个新的管理员账号，需要检查用户名是否重复，
     * 密码会用BCrypt加密存储，同时保存管理员和角色的关联关系。
     * </p>
     *
     * @param dto 新增参数，包含用户名、密码、昵称、角色ID列表等
     */
    void createAdminUser(AdminUserCreateDTO dto);

    /**
     * 修改管理员信息
     * <p>
     * 修改管理员的基本信息和角色分配，角色分配会先删除旧关联再插入新关联。
     * 不能修改用户名和密码，密码修改走专门的接口。
     * </p>
     *
     * @param id  管理员ID
     * @param dto 修改参数，包含昵称、邮箱、手机号、部门、角色等
     */
    void updateAdminUser(Long id, AdminUserUpdateDTO dto);

    /**
     * 删除管理员
     * <p>
     * 逻辑删除管理员账号（不是真删，只是标记为已删除）。
     * 超级管理员（id=1）不能被删除。
     * </p>
     *
     * @param id 管理员ID
     */
    void deleteAdminUser(Long id);

    /**
     * 修改管理员密码
     * <p>
     * 需要验证旧密码才能修改新密码，防止被盗号。
     * 新密码会用BCrypt加密存储。
     * </p>
     *
     * @param id  管理员ID
     * @param dto 密码修改参数，包含旧密码和新密码
     */
    void updatePassword(Long id, AdminPasswordUpdateDTO dto);

    /**
     * 重置管理员密码
     * <p>
     * 管理员忘记密码时，由其他有权限的管理员把密码重置为默认密码（123456）。
     * 不需要验证旧密码，重置后管理员可以用默认密码登录再自行修改。
     * 默认密码会用BCrypt加密存储。
     * </p>
     *
     * @param id 管理员ID
     */
    void resetPassword(Long id);

    /**
     * 修改管理员状态
     * <p>
     * 启用或禁用管理员账号，禁用后管理员无法登录系统。
     * 超级管理员（id=1）不能被禁用。
     * </p>
     *
     * @param id     管理员ID
     * @param status 状态：0禁用 1正常
     */
    void updateStatus(Long id, Integer status);

    /**
     * 获取当前登录管理员信息
     * <p>
     * 从SecurityUtils获取当前登录的管理员ID，查询详细信息返回。
     * 用于管理员登录后查看自己的信息。
     * </p>
     *
     * @return 当前管理员详细信息（包含角色列表）
     */
    AdminUserVO getCurrentAdminInfo();
}
