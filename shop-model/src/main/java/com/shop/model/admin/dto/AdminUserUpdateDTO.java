package com.shop.model.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 编辑管理员请求参数
 * <p>
 * 修改管理员信息时提交的数据，和新增不同，这里不能修改用户名和密码。
 * 如果要改密码，需要走专门的修改密码接口（AdminPasswordUpdateDTO）。
 * </p>
 */
@Data
public class AdminUserUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 昵称，管理员在系统中显示的名称，最长50个字符 */
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    /** 邮箱，管理员的联系邮箱，需要符合邮箱格式 */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 手机号，管理员的联系电话，最长20个字符 */
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;

    /** 部门ID，管理员所属的部门 */
    private Long deptId;

    /** 状态：0禁用 1正常，用来启用或禁用管理员账号 */
    private Integer status;

    /** 角色ID列表，重新分配的角色，会覆盖之前的角色 */
    private List<Long> roleIds;
}
