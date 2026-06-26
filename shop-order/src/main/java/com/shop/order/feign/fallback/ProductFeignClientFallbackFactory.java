package com.shop.order.feign.fallback;

import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.dto.StockDeductItemDTO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.order.feign.ProductFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商品服务Feign降级工厂
 * <p>
 * 当商品服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 核心优化点：
 * - 查询类接口（getSkuById/batchGetSkuByIds）：降级返回友好提示，不影响用户体验
 * - 关键操作接口（deductStock/batchDeductStock）：降级时抛出异常，触发Seata分布式事务回滚
 *   因为库存扣减是核心链路，如果商品服务挂了，下单必须失败，不能让用户下了单但库存没扣
 * - 回滚类接口（addStock）：降级时记录日志，可以通过定时任务补偿
 * - 弱依赖接口（incrSales/incrSalesBatch）：降级时仅记日志，返回成功，不影响下单主流程
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
             * 获取SKU信息降级：返回"商品服务不可用"
             * <p>
             * 查询类接口降级不抛异常，返回友好提示。
             * 因为查询失败不会影响数据一致性。
             * </p>
             */
            @Override
            public Result<ProductSkuVO> getSkuById(Long skuId) {
                log.warn("获取SKU信息降级: skuId={}", skuId);
                return Result.fail("商品服务暂时不可用，请稍后重试");
            }

            /**
             * 扣减库存降级：抛出异常触发Seata回滚
             * <p>
             * 关键操作！库存扣减是下单核心链路，
             * 如果商品服务挂了导致库存没扣成功，整个订单必须回滚，
             * 否则用户下了单但库存没扣，会导致超卖。
             * 所以这里必须抛异常，让Seata感知到失败并回滚整个分布式事务。
             * </p>
             */
            @Override
            public Result<Void> deductStock(Long skuId, Integer quantity) {
                log.error("扣减库存降级，触发Seata回滚: skuId={}, quantity={}", skuId, quantity);
                throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode(),
                        "商品服务不可用，库存扣减失败，订单将回滚");
            }

            /**
             * 回滚库存降级：记录日志，不抛异常
             * <p>
             * 回滚库存是在取消/退款时调用的，不是核心链路，
             * 即使回滚失败也不应该影响取消/退款的主流程。
             * 可以通过定时任务补偿（扫描未回滚的库存）。
             * </p>
             */
            @Override
            public Result<Void> addStock(Long skuId, Integer quantity) {
                log.error("回滚库存降级，需要通过定时任务补偿: skuId={}, quantity={}", skuId, quantity);
                return Result.fail("商品服务暂时不可用，库存回滚将延迟处理");
            }

            /**
             * 销量累加降级：仅记日志，返回成功
             * <p>
             * 销量累加是弱依赖（用于推荐排序，不影响交易），
             * 即使失败了也不影响下单主流程，只是销量数据不准而已。
             * </p>
             */
            @Override
            public Result<Void> incrSales(Long productId, Integer quantity) {
                log.warn("销量累加降级（商品服务不可用）: productId={}, quantity={}", productId, quantity);
                return Result.success();
            }

            /**
             * 批量获取SKU信息降级：返回友好提示
             * <p>
             * 查询类接口，降级不抛异常，返回失败让 shop-order 走自身校验逻辑。
             * </p>
             */
            @Override
            public Result<List<ProductSkuVO>> batchGetSkuByIds(List<Long> skuIds) {
                log.warn("批量获取SKU信息降级: skuIds={}", skuIds);
                return Result.fail("商品服务暂时不可用，请稍后重试");
            }

            /**
             * 批量扣减库存降级：抛异常触发事务回滚
             * <p>
             * 库存扣减是核心链路，降级必须抛异常让整个下单事务回滚，
             * 避免出现"订单已创建但库存没扣"的超卖问题。
             * </p>
             */
            @Override
            public Result<Void> batchDeductStock(List<StockDeductItemDTO> items, String orderNo) {
                log.error("批量扣减库存降级，触发事务回滚: orderNo={}, items={}", orderNo, items);
                throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode(),
                        "商品服务不可用，库存扣减失败，订单将回滚");
            }

            /**
             * 批量销量累加降级：仅记日志，返回成功
             * <p>
             * 弱依赖，降级时返回成功，不影响下单主流程。
             * </p>
             */
            @Override
            public Result<Void> incrSalesBatch(Map<Long, Integer> productQuantities) {
                log.warn("批量销量累加降级（商品服务不可用）: productQuantities={}", productQuantities);
                return Result.success();
            }
        };
    }
}
