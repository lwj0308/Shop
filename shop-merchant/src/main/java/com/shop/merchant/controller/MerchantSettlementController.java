package com.shop.merchant.controller;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.service.MerchantService;
import com.shop.merchant.service.MerchantSettlementService;
import com.shop.model.merchant.dto.MerchantSettlementDTO;
import com.shop.model.merchant.dto.WithdrawApplyDTO;
import com.shop.model.merchant.dto.WithdrawAuditDTO;
import com.shop.model.merchant.vo.MerchantSettlementVO;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.merchant.vo.SettlementRecordVO;
import com.shop.model.merchant.vo.WithdrawOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家结算管理控制器
 * <p>
 * 提供结算账户管理、结算流水查询、提现申请等功能。
 * 所有接口都需要商家登录，通过 SecurityUtils 获取当前登录用户ID，
 * 再查出对应的商家ID，商家只能管理自己的结算信息。
 * </p>
 */
@Tag(name = "结算管理", description = "商家结算账户、结算流水、提现申请管理")
@RestController
@RequestMapping("/merchant/settlement")
@RequiredArgsConstructor
public class MerchantSettlementController {

    /** 结算服务 */
    private final MerchantSettlementService settlementService;

    /** 商家服务，用于通过用户ID查找商家 */
    private final MerchantService merchantService;

    // ==================== 结算账户管理 ====================

    /**
     * 添加结算账户
     */
    @Operation(summary = "添加结算账户", description = "商家首次配置银行账户信息")
    @PostMapping
    public Result<Void> addSettlement(@Validated @RequestBody MerchantSettlementDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        merchantService.checkMerchantActive(merchantId);
        settlementService.addSettlement(merchantId, dto);
        return Result.success(null);
    }

    /**
     * 更新结算账户
     */
    @Operation(summary = "更新结算账户", description = "商家修改银行账户信息")
    @PutMapping
    public Result<Void> updateSettlement(@Validated @RequestBody MerchantSettlementDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        merchantService.checkMerchantActive(merchantId);
        settlementService.updateSettlement(merchantId, dto);
        return Result.success(null);
    }

    /**
     * 获取结算账户（含余额信息）
     */
    @Operation(summary = "获取结算账户", description = "查询商家的银行账户信息和余额")
    @GetMapping
    public Result<MerchantSettlementVO> getSettlement() {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        MerchantSettlementVO settlement = settlementService.getSettlement(merchantId);
        return Result.success(settlement);
    }

    // ==================== 结算流水查询 ====================

    /**
     * 结算流水列表
     * <p>商家查看自己店铺的结算记录，每条记录对应一笔订单的结算信息</p>
     *
     * @param status    结算状态：0-待结算 1-已结算 2-已退款，不传查全部
     * @param pageRequest 分页参数
     * @return 分页结算流水列表
     */
    @Operation(summary = "结算流水列表", description = "分页查询商家的结算记录")
    @GetMapping("/records")
    public Result<PageResult<SettlementRecordVO>> getSettlementRecords(
            @Parameter(description = "结算状态：0待结算 1已结算 2已退款") @RequestParam(required = false) Integer status,
            PageRequest pageRequest) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        PageResult<SettlementRecordVO> records = settlementService.getSettlementRecords(merchantId, status, pageRequest);
        return Result.success(records);
    }

    // ==================== 提现申请 ====================

    /**
     * 申请提现
     * <p>商家发起提现，金额从可用余额转入冻结金额，等待管理员审核</p>
     *
     * @param dto 提现参数（含提现金额）
     * @return 操作结果
     */
    @Operation(summary = "申请提现", description = "商家发起提现申请")
    @PostMapping("/withdraw")
    public Result<Void> applyWithdraw(@Validated @RequestBody WithdrawApplyDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        merchantService.checkMerchantActive(merchantId);
        settlementService.applyWithdraw(merchantId, dto);
        return Result.success("提现申请已提交", null);
    }

    /**
     * 提现申请列表
     * <p>商家查看自己的提现申请历史</p>
     *
     * @param status    提现状态：0-待审核 1-已通过 2-已拒绝 3-已打款，不传查全部
     * @param pageRequest 分页参数
     * @return 分页提现申请列表
     */
    @Operation(summary = "提现申请列表", description = "分页查询商家的提现申请记录")
    @GetMapping("/withdraw/list")
    public Result<PageResult<WithdrawOrderVO>> getWithdrawList(
            @Parameter(description = "提现状态：0待审核 1已通过 2已拒绝 3已打款") @RequestParam(required = false) Integer status,
            PageRequest pageRequest) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        PageResult<WithdrawOrderVO> list = settlementService.getWithdrawList(merchantId, status, pageRequest);
        return Result.success(list);
    }

    // ==================== 私有方法 ====================

    /**
     * 通过用户ID获取商家ID
     */
    private Long getMerchantId(Long userId) {
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        return merchant != null ? merchant.getId() : null;
    }

    // ==================== 内部接口（供其他微服务通过Feign调用，不鉴权） ====================

    /**
     * 订单结算：生成结算记录并增加商家余额
     * <p>
     * 用户确认收货后，订单服务通过Feign调用此接口。
     * 此接口在SaTokenConfig白名单中，不需要登录鉴权。
     * 幂等设计：同一订单号重复调用不会重复结算。
     * </p>
     *
     * @param merchantId  商家ID
     * @param orderNo     订单号
     * @param orderAmount 订单金额（元）
     * @return 操作结果
     */
    @Operation(summary = "订单结算（内部接口）", description = "订单确认收货后生成结算记录并增加商家余额")
    @PostMapping("/inner/settle")
    public Result<Void> settleOrder(
            @Parameter(description = "商家ID") @RequestParam Long merchantId,
            @Parameter(description = "订单号") @RequestParam String orderNo,
            @Parameter(description = "订单金额（元）") @RequestParam java.math.BigDecimal orderAmount) {
        settlementService.settleOrder(merchantId, orderNo, orderAmount);
        return Result.success(null);
    }

    // ==================== 管理端接口（供 shop-admin 通过 Feign 调用） ====================

    /**
     * 管理端查询所有提现申请
     * <p>管理员查看全平台的提现申请，支持按状态筛选</p>
     *
     * @param status      提现状态：0-待审核 1-已通过 2-已拒绝 3-已打款，不传查全部
     * @param pageRequest 分页参数
     * @return 分页提现申请列表（含商家名称）
     */
    @Operation(summary = "管理端-提现申请列表", description = "管理员查看全平台提现申请")
    @GetMapping("/admin/withdraw/list")
    public Result<PageResult<WithdrawOrderVO>> adminGetWithdrawList(
            @Parameter(description = "提现状态：0待审核 1已通过 2已拒绝 3已打款") @RequestParam(required = false) Integer status,
            PageRequest pageRequest) {
        PageResult<WithdrawOrderVO> list = settlementService.adminGetWithdrawList(status, pageRequest);
        return Result.success(list);
    }

    /**
     * 管理端审核提现申请
     * <p>
     * 管理员审核商家的提现申请：
     * - 审核通过：冻结金额扣减（钱已打款）
     * - 审核拒绝：冻结金额转回可用余额（解冻）
     * </p>
     *
     * @param dto 审核参数（含提现ID、审核结果、备注）
     * @return 操作结果
     */
    @Operation(summary = "管理端-审核提现", description = "管理员审核商家提现申请")
    @PutMapping("/admin/withdraw/audit")
    public Result<Void> auditWithdraw(@Validated @RequestBody WithdrawAuditDTO dto) {
        settlementService.auditWithdraw(dto);
        return Result.success("审核成功", null);
    }
}
