package com.shop.product.feign;

import com.shop.common.result.Result;
import com.shop.model.merchant.vo.ShopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商家服务Feign降级工厂
 * <p>
 * 当商家服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口（getShopById）：降级返回null，调用方拿到null后会跳过merchantId设置，
 *   不阻塞商品查询和下单的主流程。
 * </p>
 * <p>
 * 简单理解：就像你打电话问同事问题，同事没接电话（服务挂了），
 * 你就先按"不知道"处理（返回null），事情还得继续往下做，不能因为一个电话卡住。
 * </p>
 */
@Slf4j
@Component
public class MerchantFeignClientFallbackFactory implements FallbackFactory<MerchantFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的MerchantFeignClient实例
     */
    @Override
    public MerchantFeignClient create(Throwable cause) {
        log.error("商家服务调用失败，触发降级", cause);
        return new MerchantFeignClient() {

            /**
             * 查询店铺信息降级：返回null
             * <p>
             * 查询类降级返回null，调用方（ProductServiceImpl）会判断null并跳过merchantId设置，
             * 保证商品查询主流程不被商家服务故障阻塞。
             * </p>
             *
             * @param shopId 店铺ID
             * @return 降级返回null
             */
            @Override
            public Result<ShopVO> getShopById(Long shopId) {
                log.warn("查询店铺信息降级: shopId={}", shopId);
                return null;
            }
        };
    }
}
