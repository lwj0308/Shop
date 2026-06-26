package com.shop.merchant.controller;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.feign.ProductFeignClient;
import com.shop.merchant.service.MerchantService;
import com.shop.merchant.service.ShopService;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.merchant.vo.ShopVO;
import com.shop.model.product.dto.CommentReplyDTO;
import com.shop.model.product.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 商家评价管理控制器
 * <p>
 * 商家在商家端管理自己店铺商品收到的评价：查看评价列表、回复评价。
 * 所有接口都需要登录，通过 SecurityUtils 获取当前登录用户ID，
 * 再查出对应的商家ID和店铺ID，商家只能管理自己店铺的评价。
 * </p>
 * <p>
 * 这个控制器本身不处理业务逻辑，而是通过 Feign 远程调用商品服务（shop-product）。
 * </p>
 */
@Slf4j
@Tag(name = "商家评价管理", description = "商家查看评价列表、回复用户评价")
@RestController
@RequestMapping("/merchant/comment")
@RequiredArgsConstructor
public class MerchantCommentController {

    /** 商品服务的Feign客户端，通过它远程调用商品服务的评价接口 */
    private final ProductFeignClient productFeignClient;

    /** 商家服务，用于通过用户ID查找商家 */
    private final MerchantService merchantService;

    /** 店铺服务，用于通过商家ID查找店铺 */
    private final ShopService shopService;

    /**
     * 获取当前商家的店铺ID
     * <p>内部工具方法：userId → merchantId → shopId，任一步骤失败返回null</p>
     *
     * @return 店铺ID，失败返回null
     */
    private Long getCurrentShopId() {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return null;
        }
        ShopVO shop = shopService.getShopByMerchantId(merchant.getId());
        return shop != null ? shop.getId() : null;
    }

    /**
     * 商家评价列表
     * <p>商家查看自己店铺商品收到的所有评价，支持按"是否已回复"筛选</p>
     *
     * @param pageNum   页码（从1开始）
     * @param pageSize  每页条数
     * @param hasReply  是否已回复：true只看已回复，false只看未回复，不传看全部
     * @return 分页评价列表（含商品名称）
     */
    @Operation(summary = "商家评价列表", description = "分页查询当前商家店铺的评价")
    @GetMapping("/list")
    public Result<PageResult<CommentVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Boolean hasReply) {
        Long shopId = getCurrentShopId();
        if (shopId == null) {
            return Result.fail("店铺信息不存在");
        }
        return productFeignClient.getCommentListByShopId(shopId, pageNum, pageSize, hasReply);
    }

    /**
     * 商家回复评价
     * <p>商家对用户的评价进行回复，shop-product会校验评价归属</p>
     *
     * @param dto 回复参数（评价ID + 回复内容）
     * @return 操作结果
     */
    @Operation(summary = "商家回复评价", description = "商家回复用户的评价")
    @PostMapping("/reply")
    public Result<Void> reply(@Validated @RequestBody CommentReplyDTO dto) {
        Long shopId = getCurrentShopId();
        if (shopId == null) {
            return Result.fail("店铺信息不存在");
        }
        return productFeignClient.replyComment(shopId, dto);
    }
}
