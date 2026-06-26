package com.shop.admin.feign.fallback;

import com.shop.admin.feign.CategoryFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.dto.CategoryDTO;
import com.shop.model.product.vo.CategoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 分类服务Feign降级工厂
 * <p>
 * 当商品服务不可用时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口（getCategoryTree）：降级返回友好提示
 * - 写操作接口（addCategory、updateCategory、deleteCategory）：降级时抛出业务异常
 * </p>
 */
@Slf4j
@Component
public class CategoryFeignClientFallbackFactory implements FallbackFactory<CategoryFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的CategoryFeignClient实例
     */
    @Override
    public CategoryFeignClient create(Throwable cause) {
        log.error("分类服务调用失败，触发降级", cause);
        return new CategoryFeignClient() {

            /**
             * 查询分类树降级：返回"服务暂不可用"
             */
            @Override
            public Result<List<CategoryVO>> getCategoryTree() {
                log.warn("查询分类树降级");
                return Result.fail("商品服务暂不可用");
            }

            /**
             * 新增分类降级：抛出业务异常
             */
            @Override
            public Result<Void> addCategory(CategoryDTO dto) {
                log.error("新增分类降级: name={}", dto.getName());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 修改分类降级：抛出业务异常
             */
            @Override
            public Result<Void> updateCategory(Long id, CategoryDTO dto) {
                log.error("修改分类降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 删除分类降级：抛出业务异常
             */
            @Override
            public Result<Void> deleteCategory(Long id) {
                log.error("删除分类降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
