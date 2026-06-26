package com.shop.admin.feign;

import com.shop.common.result.Result;
import com.shop.admin.feign.fallback.BrandFeignClientFallbackFactory;
import com.shop.model.product.dto.BrandDTO;
import com.shop.model.product.vo.BrandVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 品牌服务Feign客户端
 * <p>
 * 通过Feign远程调用商品服务中的品牌模块，管理后台用来管理品牌的增删改查。
 * 使用fallbackFactory实现降级：当商品服务不可用时，走降级逻辑返回友好提示。
 * </p>
 */
@FeignClient(name = "shop-product", contextId = "adminBrand", path = "/product/brand", fallbackFactory = BrandFeignClientFallbackFactory.class)
public interface BrandFeignClient {

    /**
     * 获取品牌列表
     * <p>
     * 获取所有品牌的列表，用于品牌管理页展示。
     * </p>
     *
     * @return 品牌列表
     */
    @GetMapping("/list")
    Result<List<BrandVO>> getBrandList();

    /**
     * 根据品牌ID查询品牌详情
     * <p>
     * 查看某个品牌的详细信息。
     * </p>
     *
     * @param id 品牌ID
     * @return 品牌信息
     */
    @GetMapping("/{id}")
    Result<BrandVO> getBrandById(@PathVariable("id") Long id);

    /**
     * 新增品牌
     * <p>
     * 管理员添加新的品牌，比如新增"小米"品牌。
     * </p>
     *
     * @param dto 品牌参数（包含名称、Logo、描述等）
     * @return 操作结果
     */
    @PostMapping("")
    Result<Void> addBrand(@RequestBody BrandDTO dto);

    /**
     * 修改品牌
     * <p>
     * 管理员修改品牌信息，比如修改品牌名称、Logo、描述等。
     * </p>
     *
     * @param id  品牌ID
     * @param dto 品牌参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> updateBrand(@PathVariable("id") Long id, @RequestBody BrandDTO dto);

    /**
     * 删除品牌
     * <p>
     * 管理员删除品牌，删除前需要确保该品牌下没有商品。
     * </p>
     *
     * @param id 品牌ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> deleteBrand(@PathVariable("id") Long id);
}
