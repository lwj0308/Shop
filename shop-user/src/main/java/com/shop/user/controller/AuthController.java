package com.shop.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.result.Result;
import com.shop.model.user.dto.TokenRefreshDTO;
import com.shop.model.user.dto.UserLoginDTO;
import com.shop.model.user.dto.UserRegisterDTO;
import com.shop.model.user.vo.UserLoginVO;
import com.shop.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * <p>
 * 处理注册、登录、退出登录、刷新Token等认证相关的接口。
 * 注册、登录、刷新Token接口不需要登录就能访问；
 * 退出登录接口需要登录后才能访问（由SaTokenConfig配置放行）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "注册、登录、退出登录、刷新Token等接口")
public class AuthController {

    /** 用户服务 */
    private final UserService userService;

    /**
     * 用户注册
     * <p>
     * 前端传入手机号、密码、确认密码，注册成功后需要调用登录接口获取Token。
     * 密码要求8-20位，必须包含字母和数字。
     * </p>
     *
     * @param dto 注册请求参数
     * @return 操作结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用手机号和密码注册新用户，密码8-20位且必须包含字母和数字")
    public Result<Void> register(@Validated @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success("注册成功", null);
    }

    /**
     * 用户登录
     * <p>
     * 登录成功返回双Token（AccessToken + RefreshToken）和用户基本信息。
     * AccessToken 2小时过期，RefreshToken 7天过期。
     * 连续5次密码错误会锁定30分钟。
     * </p>
     *
     * @param dto 登录请求参数
     * @return 登录响应（Token + 用户信息）
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "手机号+密码登录，返回双Token。连续5次错误锁定30分钟")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginDTO dto) {
        UserLoginVO loginVO = userService.login(dto);
        return Result.success("登录成功", loginVO);
    }

    /**
     * 退出登录
     * <p>
     * 使当前Token失效，退出后需要重新登录才能访问需要登录的接口。
     * Sa-Token会自动清除服务端的Token记录，确保Token无法再被使用。
     * </p>
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "使当前Token失效，退出后需重新登录")
    public Result<Void> logout() {
        StpUtil.logout();
        log.info("用户退出登录: userId={}", StpUtil.getLoginIdAsLong());
        return Result.success("退出成功", null);
    }

    /**
     * 刷新Token
     * <p>
     * 当AccessToken过期后，用RefreshToken换取新的AccessToken。
     * 如果RefreshToken也过期了，就需要重新登录。
     * </p>
     *
     * @param dto 刷新Token请求参数
     * @return 新的登录响应
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "用RefreshToken换取新的AccessToken")
    public Result<UserLoginVO> refresh(@Validated @RequestBody TokenRefreshDTO dto) {
        UserLoginVO loginVO = userService.refreshToken(dto);
        return Result.success("刷新成功", loginVO);
    }

    /**
     * 微信登录（预留接口）
     * <p>
     * 预留的微信登录接口，后续对接微信开放平台时实现。
     * 目前返回"功能开发中"提示。
     * </p>
     *
     * @return 操作结果
     */
    @PostMapping("/wx-login")
    @Operation(summary = "微信登录", description = "微信授权登录（预留接口，暂未实现）")
    public Result<Void> wxLogin() {
        return Result.fail("微信登录功能开发中");
    }
}
