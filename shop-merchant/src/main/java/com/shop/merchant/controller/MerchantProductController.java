package com.shop.merchant.controller;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.merchant.feign.ProductFeignClient;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商家商品管理Controller
 * <p>
 * 商家在商家端管理自己的商品：查看列表、发布、编辑、上架/下架。
 * 这里本身不处理业务逻辑，而是通过Feign远程调用商品服务（shop-product）。
 * </p>
 * <p>
 * 简单理解：这个Controller就像个"传话筒"，前端跟它说要看商品列表，
 * 它就转告商品服务"有人要看列表"，商品服务把数据给它，它再转给前端。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/merchant/product")
@RequiredArgsConstructor
@Tag(name = "商家商品管理", description = "商家管理自己的商品：列表、发布、编辑、上下架")
public class MerchantProductController {

    /** 商品服务的Feign客户端，通过它远程调用商品服务 */
    private final ProductFeignClient productFeignClient;

    /**
     * 商家商品列表
     * <p>
     * 前端传的是pageNum/pageSize，商品服务用的是page/size，这里做一下转换。
     * </p>
     *
     * @param pageNum    页码（前端习惯用pageNum）
     * @param pageSize   每页条数（前端习惯用pageSize）
     * @param status     商品状态（可选）：0下架 1上架
     * @param categoryId 分类ID（可选）
     * @param keyword    搜索关键词（可选）
     * @return 分页商品列表
     */
    @GetMapping("/list")
    @Operation(summary = "商家商品列表", description = "分页查询当前商家的商品列表")
    public Result<PageResult<ProductVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        // 调用Feign：把前端的pageNum/pageSize转成商品服务期望的page/size
        return productFeignClient.listProducts(pageNum, pageSize, categoryId, status, keyword);
    }

    /**
     * 发布商品
     *
     * @param dto 商品发布参数（名称、价格、库存、分类、规格等）
     * @return 新创建的商品ID
     */
    @PostMapping("/create")
    @Operation(summary = "发布商品", description = "商家发布新商品")
    public Result<Long> create(@RequestBody ProductCreateDTO dto) {
        return productFeignClient.createProduct(dto);
    }

    /**
     * 编辑商品
     *
     * @param id  商品ID
     * @param dto 商品编辑参数
     * @return 操作结果
     */
    @PutMapping("/update/{id}")
    @Operation(summary = "编辑商品", description = "编辑商品信息")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProductUpdateDTO dto) {
        return productFeignClient.updateProduct(id, dto);
    }

    /**
     * 上架/下架商品
     * <p>
     * 前端传一个status字段：1=上架，0=下架。
     * 商品服务拆成了两个接口（on-shelf和off-shelf），这里根据status值路由到对应接口。
     * </p>
     *
     * @param id     商品ID
     * @param status 目标状态：1上架 0下架
     * @return 操作结果
     */
    @PutMapping("/status/{id}")
    @Operation(summary = "上架/下架商品", description = "根据status值调用上架或下架接口")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam int status) {
        if (status == 1) {
            return productFeignClient.onShelfProduct(id);
        } else {
            return productFeignClient.offShelfProduct(id);
        }
    }

    /**
     * 删除商品
     * <p>
     * 商品服务暂无物理删除接口，这里用"下架"代替，让商品对用户不可见。
     * </p>
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除商品", description = "删除商品（实际执行下架操作）")
    public Result<Void> delete(@PathVariable Long id) {
        return productFeignClient.offShelfProduct(id);
    }

    /**
     * 获取商品详情
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "商品详情", description = "获取商品完整信息")
    public Result<ProductDetailVO> detail(@PathVariable Long id) {
        return productFeignClient.getProductDetail(id);
    }
}
