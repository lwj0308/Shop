package com.shop.product.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.result.Result;
import com.shop.model.product.vo.ProductVO;
import com.shop.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品推荐控制器
 * <p>
 * 提供首页和详情页的推荐商品接口：
 * - 热销推荐：按销量降序，用于首页"热销推荐"区域
 * - 新品推荐：按创建时间降序，用于首页"新品推荐"区域
 * - 相关推荐：同分类商品，用于详情页"看了又看"区域
 * - 猜你喜欢：基于浏览足迹的个性化推荐，用于首页"猜你喜欢"区域
 * </p>
 * <p>
 * 所有推荐接口都是公开访问的（不需要登录也能看推荐），
 * 但"猜你喜欢"对登录用户会返回个性化推荐，未登录用户降级为全站热销。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/product/recommend")
@RequiredArgsConstructor
@Tag(name = "商品推荐", description = "热销、新品、相关、猜你喜欢推荐")
public class ProductRecommendController {

    /** 商品服务 */
    private final ProductService productService;

    /**
     * 热销推荐
     * <p>
     * 按销量从高到低返回商品，用于首页"热销推荐"区域。
     * 公开访问，不需要登录。
     * </p>
     *
     * @param limit 返回数量（默认10个）
     * @return 热销商品列表
     */
    @GetMapping("/hot")
    @Operation(summary = "热销推荐", description = "按销量降序返回商品，用于首页热销推荐区域")
    public Result<List<ProductVO>> getHotProducts(
            @Parameter(description = "返回数量，默认10") @RequestParam(defaultValue = "10") int limit) {
        return Result.success(productService.getHotProducts(limit));
    }

    /**
     * 新品推荐
     * <p>
     * 按创建时间从新到旧返回商品，用于首页"新品推荐"区域。
     * 公开访问，不需要登录。
     * </p>
     *
     * @param limit 返回数量（默认10个）
     * @return 新品商品列表
     */
    @GetMapping("/new")
    @Operation(summary = "新品推荐", description = "按创建时间降序返回商品，用于首页新品推荐区域")
    public Result<List<ProductVO>> getNewProducts(
            @Parameter(description = "返回数量，默认10") @RequestParam(defaultValue = "10") int limit) {
        return Result.success(productService.getNewProducts(limit));
    }

    /**
     * 相关推荐（看了又看）
     * <p>
     * 查询与指定商品同分类的其他商品，用于商品详情页"看了又看"区域。
     * 公开访问，不需要登录。
     * </p>
     *
     * @param productId 当前商品ID
     * @param limit     返回数量（默认10个）
     * @return 相关商品列表
     */
    @GetMapping("/related/{productId}")
    @Operation(summary = "相关推荐", description = "查询同分类商品，用于详情页看了又看区域")
    public Result<List<ProductVO>> getRelatedProducts(
            @Parameter(description = "当前商品ID") @PathVariable Long productId,
            @Parameter(description = "返回数量，默认10") @RequestParam(defaultValue = "10") int limit) {
        return Result.success(productService.getRelatedProducts(productId, limit));
    }

    /**
     * 猜你喜欢
     * <p>
     * 基于用户浏览足迹的个性化推荐：
     * - 登录用户：根据浏览过的商品分类，推荐这些分类下的热销商品
     * - 未登录用户：降级为全站热销推荐
     * 公开访问（未登录也能看，只是不个性化）。
     * </p>
     *
     * @param limit 返回数量（默认10个）
     * @return 猜你喜欢商品列表
     */
    @GetMapping("/guess")
    @Operation(summary = "猜你喜欢", description = "基于浏览足迹的个性化推荐，未登录降级为全站热销")
    public Result<List<ProductVO>> getGuessProducts(
            @Parameter(description = "返回数量，默认10") @RequestParam(defaultValue = "10") int limit) {
        // 判断是否登录：登录用户走个性化推荐，未登录用户走全站热销
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return Result.success(productService.getGuessProducts(userId, limit));
    }
}
