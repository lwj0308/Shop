package com.shop.product.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shop.common.result.Result;
import com.shop.model.merchant.vo.ShopVO;
import com.shop.model.product.entity.Product;
import com.shop.product.feign.MerchantFeignClient;
import com.shop.product.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * shopId → merchantId 本地缓存查询器（N-P 性能测试整改）
 * <p>
 * 小白理解：店铺和商家的归属关系很少变化（只有店铺转让才会变），
 * 没必要每次查询商品都"打电话"（Feign）问商家服务"这个店铺是谁的"。
 * 把答案记在本地小本本（Caffeine 缓存）上，下次直接查小本本，30 分钟后自动过期重查。
 * </p>
 * <p>
 * 缓存策略：
 * - 最多缓存 500 个店铺的映射（足够覆盖正常业务）
 * - 写入 30 分钟后过期（店铺归属变更极少，30 分钟足够）
 * - 缓存未命中时回源 Feign 调用，失败仍降级返回 null
 * </p>
 * <p>
 * ProductServiceImpl 与 ProductCacheServiceImpl 都需要这个查询，
 * 故抽到此处共用同一份逻辑（两个 Service 各自持有一个实例，缓存互不干扰）。
 * </p>
 */
@Slf4j
class MerchantIdCache {

    /** shopId → merchantId 本地缓存 */
    private final Cache<Long, Long> shopIdToMerchantIdCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * 根据商品ID查询商家ID
     * <p>
     * 调用链：productId → Product表查shopId → 查缓存（shopId→merchantId）→ 缓存未命中才 Feign 调用商家服务。
     * 整个过程用try-catch包裹，任何一步失败都只记日志返回null，
     * 保证商品查询和下单主流程不被商家服务故障阻塞。
     * </p>
     *
     * @param productId            商品ID
     * @param productMapper        商品Mapper（查shopId用）
     * @param merchantFeignClient  商家服务Feign客户端
     * @return 商家ID，查询失败返回null
     */
    Long resolve(Long productId, ProductMapper productMapper, MerchantFeignClient merchantFeignClient) {
        try {
            // 1. 先查出商品SPU，拿到店铺ID
            Product product = productMapper.selectById(productId);
            if (product == null || product.getShopId() == null) {
                log.warn("查询merchantId失败：商品或店铺ID为空, productId={}", productId);
                return null;
            }

            Long shopId = product.getShopId();

            // 2. 先查本地缓存（命中率 >99%，命中直接返回，不发起远程调用）
            Long cachedMerchantId = shopIdToMerchantIdCache.getIfPresent(shopId);
            if (cachedMerchantId != null) {
                log.debug("shopId→merchantId 缓存命中: shopId={}, merchantId={}", shopId, cachedMerchantId);
                return cachedMerchantId;
            }

            // 3. 缓存未命中，通过Feign调用商家服务查询店铺信息
            Result<ShopVO> shopResult = merchantFeignClient.getShopById(shopId);
            if (shopResult == null || !shopResult.isSuccess() || shopResult.getData() == null) {
                log.warn("查询merchantId失败：店铺信息为空, shopId={}", shopId);
                return null;
            }

            // 4. 取出merchantId并写入缓存，下次同一shopId就不再发起远程调用
            Long merchantId = shopResult.getData().getMerchantId();
            if (merchantId != null) {
                shopIdToMerchantIdCache.put(shopId, merchantId);
                log.debug("shopId→merchantId 缓存写入: shopId={}, merchantId={}", shopId, merchantId);
            }

            return merchantId;
        } catch (Exception e) {
            // Feign调用失败（服务挂了、超时等）只记日志，不抛异常
            log.warn("查询merchantId异常，不影响主流程: productId={}", productId, e);
            return null;
        }
    }
}
