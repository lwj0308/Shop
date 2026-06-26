package com.shop.merchant.controller;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.service.MerchantService;
import com.shop.merchant.service.PromotionService;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.promotion.dto.PromotionCalculateDTO;
import com.shop.model.promotion.dto.PromotionCreateDTO;
import com.shop.model.promotion.dto.PromotionQueryDTO;
import com.shop.model.promotion.vo.PromotionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 满减活动控制器
 * <p>
 * 提供满减活动的创建、修改、下架、查询等功能。
 * 包含三类接口：
 * 1. 商家端接口（/merchant/promotion/*）：商家管理自己的满减活动，需登录
 * 2. 管理端接口（/merchant/promotion/admin/*）：管理员管理所有满减活动，供 shop-admin 通过 Feign 调用
 * 3. 内部接口（/merchant/promotion/inner/*）：供 shop-order 通过 Feign 调用计算满减优惠，不鉴权
 * </p>
 */
@Tag(name = "满减活动管理", description = "满减活动的创建、修改、下架、查询、优惠计算")
@RestController
@RequestMapping("/merchant/promotion")
@RequiredArgsConstructor
public class MerchantPromotionController {

    /** 满减活动服务 */
    private final PromotionService promotionService;

    /** 商家服务，用于通过用户ID查找商家 */
    private final MerchantService merchantService;

    // ==================== 商家端接口 ====================

    /**
     * 创建满减活动
     */
    @Operation(summary = "商家-创建满减活动", description = "商家创建自己的满减活动")
    @PostMapping
    public Result<Long> createPromotion(@Validated @RequestBody PromotionCreateDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        Long promotionId = promotionService.createPromotion(merchantId, dto);
        return Result.success(promotionId);
    }

    /**
     * 修改满减活动（仅待生效状态可改）
     */
    @Operation(summary = "商家-修改满减活动", description = "修改满减活动规则，仅待生效状态可修改")
    @PutMapping("/{promotionId}")
    public Result<Void> updatePromotion(@PathVariable Long promotionId, @Validated @RequestBody PromotionCreateDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        promotionService.updatePromotion(merchantId, promotionId, dto);
        return Result.success(null);
    }

    /**
     * 下架满减活动
     */
    @Operation(summary = "商家-下架满减活动", description = "下架后不再生效，下单时不会计算该活动的优惠")
    @PutMapping("/{promotionId}/offline")
    public Result<Void> offlinePromotion(@PathVariable Long promotionId) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        promotionService.offlinePromotion(merchantId, promotionId);
        return Result.success(null);
    }

    /**
     * 查询商家自己的满减活动列表
     */
    @Operation(summary = "商家-满减活动列表", description = "查询当前商家的满减活动列表")
    @GetMapping("/list")
    public Result<PageResult<PromotionVO>> getPromotionList(PromotionQueryDTO query) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        PageResult<PromotionVO> list = promotionService.getPromotionList(merchantId, query);
        return Result.success(list);
    }

    /**
     * 查询满减活动详情
     */
    @Operation(summary = "商家-满减活动详情", description = "查询指定满减活动的详细信息")
    @GetMapping("/{promotionId}")
    public Result<PromotionVO> getPromotionDetail(@PathVariable Long promotionId) {
        PromotionVO vo = promotionService.getPromotionDetail(promotionId);
        return Result.success(vo);
    }

    // ==================== 管理端接口（供 shop-admin 通过 Feign 调用） ====================

    /**
     * 管理端创建平台满减活动
     * <p>merchantId=0 表示平台活动</p>
     */
    @Operation(summary = "管理端-创建平台满减活动", description = "管理员创建平台满减活动")
    @PostMapping("/admin/create")
    public Result<Long> adminCreatePromotion(@Validated @RequestBody PromotionCreateDTO dto) {
        Long promotionId = promotionService.createPromotion(0L, dto);
        return Result.success(promotionId);
    }

    /**
     * 管理端查询所有满减活动（含平台活动和商家活动）
     */
    @Operation(summary = "管理端-满减活动列表", description = "管理员查看全平台满减活动")
    @GetMapping("/admin/list")
    public Result<PageResult<PromotionVO>> adminGetPromotionList(PromotionQueryDTO query) {
        PageResult<PromotionVO> list = promotionService.getPromotionList(null, query);
        return Result.success(list);
    }

    /**
     * 管理端下架满减活动
     */
    @Operation(summary = "管理端-下架满减活动", description = "管理员下架任意满减活动")
    @PutMapping("/admin/{promotionId}/offline")
    public Result<Void> adminOfflinePromotion(@PathVariable Long promotionId) {
        promotionService.offlinePromotion(0L, promotionId);
        return Result.success(null);
    }

    // ==================== 内部接口（供其他微服务通过 Feign 调用，不鉴权） ====================

    /**
     * 计算满减优惠金额（内部接口）
     * <p>shop-order 下单时通过 Feign 调用此接口，传入订单金额和商家ID，返回满减优惠金额</p>
     */
    @Operation(summary = "内部-计算满减优惠", description = "根据订单金额计算满减优惠金额，返回最大优惠额")
    @PostMapping("/inner/calculate")
    public Result<BigDecimal> calculatePromotion(@RequestBody PromotionCalculateDTO dto) {
        BigDecimal discount = promotionService.calculatePromotion(dto);
        return Result.success(discount);
    }

    // ==================== 私有方法 ====================

    /**
     * 通过用户ID获取商家ID
     */
    private Long getMerchantId(Long userId) {
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        return merchant != null ? merchant.getId() : null;
    }
}
