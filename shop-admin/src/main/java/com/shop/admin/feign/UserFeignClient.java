package com.shop.admin.feign;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.admin.feign.fallback.UserFeignClientFallbackFactory;
import com.shop.model.user.vo.UserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务Feign客户端
 * <p>
 * 通过Feign远程调用用户服务，管理后台用来管理C端用户。
 * 使用fallbackFactory实现降级：当用户服务不可用时，走降级逻辑返回友好提示，
 * 不会因为用户服务挂了就导致管理后台也跟着挂。
 * </p>
 */
@FeignClient(name = "shop-user", path = "/user", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 分页查询C端用户列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有C端用户，支持按状态和关键词筛选。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  用户状态（可选）：0禁用 1正常
     * @param keyword 搜索关键词（可选）：按昵称或手机号搜索
     * @return 分页用户列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<UserVO>> listUsers(@RequestParam("page") int page,
                                         @RequestParam("size") int size,
                                         @RequestParam(value = "status", required = false) Integer status,
                                         @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 根据用户ID查询用户详情（管理后台专用）
     * <p>
     * 管理员查看某个C端用户的详细信息。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/admin/{userId}")
    Result<UserVO> getUserById(@PathVariable("userId") Long userId);

    /**
     * 禁用用户
     * <p>
     * 管理员封禁违规用户，禁用后用户无法登录和下单。
     * </p>
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/admin/{userId}/disable")
    Result<Void> disableUser(@PathVariable("userId") Long userId);

    /**
     * 启用用户
     * <p>
     * 管理员解封用户，启用后用户可以正常使用。
     * </p>
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/admin/{userId}/enable")
    Result<Void> enableUser(@PathVariable("userId") Long userId);
}
