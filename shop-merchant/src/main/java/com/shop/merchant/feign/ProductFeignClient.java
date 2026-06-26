package com.shop.merchant.feign;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.product.dto.CommentReplyDTO;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.vo.CommentVO;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 商品服务Feign客户端（商家端专用）
 * <p>
 * 商家服务通过Feign远程调用商品服务，管理自己的商品。
 * 包括：查看商品列表、发布商品、编辑商品、上架/下架。
 * </p>
 * <p>
 * 使用fallbackFactory实现降级：当商品服务不可用时，走降级逻辑返回友好提示。
 * </p>
 */
@FeignClient(
        name = "shop-product",
        contextId = "merchantProduct",
        path = "/product",
        fallbackFactory = ProductFeignClientFallbackFactory.class
)
public interface ProductFeignClient {

    /**
     * 分页查询商品列表（管理后台专用）
     * <p>
     * 商家在后台查看自己的商品，支持按分类、状态和关键词筛选。
     * </p>
     *
     * @param page       页码（从1开始）
     * @param size       每页条数
     * @param categoryId 分类ID（可选）
     * @param status     商品状态（可选）：0下架 1上架
     * @param keyword    搜索关键词（可选）
     * @return 分页商品列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<ProductVO>> listProducts(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 获取商品详情
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/{id}")
    Result<ProductDetailVO> getProductDetail(@PathVariable("id") Long id);

    /**
     * 发布商品
     * <p>
     * 商家发布新商品，同时创建SPU+规格+SKU。
     * </p>
     *
     * @param dto 商品发布参数
     * @return 商品ID
     */
    @PostMapping
    Result<Long> createProduct(@RequestBody ProductCreateDTO dto);

    /**
     * 编辑商品
     *
     * @param id  商品ID
     * @param dto 商品编辑参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> updateProduct(@PathVariable("id") Long id, @RequestBody ProductUpdateDTO dto);

    /**
     * 上架商品
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @PutMapping("/{id}/on-shelf")
    Result<Void> onShelfProduct(@PathVariable("id") Long id);

    /**
     * 下架商品
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @PutMapping("/{id}/off-shelf")
    Result<Void> offShelfProduct(@PathVariable("id") Long id);

    /**
     * 按店铺查询评价列表（内部接口）
     * <p>商家在评价管理页面查看自己店铺商品收到的所有评价</p>
     *
     * @param shopId    店铺ID
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @param hasReply  是否已回复：true已回复，false未回复，null全部
     * @return 分页评价列表（含商品名称）
     */
    @GetMapping("/comment/shop/list")
    Result<PageResult<CommentVO>> getCommentListByShopId(
            @RequestParam("shopId") Long shopId,
            @RequestParam("pageNum") int pageNum,
            @RequestParam("pageSize") int pageSize,
            @RequestParam(value = "hasReply", required = false) Boolean hasReply);

    /**
     * 商家回复评价（内部接口）
     * <p>商家对用户的评价进行回复，shop-product会校验评价归属</p>
     *
     * @param shopId 店铺ID（用于校验评价归属）
     * @param dto    回复参数（评价ID + 回复内容）
     * @return 操作结果
     */
    @PostMapping("/comment/reply")
    Result<Void> replyComment(
            @RequestParam("shopId") Long shopId,
            @RequestBody CommentReplyDTO dto);
}
