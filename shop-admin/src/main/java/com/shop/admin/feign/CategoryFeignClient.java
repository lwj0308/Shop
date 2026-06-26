package com.shop.admin.feign;

import com.shop.common.result.Result;
import com.shop.admin.feign.fallback.CategoryFeignClientFallbackFactory;
import com.shop.model.product.dto.CategoryDTO;
import com.shop.model.product.vo.CategoryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类服务Feign客户端
 * <p>
 * 通过Feign远程调用商品服务中的分类模块，管理后台用来管理商品分类的增删改查。
 * 使用fallbackFactory实现降级：当商品服务不可用时，走降级逻辑返回友好提示。
 * </p>
 */
@FeignClient(name = "shop-product", contextId = "adminCategory", path = "/product/category", fallbackFactory = CategoryFeignClientFallbackFactory.class)
public interface CategoryFeignClient {

    /**
     * 获取分类树形结构
     * <p>
     * 获取所有分类的树形结构，前端可以直接用来渲染分类菜单。
     * 比如"手机数码"下面有"智能手机"、"功能手机"等子分类。
     * </p>
     *
     * @return 分类树形列表
     */
    @GetMapping("/tree")
    Result<List<CategoryVO>> getCategoryTree();

    /**
     * 新增分类
     * <p>
     * 管理员添加新的商品分类，比如新增"智能家居"分类。
     * </p>
     *
     * @param dto 分类参数（包含父分类ID、名称、图标、排序等）
     * @return 操作结果
     */
    @PostMapping("")
    Result<Void> addCategory(@RequestBody CategoryDTO dto);

    /**
     * 修改分类
     * <p>
     * 管理员修改分类信息，比如修改分类名称、图标、排序等。
     * </p>
     *
     * @param id  分类ID
     * @param dto 分类参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> updateCategory(@PathVariable("id") Long id, @RequestBody CategoryDTO dto);

    /**
     * 删除分类
     * <p>
     * 管理员删除分类，删除前需要确保该分类下没有子分类和商品。
     * </p>
     *
     * @param id 分类ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> deleteCategory(@PathVariable("id") Long id);
}
