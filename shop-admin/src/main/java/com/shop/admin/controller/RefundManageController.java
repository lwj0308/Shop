package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.RefundFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.order.dto.RefundAuditDTO;
import com.shop.model.order.vo.RefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 退款管理控制器
 * <p>
 * 管理后台对退款的管理接口，包括退款列表查询、退款审核。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/refund")
@Tag(name = "退款管理", description = "管理后台对退款的管理接口")
@RequiredArgsConstructor
public class RefundManageController {

    /** 退款服务Feign客户端，远程调用订单服务中的退款模块 */
    private final RefundFeignClient refundFeignClient;

    /**
     * 分页查询退款列表
     * <p>
     * 管理员查看所有退款申请，支持按状态筛选。
     * 需要 refund:list 权限。
     * </p>
     *
     * @param page   页码
     * @param size   每页条数
     * @param status 退款状态（可选）
     * @return 分页退款列表
     */
    @Operation(summary = "查询退款列表", description = "分页查询退款列表，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("refund:list")
    @OperationLog(module = "退款管理", type = OperationType.QUERY, description = "查询退款列表")
    public Result<PageResult<RefundVO>> listRefunds(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(required = false) Integer status) {
        return refundFeignClient.listRefunds(page, size, status);
    }

    /**
     * 审核退款申请
     * <p>
     * 管理员审核退款申请，可以同意或拒绝。
     * 同意后系统会自动发起退款，拒绝时需要填写拒绝原因。
     * 需要 refund:audit 权限。
     * </p>
     *
     * @param dto 审核参数
     * @return 操作结果
     */
    @Operation(summary = "审核退款", description = "审核退款申请，同意或拒绝")
    @PutMapping("/audit")
    @RequirePermission("refund:audit")
    @OperationLog(module = "退款管理", type = OperationType.UPDATE, description = "审核退款：#dto.refundId")
    public Result<Void> auditRefund(@RequestBody @Validated RefundAuditDTO dto) {
        return refundFeignClient.auditRefund(dto);
    }
}
