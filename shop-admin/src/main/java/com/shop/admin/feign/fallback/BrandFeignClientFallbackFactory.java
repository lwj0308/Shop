package com.shop.admin.feign.fallback;

import com.shop.admin.feign.BrandFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.dto.BrandDTO;
import com.shop.model.product.vo.BrandVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 品牌服务Feign降级工厂
 * <p>
 * 当商品服务不可用时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口（getBrandList、getBrandById）：降级返回友好提示
 * - 写操作接口（addBrand、updateBrand、deleteBrand）：降级时抛出业务异常
 * </p>
 */
@Slf4j
@Component
public class BrandFeignClientFallbackFactory implements FallbackFactory<BrandFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的BrandFeignClient实例
     */
    @Override
    public BrandFeignClient create(Throwable cause) {
        log.error("品牌服务调用失败，触发降级", cause);
        return new BrandFeignClient() {

            /**
             * 查询品牌列表降级：返回"服务暂不可用"
             */
            @Override
            public Result<List<BrandVO>> getBrandList() {
                log.warn("查询品牌列表降级");
                return Result.fail("商品服务暂不可用");
            }

            /**
             * 查询品牌详情降级：返回"服务暂不可用"
             */
            @Override
            public Result<BrandVO> getBrandById(Long id) {
                log.warn("查询品牌详情降级: id={}", id);
                return Result.fail("商品服务暂不可用");
            }

            /**
             * 新增品牌降级：抛出业务异常
             */
            @Override
            public Result<Void> addBrand(BrandDTO dto) {
                log.error("新增品牌降级: name={}", dto.getName());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 修改品牌降级：抛出业务异常
             */
            @Override
            public Result<Void> updateBrand(Long id, BrandDTO dto) {
                log.error("修改品牌降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 删除品牌降级：抛出业务异常
             */
            @Override
            public Result<Void> deleteBrand(Long id) {
                log.error("删除品牌降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
