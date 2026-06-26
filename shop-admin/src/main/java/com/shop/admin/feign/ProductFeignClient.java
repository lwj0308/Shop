package com.shop.admin.feign;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.admin.feign.fallback.ProductFeignClientFallbackFactory;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 商品服务Feign客户端
 * <p>
 * 通过Feign远程调用商品服务，管理后台用来管理商品上下架、查看商品列表等。
 * 使用fallbackFactory实现降级：当商品服务不可用时，走降级逻辑返回友好提示。
 * </p>
 */
@FeignClient(name = "shop-product", contextId = "adminProduct", path = "/product", fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    /**
     * 分页查询商品列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有商品，支持按分类、状态和关键词筛选。
     * </p>
     *
     * @param page       页码
     * @param size       每页条数
     * @param categoryId 分类ID（可选）
     * @param status     商品状态（可选）：0下架 1上架
     * @param keyword    搜索关键词（可选）：按商品名称搜索
     * @return 分页商品列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<ProductVO>> listProducts(@RequestParam("page") int page,
                                                @RequestParam("size") int size,
                                                @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                @RequestParam(value = "status", required = false) Integer status,
                                                @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 获取商品详情
     * <p>
     * 查看商品的完整信息，包含SKU列表、规格列表、评价摘要等。
     * </p>
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/{id}")
    Result<ProductDetailVO> getProductDetail(@PathVariable("id") Long id);

    /**
     * 强制下架商品
     * <p>
     * 管理员强制下架违规商品，下架后用户无法看到该商品。
     * </p>
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @PutMapping("/{id}/off-shelf")
    Result<Void> offShelfProduct(@PathVariable("id") Long id);

    /**
     * 审批通过并上架商品
     * <p>
     * 管理员审批通过后，商品上架对用户可见。
     * </p>
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @PutMapping("/{id}/on-shelf")
    Result<Void> onShelfProduct(@PathVariable("id") Long id);
}
