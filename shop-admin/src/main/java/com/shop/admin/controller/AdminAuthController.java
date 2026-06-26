package com.shop.admin.controller;

import com.shop.admin.service.AdminAuthService;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminLoginDTO;
import com.shop.model.admin.vo.AdminLoginVO;
import com.shop.model.admin.vo.CaptchaVO;
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
 * 管理员认证控制器
 * <p>
 * 处理管理员登录、登出、获取验证码等认证相关的接口。
 * 验证码和登录接口不需要登录就能访问（在SaTokenConfig中配置了排除）；
 * 登出接口需要登录后才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
@Tag(name = "管理员认证", description = "管理员登录、登出、验证码接口")
public class AdminAuthController {

    /** 管理员认证服务，处理验证码、登录、登出等业务逻辑 */
    private final AdminAuthService adminAuthService;

    /**
     * 获取验证码
     * <p>
     * 前端调用此接口获取验证码图片，返回captchaKey和base64图片。
     * 前端把图片显示出来让用户输入，登录时把captchaKey和用户输入的验证码一起提交。
     * 验证码5分钟内有效，用完即失效。
     * </p>
     *
     * @return 验证码数据，包含captchaKey和base64图片
     */
    @PostMapping("/captcha")
    @Operation(summary = "获取验证码", description = "生成图形验证码，5分钟内有效，登录时需要提交验证码")
    public Result<CaptchaVO> getCaptcha() {
        CaptchaVO captchaVO = adminAuthService.getCaptcha();
        return Result.success("获取验证码成功", captchaVO);
    }

    /**
     * 管理员登录
     * <p>
     * 管理员使用用户名+密码+验证码登录后台系统。
     * 登录成功返回Token和基本信息（权限、角色等）。
     * 连续5次密码错误会锁定账号30分钟，防止暴力破解。
     * </p>
     *
     * @param dto 登录请求参数，包含用户名、密码、验证码key和验证码
     * @return 登录响应数据，包含Token、管理员信息、权限和角色
     */
    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "用户名+密码+验证码登录，连续5次密码错误锁定30分钟")
    public Result<AdminLoginVO> login(@Validated @RequestBody AdminLoginDTO dto) {
        AdminLoginVO loginVO = adminAuthService.login(dto);
        return Result.success("登录成功", loginVO);
    }

    /**
     * 管理员登出
     * <p>
     * 使当前Token失效，退出后需要重新登录才能访问需要登录的接口。
     * Sa-Token会自动清除服务端的Token记录，确保Token无法再被使用。
     * </p>
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    @Operation(summary = "管理员登出", description = "使当前Token失效，退出后需重新登录")
    public Result<Void> logout() {
        adminAuthService.logout();
        return Result.success("退出成功", null);
    }
}
