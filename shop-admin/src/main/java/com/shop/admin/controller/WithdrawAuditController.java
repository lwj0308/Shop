package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.MerchantFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.merchant.dto.WithdrawAuditDTO;
import com.shop.model.merchant.vo.WithdrawOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 提现审核控制器
 * <p>
 * 管理后台对商家提现申请的审核接口。
 * 管理员可以查看所有商家的提现申请，并通过或拒绝。
 * </p>
 * <p>
 * 审核流程：
 * 1. 商家在商家后台申请提现 → 金额从balance转入frozen_amount（冻结）
 * 2. 管理员在此页面审核：
 *    - 通过 → frozen_amount扣减（钱已打款给商家）
 *    - 拒绝 → frozen_amount转回balance（解冻给商家）
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/withdraw")
@Tag(name = "提现审核", description = "管理后台对商家提现申请的审核接口")
@RequiredArgsConstructor
public class WithdrawAuditController {

    /** 商家服务Feign客户端，远程调用商家服务 */
    private final MerchantFeignClient merchantFeignClient;

    /**
     * 分页查询提现申请列表
     * <p>管理员查看全平台的提现申请，支持按状态筛选</p>
     *
     * @param page   页码
     * @param size   每页条数
     * @param status 提现状态（可选）：0待审核 1已通过 2已拒绝 3已打款
     * @return 分页提现申请列表（含商家名称）
     */
    @RequirePermission("merchant:withdraw:list")
    @OperationLog(module = "提现审核", type = OperationType.QUERY, description = "查询提现申请列表")
    @Operation(summary = "查询提现申请列表", description = "分页查询全平台提现申请")
    @GetMapping("/list")
    public Result<PageResult<WithdrawOrderVO>> listWithdraws(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "提现状态：0待审核 1已通过 2已拒绝 3已打款")
            @RequestParam(required = false) Integer status) {
        return merchantFeignClient.adminGetWithdrawList(page, size, status);
    }

    /**
     * 审核提现申请
     * <p>
     * 管理员审核商家的提现申请：
     * - 审核通过(status=1)：冻结金额扣减（钱已打款）
     * - 审核拒绝(status=2)：冻结金额转回可用余额（解冻）
     * </p>
     *
     * @param dto 审核参数（含提现ID、审核结果、备注）
     * @return 操作结果
     */
    @RequirePermission("merchant:withdraw:audit")
    @OperationLog(module = "提现审核", type = OperationType.UPDATE, description = "审核提现申请")
    @Operation(summary = "审核提现申请", description = "通过或拒绝商家提现申请")
    @PutMapping("/audit")
    public Result<Void> auditWithdraw(@Validated @RequestBody WithdrawAuditDTO dto) {
        return merchantFeignClient.auditWithdraw(dto);
    }
}
