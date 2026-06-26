package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.MerchantFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.merchant.dto.MerchantAuditDTO;
import com.shop.model.merchant.vo.MerchantVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 商家管理控制器
 * <p>
 * 管理后台对商家的管理接口，包括商家列表查询、详情查询、审核、禁用、启用。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/merchant")
@Tag(name = "商家管理", description = "管理后台对商家的管理接口")
@RequiredArgsConstructor
public class MerchantManageController {

    /** 商家服务Feign客户端，远程调用商家服务 */
    private final MerchantFeignClient merchantFeignClient;

    /**
     * 分页查询商家列表
     * <p>
     * 管理员查看所有商家，支持按状态和关键词筛选。
     * 需要 merchant:list 权限。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  商家状态（可选）
     * @param keyword 搜索关键词（可选）
     * @return 分页商家列表
     */
    @Operation(summary = "查询商家列表", description = "分页查询商家列表，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("merchant:list")
    @OperationLog(module = "商家管理", type = OperationType.QUERY, description = "查询商家列表")
    public Result<PageResult<MerchantVO>> listMerchants(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) Integer status,
                                                         @RequestParam(required = false) String keyword) {
        return merchantFeignClient.listMerchants(page, size, status, keyword);
    }

    /**
     * 根据商家ID查询商家详情
     * <p>
     * 管理员查看某个商家的详细信息，用于审核或管理操作。
     * 需要 merchant:detail 权限。
     * </p>
     *
     * @param id 商家ID
     * @return 商家信息
     */
    @Operation(summary = "查询商家详情", description = "根据ID查询商家详细信息")
    @GetMapping("/{id}")
    @RequirePermission("merchant:detail")
    @OperationLog(module = "商家管理", type = OperationType.QUERY, description = "查询商家详情：#id")
    public Result<MerchantVO> getMerchantById(@PathVariable Long id) {
        return merchantFeignClient.getMerchantById(id);
    }

    /**
     * 审核商家入驻申请
     * <p>
     * 管理员审核商家提交的入驻申请，可以同意或拒绝。
     * 需要 merchant:audit 权限。
     * </p>
     *
     * @param dto 审核参数
     * @return 操作结果
     */
    @Operation(summary = "审核商家", description = "审核商家入驻申请，同意或拒绝")
    @PutMapping("/audit")
    @RequirePermission("merchant:audit")
    @OperationLog(module = "商家管理", type = OperationType.UPDATE, description = "审核商家：#dto.merchantId")
    public Result<Void> auditMerchant(@RequestBody @Validated MerchantAuditDTO dto) {
        return merchantFeignClient.auditMerchant(dto);
    }

    /**
     * 禁用商家
     * <p>
     * 管理员封禁违规商家，禁用后商家无法经营。
     * 需要 merchant:disable 权限。
     * </p>
     *
     * @param id 商家ID
     * @return 操作结果
     */
    @Operation(summary = "禁用商家", description = "封禁违规商家，禁用后无法经营")
    @PutMapping("/{id}/disable")
    @RequirePermission("merchant:disable")
    @OperationLog(module = "商家管理", type = OperationType.UPDATE, description = "禁用商家：#id")
    public Result<Void> disableMerchant(@PathVariable Long id) {
        return merchantFeignClient.disableMerchant(id);
    }

    /**
     * 启用商家
     * <p>
     * 管理员解封商家，启用后商家可以正常经营。
     * 需要 merchant:enable 权限。
     * </p>
     *
     * @param id 商家ID
     * @return 操作结果
     */
    @Operation(summary = "启用商家", description = "解封商家，启用后可正常经营")
    @PutMapping("/{id}/enable")
    @RequirePermission("merchant:enable")
    @OperationLog(module = "商家管理", type = OperationType.UPDATE, description = "启用商家：#id")
    public Result<Void> enableMerchant(@PathVariable Long id) {
        return merchantFeignClient.enableMerchant(id);
    }
}
