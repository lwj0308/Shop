package com.shop.merchant.controller;

import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.service.MerchantService;
import com.shop.merchant.service.ShopService;
import com.shop.model.merchant.dto.ShopDTO;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.merchant.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺管理控制器
 * <p>
 * 提供店铺信息查询和修改接口。
 * 商家审核通过后系统自动创建店铺，商家可以修改店铺信息。
 * 使用SecurityUtils获取当前登录用户ID，替代直接从请求头获取。
 * </p>
 */
@Tag(name = "店铺管理", description = "店铺信息查询和修改接口")
@RestController
@RequestMapping("/merchant/shop")
@RequiredArgsConstructor
public class ShopController {

    /** 店铺服务，处理店铺相关的业务逻辑 */
    private final ShopService shopService;

    /** 商家服务，用于通过用户ID查找商家 */
    private final MerchantService merchantService;

    /**
     * 获取当前店铺信息
     * <p>
     * 商家登录后查看自己的店铺信息。
     * </p>
     *
     * @return 店铺信息
     */
    @Operation(summary = "获取当前店铺信息", description = "商家查看自己的店铺信息")
    @GetMapping("/info")
    public Result<ShopVO> getInfo() {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        ShopVO shop = shopService.getShopByMerchantId(merchant.getId());
        return Result.success(shop);
    }

    /**
     * 更新店铺信息
     * <p>
     * 商家修改自己店铺的名称、Logo、Banner、描述等信息。
     * 只传需要修改的字段，不传的字段保持不变。
     * 被禁用的商家不能修改店铺信息。
     * </p>
     *
     * @param shopDTO 店铺更新参数
     * @return 操作结果
     */
    @Operation(summary = "更新店铺信息", description = "商家修改店铺名称、Logo、Banner等")
    @PutMapping("/info")
    public Result<Void> updateInfo(@Validated @RequestBody ShopDTO shopDTO) {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        // 校验商家状态：被禁用的商家不能修改店铺信息
        merchantService.checkMerchantActive(merchant.getId());

        ShopVO shop = shopService.getShopByMerchantId(merchant.getId());
        if (shop == null) {
            return Result.fail("店铺信息不存在");
        }
        shopService.updateShop(shop.getId(), shopDTO);
        return Result.success(null);
    }

    /**
     * 根据ID获取店铺信息（公开接口）
     * <p>
     * 任何人都可以查看店铺信息，不需要登录。
     * 用户浏览商品时可能会查看店铺详情。
     * </p>
     *
     * @param shopId 店铺ID
     * @return 店铺信息
     */
    @Operation(summary = "根据ID获取店铺信息", description = "公开接口，任何人可查看店铺详情")
    @GetMapping("/{shopId}")
    public Result<ShopVO> getShopById(@PathVariable Long shopId) {
        ShopVO shop = shopService.getShopInfo(shopId);
        return Result.success(shop);
    }
}
