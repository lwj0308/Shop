package com.shop.merchant.controller;

import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.service.MerchantService;
import com.shop.model.merchant.dto.MerchantChangePasswordDTO;
import com.shop.model.merchant.dto.MerchantLoginDTO;
import com.shop.model.merchant.vo.MerchantVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商家认证控制器
 * <p>
 * 提供商家登录、登出、修改密码等认证接口。
 * 商家登录后获得Token，后续请求带上Token就能识别身份。
 * 登录逻辑在MerchantService中实现，Controller只负责接收参数和返回结果。
 * </p>
 */
@Tag(name = "商家认证", description = "商家登录、登出、修改密码等认证接口")
@RestController
@RequestMapping("/merchant/auth")
@RequiredArgsConstructor
public class MerchantAuthController {

    /** 商家服务，处理登录、修改密码等认证相关的业务逻辑 */
    private final MerchantService merchantService;

    /**
     * 商家登录
     * <p>
     * 商家使用手机号和密码登录，登录成功后返回Token。
     * Token要放在请求头Authorization中，后续请求会自动识别商家身份。
     * 连续5次密码错误会锁定30分钟，防止暴力破解。
     * </p>
     *
     * @param loginDTO 登录参数，包含手机号和密码
     * @return 登录结果，包含Token和商家ID
     */
    @Operation(summary = "商家登录", description = "使用手机号和密码登录，返回Token")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody MerchantLoginDTO loginDTO) {
        Map<String, Object> result = merchantService.login(loginDTO);
        return Result.success("登录成功", result);
    }

    /**
     * 商家登出
     * <p>
     * 商家退出登录，Token失效。
     * </p>
     *
     * @return 操作结果
     */
    @Operation(summary = "商家登出", description = "退出登录，Token失效")
    @PostMapping("/logout")
    public Result<Void> logout() {
        SecurityUtils.requireLogin();
        cn.dev33.satoken.stp.StpUtil.logout();
        return Result.success("登出成功", null);
    }

    /**
     * 商家修改密码
     * <p>
     * 商家修改登录密码，需要输入正确的旧密码才能修改新密码。
     * 修改密码后不会自动退出登录，可以继续使用当前Token。
     * </p>
     *
     * @param dto 修改密码参数，包含旧密码和新密码
     * @return 操作结果
     */
    @Operation(summary = "修改密码", description = "商家修改登录密码，需要验证旧密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody MerchantChangePasswordDTO dto) {
        // 从UserContext获取当前用户ID，再查找关联的商家
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        merchantService.changePassword(merchant.getId(), dto);
        return Result.success("密码修改成功", null);
    }
}
