package com.shop.merchant.controller;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.service.MerchantService;
import com.shop.merchant.service.SeckillActivityService;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.dto.SeckillQueryDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.vo.SeckillVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀活动控制器
 * <p>
 * 提供秒杀活动的创建、下架、查询等功能。
 * 包含三类接口：
 * 1. 商家端接口（/merchant/seckill/*）：商家管理自己的秒杀活动，需登录
 * 2. 管理端接口（/merchant/seckill/admin/*）：管理员管理所有秒杀活动，供 shop-admin 通过 Feign 调用
 * 3. 内部接口（/merchant/seckill/inner/*）：供 shop-order 通过 Feign 调用查询活动信息，不鉴权
 * </p>
 */
@Tag(name = "秒杀活动管理", description = "秒杀活动的创建、下架、查询")
@RestController
@RequestMapping("/merchant/seckill")
@RequiredArgsConstructor
public class MerchantSeckillController {

    /** 秒杀活动服务 */
    private final SeckillActivityService seckillActivityService;

    /** 商家服务，用于通过用户ID查找商家 */
    private final MerchantService merchantService;

    // ==================== 商家端接口 ====================

    /**
     * 创建秒杀活动
     */
    @Operation(summary = "商家-创建秒杀活动", description = "商家创建自己的秒杀活动，创建后会预热库存到 Redis")
    @PostMapping
    public Result<Long> createSeckillActivity(@Validated @RequestBody SeckillCreateDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        Long seckillId = seckillActivityService.createSeckillActivity(merchantId, dto);
        return Result.success(seckillId);
    }

    /**
     * 下架秒杀活动
     */
    @Operation(summary = "商家-下架秒杀活动", description = "下架后用户无法继续抢购，同时清理 Redis 库存缓存")
    @PutMapping("/{seckillId}/offline")
    public Result<Void> offlineSeckillActivity(@PathVariable Long seckillId) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        seckillActivityService.offlineSeckillActivity(merchantId, seckillId);
        return Result.success(null);
    }

    /**
     * 查询商家自己的秒杀活动列表
     */
    @Operation(summary = "商家-秒杀活动列表", description = "查询当前商家的秒杀活动列表")
    @GetMapping("/list")
    public Result<PageResult<SeckillVO>> getSeckillList(SeckillQueryDTO query) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        PageResult<SeckillVO> list = seckillActivityService.getSeckillList(merchantId, query);
        return Result.success(list);
    }

    /**
     * 查询秒杀活动详情
     */
    @Operation(summary = "商家-秒杀活动详情", description = "查询指定秒杀活动的详细信息")
    @GetMapping("/{seckillId}")
    public Result<SeckillVO> getSeckillDetail(@PathVariable Long seckillId) {
        SeckillVO vo = seckillActivityService.getSeckillDetail(seckillId);
        return Result.success(vo);
    }

    // ==================== 管理端接口（供 shop-admin 通过 Feign 调用） ====================

    /**
     * 管理端创建平台秒杀活动
     * <p>merchantId=0 表示平台活动</p>
     */
    @Operation(summary = "管理端-创建平台秒杀活动", description = "管理员创建平台秒杀活动")
    @PostMapping("/admin/create")
    public Result<Long> adminCreateSeckillActivity(@Validated @RequestBody SeckillCreateDTO dto) {
        Long seckillId = seckillActivityService.createSeckillActivity(0L, dto);
        return Result.success(seckillId);
    }

    /**
     * 管理端查询所有秒杀活动（含平台活动和商家活动）
     */
    @Operation(summary = "管理端-秒杀活动列表", description = "管理员查看全平台秒杀活动")
    @GetMapping("/admin/list")
    public Result<PageResult<SeckillVO>> adminGetSeckillList(SeckillQueryDTO query) {
        PageResult<SeckillVO> list = seckillActivityService.getSeckillList(null, query);
        return Result.success(list);
    }

    /**
     * 管理端下架秒杀活动
     */
    @Operation(summary = "管理端-下架秒杀活动", description = "管理员下架任意秒杀活动")
    @PutMapping("/admin/{seckillId}/offline")
    public Result<Void> adminOfflineSeckillActivity(@PathVariable Long seckillId) {
        seckillActivityService.offlineSeckillActivity(0L, seckillId);
        return Result.success(null);
    }

    // ==================== 用户端公开接口（不需要鉴权，供用户端查询秒杀活动） ====================

    /**
     * 用户端查询进行中的秒杀活动列表
     * <p>用户在秒杀列表页看到的活动，只返回进行中（status=1）的活动</p>
     */
    @Operation(summary = "用户端-查询秒杀活动列表", description = "查询所有进行中的秒杀活动，不需要登录")
    @GetMapping("/public/list")
    public Result<List<SeckillVO>> getPublicSeckillList() {
        // 查询所有进行中的秒杀活动（不限商家）
        SeckillQueryDTO query = new SeckillQueryDTO();
        query.setStatus(1); // 只查进行中的
        query.setPageNum(1);
        query.setPageSize(50); // 最多展示50个
        PageResult<SeckillVO> page = seckillActivityService.getSeckillList(null, query);
        return Result.success(page.getRecords());
    }

    /**
     * 用户端查询秒杀活动详情
     * <p>用户点击秒杀商品进入详情页时调用，不需要登录</p>
     */
    @Operation(summary = "用户端-查询秒杀活动详情", description = "根据ID查询秒杀活动详情，不需要登录")
    @GetMapping("/public/{seckillId}")
    public Result<SeckillVO> getPublicSeckillDetail(@PathVariable Long seckillId) {
        SeckillVO vo = seckillActivityService.getSeckillDetail(seckillId);
        return Result.success(vo);
    }

    // ==================== 内部接口（供其他微服务通过 Feign 调用，不鉴权） ====================

    /**
     * 查询秒杀活动信息（内部接口）
     * <p>shop-order 下单时通过 Feign 调用此接口，拿到秒杀价、限购、时间窗口等原始字段</p>
     */
    @Operation(summary = "内部-查询秒杀活动", description = "根据ID查询秒杀活动实体，供 shop-order 通过 Feign 调用")
    @GetMapping("/inner/{seckillId}")
    public Result<SeckillActivity> getSeckillById(@PathVariable Long seckillId) {
        SeckillActivity activity = seckillActivityService.getSeckillById(seckillId);
        return Result.success(activity);
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
