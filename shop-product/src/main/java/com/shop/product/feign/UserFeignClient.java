package com.shop.product.feign;

import com.shop.common.result.Result;
import com.shop.model.user.vo.UserBriefVO;
import com.shop.product.feign.fallback.UserFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务Feign客户端（商品端专用）
 * <p>
 * 商品服务通过Feign远程调用用户服务，主要用于：
 * - 评价功能：批量查询用户简要信息（昵称和头像）
 * - 推荐功能：记录浏览足迹、查询用户足迹分类（用于猜你喜欢）
 * </p>
 * <p>
 * 使用fallbackFactory实现降级：当用户服务不可用时，走降级逻辑返回空列表，
 * 不影响主流程（用户昵称和头像显示为空、足迹未记录但不影响商品查看）。
 * </p>
 */
@FeignClient(
        name = "shop-user",
        contextId = "productUser",
        fallbackFactory = UserFeignClientFallbackFactory.class
)
public interface UserFeignClient {

    /**
     * 批量查询用户简要信息
     * <p>
     * 评价列表展示用户昵称和头像时调用。一次性传入所有 userId，批量返回昵称和头像，
     * 避免对每个用户单独查询，减少RPC调用次数。
     * 调用 shop-user 的 GET /user/inner/batch 接口。
     * </p>
     *
     * @param userIds 用户ID列表（多个用逗号分隔，比如 ?userIds=1,2,3）
     * @return 用户简要信息列表
     */
    @GetMapping("/user/inner/batch")
    Result<List<UserBriefVO>> batchGetUserInfo(@RequestParam("userIds") List<Long> userIds);

    /**
     * 记录浏览足迹（用户查看商品详情时调用）
     * <p>
     * 调用 shop-user 的 POST /user/inner/footprint 接口，
     * 将用户浏览的商品记录到足迹表，用于"我的足迹"和"猜你喜欢"推荐。
     * categoryId 由商品服务传入（冗余存储，避免查商品表）。
     * </p>
     *
     * @param userId     用户ID
     * @param productId  商品ID
     * @param categoryId 商品分类ID
     * @return 操作结果
     */
    @PostMapping("/user/inner/footprint")
    Result<Void> recordFootprint(@RequestParam("userId") Long userId,
                                 @RequestParam("productId") Long productId,
                                 @RequestParam("categoryId") Long categoryId);

    /**
     * 查询用户浏览过的商品分类ID列表（猜你喜欢推荐时调用）
     * <p>
     * 调用 shop-user 的 GET /user/inner/footprint/categories 接口，
     * 返回用户浏览过的商品分类（去重），用于在这些分类下查热销商品作为推荐。
     * 如果返回空列表，调用方降级为全站热销推荐。
     * </p>
     *
     * @param userId 用户ID
     * @return 去重后的分类ID列表
     */
    @GetMapping("/user/inner/footprint/categories")
    Result<List<Long>> getFootprintCategories(@RequestParam("userId") Long userId);
}
