package com.shop.user.controller;

import cn.hutool.core.util.DesensitizedUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.user.dto.UserPasswordDTO;
import com.shop.model.user.dto.UserUpdateDTO;
import com.shop.model.user.entity.User;
import com.shop.model.user.vo.UserVO;
import com.shop.user.mapper.UserMapper;
import com.shop.user.service.UserService;
import com.shop.user.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户信息控制器
 * <p>
 * 处理获取用户信息、修改个人信息、修改密码等接口。
 * 这些接口都需要登录后才能访问（由SaTokenConfig拦截器校验）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户信息", description = "获取/修改用户信息、修改密码")
public class UserController {

    /** 用户服务 */
    private final UserService userService;

    /** 用户Mapper，管理后台直接查询用户表用 */
    private final UserMapper userMapper;

    /**
     * 获取当前登录用户信息
     * <p>
     * 从Sa-Token中获取当前登录用户的ID，查询用户信息返回。
     * 手机号会做脱敏处理（138****1234）。
     * </p>
     *
     * @return 用户信息（不含密码，手机号脱敏）
     */
    @GetMapping("/info")
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的基本信息，手机号脱敏")
    public Result<UserVO> getUserInfo() {
        Long userId = UserContext.getUserId();
        UserVO userVO = userService.getUserInfo(userId);
        return Result.success(userVO);
    }

    /**
     * 修改个人信息
     * <p>
     * 目前只支持修改昵称和头像，手机号和密码需要走专门的接口。
     * </p>
     *
     * @param dto 修改信息请求参数（昵称、头像）
     * @return 操作结果
     */
    @PutMapping("/info")
    @Operation(summary = "修改个人信息", description = "修改昵称和头像")
    public Result<Void> updateUserInfo(@Validated @RequestBody UserUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        userService.updateUserInfo(userId, dto);
        return Result.success("修改成功", null);
    }

    /**
     * 修改密码
     * <p>
     * 需要输入旧密码验证身份，修改成功后需要重新登录。
     * 新密码要求8-20位，必须包含字母和数字。
     * </p>
     *
     * @param dto 修改密码请求参数（旧密码、新密码）
     * @return 操作结果
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "验证旧密码后修改为新密码，修改后需重新登录。新密码8-20位且必须包含字母和数字")
    public Result<Void> updatePassword(@Validated @RequestBody UserPasswordDTO dto) {
        Long userId = UserContext.getUserId();
        userService.updatePassword(userId, dto);
        return Result.success("密码修改成功，请重新登录", null);
    }

    // ========== 管理后台专用接口（供 shop-admin 通过 Feign 调用） ==========

    /**
     * 分页查询C端用户列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有C端用户，支持按状态和关键词筛选。
     * 关键词会同时匹配昵称和手机号。手机号返回时做脱敏处理。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  用户状态（可选）：0禁用 1正常
     * @param keyword 搜索关键词（可选）：按昵称或手机号搜索
     * @return 分页用户列表
     */
    @GetMapping("/admin/list")
    public Result<PageResult<UserVO>> adminListUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        // 构造分页对象，MyBatis-Plus会自动拼接LIMIT语句
        Page<User> pageParam = new Page<>(page, size);
        // 构造查询条件
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 按状态精确筛选
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        // 按关键词模糊搜索昵称或手机号
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword));
        }
        // 按创建时间倒序，新注册的用户排前面
        wrapper.orderByDesc(User::getCreateTime);
        // 执行分页查询
        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        // 把User实体列表转成UserVO列表（手机号脱敏）
        List<UserVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        // 用PageResult.from把MyBatis-Plus的分页结果转成项目的分页结果
        return Result.success(PageResult.from(result, voList));
    }

    /**
     * 根据用户ID查询用户详情（管理后台专用）
     * <p>
     * 直接复用UserService的getUserInfo方法，手机号会做脱敏处理。
     * 用户不存在时会抛出BusinessException，由全局异常处理器统一处理。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/admin/{userId}")
    public Result<UserVO> adminGetUserById(@PathVariable Long userId) {
        UserVO userVO = userService.getUserInfo(userId);
        return Result.success(userVO);
    }

    /**
     * 禁用用户（管理后台专用）
     * <p>
     * 将用户状态改为0（禁用），禁用后用户无法登录和下单。
     * </p>
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/admin/{userId}/disable")
    public Result<Void> adminDisableUser(@PathVariable Long userId) {
        // 先检查用户是否存在，不存在就抛异常
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 只更新状态字段，其他字段不变
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setStatus(0);
        userMapper.updateById(updateUser);
        log.info("管理后台禁用用户: userId={}", userId);
        return Result.success();
    }

    /**
     * 启用用户（管理后台专用）
     * <p>
     * 将用户状态改为1（正常），启用后用户可以正常使用。
     * </p>
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/admin/{userId}/enable")
    public Result<Void> adminEnableUser(@PathVariable Long userId) {
        // 先检查用户是否存在，不存在就抛异常
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 只更新状态字段，其他字段不变
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setStatus(1);
        userMapper.updateById(updateUser);
        log.info("管理后台启用用户: userId={}", userId);
        return Result.success();
    }

    /**
     * User实体转UserVO（手机号脱敏）
     * <p>
     * 把数据库查出来的User对象转成返回给前端的UserVO对象。
     * 手机号做脱敏处理，中间4位用*号替代，比如138****1234。
     * 这个方法和UserServiceImpl里的convertToVO逻辑一样，
     * 因为那边是private方法没法直接调用，所以这里复制了一份。
     * </p>
     *
     * @param user 用户实体
     * @return 用户VO（手机号脱敏后）
     */
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        // 手机号脱敏：13812345678 → 138****5678
        vo.setPhone(DesensitizedUtil.mobilePhone(user.getPhone()));
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
