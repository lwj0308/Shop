package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.PromotionFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.promotion.dto.PromotionCreateDTO;
import com.shop.model.promotion.vo.PromotionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 满减活动管理控制器
 * <p>
 * 管理后台对满减活动的管理接口，包括创建平台活动、查看全平台满减活动、下架满减活动。
 * 所有接口都需要管理员拥有对应的权限才能访问（超级管理员自动放行）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/promotion")
@Tag(name = "满减活动管理", description = "管理后台对满减活动的管理接口")
@RequiredArgsConstructor
public class PromotionManageController {

    /** 满减活动服务Feign客户端，远程调用商家服务 */
    private final PromotionFeignClient promotionFeignClient;

    /**
     * 创建平台满减活动
     * <p>
     * 管理员创建平台活动（merchantId=0），全平台用户可享受。
     * 需要 promotion:create 权限。
     * </p>
     *
     * @param dto 满减活动参数
     * @return 满减活动ID
     */
    @Operation(summary = "创建平台满减活动", description = "管理员创建平台满减活动，全平台用户可享受")
    @PostMapping
    @RequirePermission("promotion:create")
    @OperationLog(module = "满减活动管理", type = OperationType.CREATE, description = "创建平台满减活动：#dto.name")
    public Result<Long> createPromotion(@RequestBody @Validated PromotionCreateDTO dto) {
        return promotionFeignClient.adminCreatePromotion(dto);
    }

    /**
     * 分页查询全平台满减活动
     * <p>
     * 管理员查看所有满减活动（含平台活动和商家活动），支持按状态筛选。
     * 需要 promotion:list 权限。
     * </p>
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   满减活动状态（可选）
     * @return 分页满减活动列表
     */
    @Operation(summary = "查询满减活动列表", description = "分页查询全平台满减活动，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("promotion:list")
    @OperationLog(module = "满减活动管理", type = OperationType.QUERY, description = "查询满减活动列表")
    public Result<PageResult<PromotionVO>> listPromotions(@RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize,
                                                          @RequestParam(required = false) Integer status) {
        return promotionFeignClient.adminGetPromotionList(pageNum, pageSize, status);
    }

    /**
     * 下架满减活动
     * <p>
     * 管理员下架任意满减活动（含商家活动），下架后用户不能再享受该活动的优惠。
     * 需要 promotion:offline 权限。
     * </p>
     *
     * @param promotionId 满减活动ID
     * @return 操作结果
     */
    @Operation(summary = "下架满减活动", description = "下架后用户不能再享受该活动的优惠")
    @PutMapping("/{promotionId}/offline")
    @RequirePermission("promotion:offline")
    @OperationLog(module = "满减活动管理", type = OperationType.UPDATE, description = "下架满减活动：#promotionId")
    public Result<Void> offlinePromotion(@PathVariable Long promotionId) {
        return promotionFeignClient.adminOfflinePromotion(promotionId);
    }
}
