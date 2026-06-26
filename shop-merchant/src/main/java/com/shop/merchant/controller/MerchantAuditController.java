package com.shop.merchant.controller;

import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.service.MerchantService;
import com.shop.model.merchant.dto.MerchantAuditDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家审核控制器（管理员功能）
 * <p>
 * 提供商家审核接口，只有管理员才能调用。
 * 审核通过后商家才能正常经营，系统会自动创建默认店铺。
 * 通过SecurityUtils校验管理员角色，非管理员无法调用。
 * </p>
 */
@Tag(name = "商家审核", description = "管理员审核商家入驻申请")
@RestController
@RequestMapping("/merchant/audit")
@RequiredArgsConstructor
public class MerchantAuditController {

    /** 商家服务，处理审核相关的业务逻辑 */
    private final MerchantService merchantService;

    /**
     * 审核商家
     * <p>
     * 管理员审核商家的入驻申请，可以通过(1)或拒绝(2)。
     * 审核通过时系统会自动创建一个默认店铺。
     * 只有拥有admin角色的用户才能调用此接口。
     * </p>
     *
     * @param auditDTO 审核参数，包含商家ID、审核状态、审核备注
     * @return 操作结果
     */
    @Operation(summary = "审核商家", description = "管理员审核商家入驻申请，通过后自动创建店铺")
    @PutMapping
    public Result<Void> audit(@Validated @RequestBody MerchantAuditDTO auditDTO) {
        // 校验管理员权限：只有admin角色才能审核商家
        SecurityUtils.checkRole("admin");
        merchantService.audit(auditDTO);
        return Result.success(null);
    }
}
