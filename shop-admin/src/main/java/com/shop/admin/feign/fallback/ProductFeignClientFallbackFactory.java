package com.shop.admin.feign.fallback;

import com.shop.admin.feign.ProductFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商品服务Feign降级工厂
 * <p>
 * 当商品服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口（listProducts、getProductDetail）：降级返回友好提示
 * - 写操作接口（offShelfProduct、onShelfProduct）：降级时抛出业务异常
 * </p>
 */
@Slf4j
@Component
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的ProductFeignClient实例
     */
    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("商品服务调用失败，触发降级", cause);
        return new ProductFeignClient() {

            /**
             * 查询商品列表降级：返回"服务暂不可用"
             */
            @Override
            public Result<PageResult<ProductVO>> listProducts(int page, int size, Long categoryId, Integer status, String keyword) {
                log.warn("查询商品列表降级: page={}, size={}, categoryId={}, status={}, keyword={}", page, size, categoryId, status, keyword);
                return Result.fail("商品服务暂不可用");
            }

            /**
             * 查询商品详情降级：返回"服务暂不可用"
             */
            @Override
            public Result<ProductDetailVO> getProductDetail(Long id) {
                log.warn("查询商品详情降级: id={}", id);
                return Result.fail("商品服务暂不可用");
            }

            /**
             * 强制下架商品降级：抛出业务异常
             * <p>
             * 下架是关键操作，如果商品服务挂了导致下架没成功，
             * 必须让管理员知道操作失败了。
             * </p>
             */
            @Override
            public Result<Void> offShelfProduct(Long id) {
                log.error("强制下架商品降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 审批上架商品降级：抛出业务异常
             */
            @Override
            public Result<Void> onShelfProduct(Long id) {
                log.error("审批上架商品降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
