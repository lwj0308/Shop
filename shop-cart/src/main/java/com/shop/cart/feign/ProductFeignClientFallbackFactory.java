package com.shop.cart.feign;

import com.shop.common.result.Result;
import com.shop.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 商品服务Feign降级工厂
 * <p>
 * 当商品服务不可用（比如挂了、超时、网络抖动）时，
 * 这个工厂会创建一个"替身"对象来兜底，不让整个购物车服务跟着挂。
 * </p>
 * <p>
 * 降级策略说明：
 * - 获取价格：返回null，购物车列表中价格显示为0，提示"价格暂不可用"
 * - 获取库存状态：返回true（假设有货），宁可多展示不可漏展示
 * - 获取商品信息：返回null，购物车列表中显示"商品信息暂不可用"
 * - 批量获取SKU信息：返回空Map
 * </p>
 * <p>
 * 使用FallbackFactory而不是Fallback的好处：
 * - 可以获取到具体的异常原因，方便记录日志排查问题
 * - 每次调用都会创建新的降级实例，线程安全
 * </p>
 */
@Slf4j
@Component
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    /**
     * 创建降级实例
     * <p>
     * 当Feign调用失败时，Spring会调用这个方法创建一个"替身"对象。
     * cause参数里包含了具体的失败原因（超时、拒绝连接等），方便排查问题。
     * </p>
     *
     * @param cause Feign调用失败的原因
     * @return 降级后的ProductFeignClient实例
     */
    @Override
    public ProductFeignClient create(Throwable cause) {
        // 记录降级日志，包含失败原因，方便运维排查
        log.warn("商品服务调用降级，原因: {}", cause.getMessage());

        return new ProductFeignClient() {

            /**
             * 批量获取SKU信息 - 降级返回空Map
             * <p>
             * 商品服务挂了就查不到SKU信息，返回空Map，
             * 购物车列表会显示"商品信息暂不可用"。
             * </p>
             */
            @Override
            public Result<Map<Long, Map<String, Object>>> batchGetSkuInfo(List<Long> skuIds) {
                log.warn("批量获取SKU信息降级: skuIds={}", skuIds);
                return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), "商品服务暂不可用");
            }

            /**
             * 获取SKU价格 - 降级返回null
             * <p>
             * 价格查不到时返回失败结果，
             * 购物车列表中价格会显示为0，不会报错。
             * </p>
             */
            @Override
            public Result<BigDecimal> getSkuPrice(Long skuId) {
                log.warn("获取SKU价格降级: skuId={}", skuId);
                return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), "商品服务暂不可用");
            }

            /**
             * 获取库存状态 - 降级返回true（假设有货）
             * <p>
             * 库存查不到时，假设有货比假设没货更好：
             * - 假设有货：用户能看到商品，结算时再校验，体验更好
             * - 假设没货：用户看到全都没货，体验很差
             * </p>
             */
            @Override
            public Result<Boolean> getStockStatus(Long skuId) {
                log.warn("获取库存状态降级: skuId={}", skuId);
                // 降级时假设有货，结算时再校验
                return Result.success(true);
            }

            /**
             * 获取商品基本信息 - 降级返回null
             * <p>
             * 商品信息查不到时返回失败结果，
             * 购物车列表会显示"商品信息暂不可用"。
             * </p>
             */
            @Override
            public Result<Map<String, Object>> getProductBasicInfo(Long productId) {
                log.warn("获取商品基本信息降级: productId={}", productId);
                return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), "商品服务暂不可用");
            }
        };
    }
}
