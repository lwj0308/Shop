package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.UserFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * C端用户管理控制器
 * <p>
 * 管理后台对C端用户的管理接口，包括用户列表查询、详情查询、禁用、启用。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/user")
@Tag(name = "C端用户管理", description = "管理后台对C端用户的管理接口")
@RequiredArgsConstructor
public class UserManageController {

    /** 用户服务Feign客户端，远程调用用户服务 */
    private final UserFeignClient userFeignClient;

    /**
     * 分页查询C端用户列表
     * <p>
     * 管理员查看所有C端用户，支持按状态和关键词筛选。
     * 需要 user:list 权限。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  用户状态（可选）
     * @param keyword 搜索关键词（可选）
     * @return 分页用户列表
     */
    @Operation(summary = "查询用户列表", description = "分页查询C端用户列表，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("user:list")
    @OperationLog(module = "用户管理", type = OperationType.QUERY, description = "查询C端用户列表")
    public Result<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String keyword) {
        return userFeignClient.listUsers(page, size, status, keyword);
    }

    /**
     * 根据用户ID查询用户详情
     * <p>
     * 管理员查看某个C端用户的详细信息。
     * 需要 user:detail 权限。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @Operation(summary = "查询用户详情", description = "根据ID查询C端用户详细信息")
    @GetMapping("/{userId}")
    @RequirePermission("user:detail")
    @OperationLog(module = "用户管理", type = OperationType.QUERY, description = "查询C端用户详情：#userId")
    public Result<UserVO> getUserById(@PathVariable Long userId) {
        return userFeignClient.getUserById(userId);
    }

    /**
     * 禁用用户
     * <p>
     * 管理员封禁违规用户，禁用后用户无法登录和下单。
     * 需要 user:disable 权限。
     * </p>
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @Operation(summary = "禁用用户", description = "封禁C端用户，禁用后无法登录")
    @PutMapping("/{userId}/disable")
    @RequirePermission("user:disable")
    @OperationLog(module = "用户管理", type = OperationType.UPDATE, description = "禁用C端用户：#userId")
    public Result<Void> disableUser(@PathVariable Long userId) {
        return userFeignClient.disableUser(userId);
    }

    /**
     * 启用用户
     * <p>
     * 管理员解封用户，启用后用户可以正常使用。
     * 需要 user:enable 权限。
     * </p>
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @Operation(summary = "启用用户", description = "解封C端用户，启用后可正常使用")
    @PutMapping("/{userId}/enable")
    @RequirePermission("user:enable")
    @OperationLog(module = "用户管理", type = OperationType.UPDATE, description = "启用C端用户：#userId")
    public Result<Void> enableUser(@PathVariable Long userId) {
        return userFeignClient.enableUser(userId);
    }
}
