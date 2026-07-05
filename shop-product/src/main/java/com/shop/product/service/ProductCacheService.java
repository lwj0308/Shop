package com.shop.product.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;

/**
 * 商品缓存服务
 * <p>
 * 封装商品详情的缓存逻辑，使用 Redis + Caffeine 二级缓存。
 * 一级缓存（Caffeine）：本地缓存，速度最快，但只在单机有效
 * 二级缓存（Redis）：分布式缓存，所有服务实例共享
 * </p>
 * <p>
 * 缓存策略（Cache Aside Pattern）：
 * 1. 读：先查一级缓存 → 再查二级缓存 → 都没有查数据库 → 写入缓存
 * 2. 写：先更新数据库 → 再删除缓存（而不是更新缓存）
 * 3. 延迟双删：更新数据库后，先删一次缓存，延迟一段时间再删一次，
 *    防止并发时旧数据被重新写入缓存
 * </p>
 */
public interface ProductCacheService {

    /**
     * 获取商品详情（带缓存）
     * <p>
     * 查询顺序：Caffeine本地缓存 → Redis分布式缓存 → 数据库
     * 哪层命中就返回哪层的数据，都没命中就查数据库并写入缓存。
     * </p>
     *
     * @param productId 商品ID
     * @return 商品详情
     */
    ProductDetailVO getProductDetailFromCache(Long productId);

    /**
     * 只查缓存不查数据库（RL-06 引入，供降级使用）
     * <p>
     * 查询顺序：Caffeine本地缓存 → Redis分布式缓存。
     * 注意：这个方法不会查数据库，如果缓存都没有会返回 null。
     * </p>
     * <p>
     * 小白理解：正常查商品详情会先查缓存，缓存没有就查数据库。
     * 但如果数据库挂了，查数据库会报错，这时候就用这个方法只查缓存，
     * 返回旧数据总比报错好（用户体验：有数据看 > 500错误页面）。
     * </p>
     *
     * @param productId 商品ID
     * @return 缓存中的商品详情，缓存未命中返回 null
     */
    ProductDetailVO getCachedProductDetail(Long productId);

    /**
     * 删除商品详情缓存
     * <p>
     * 商品更新/上下架时调用，同时删除本地缓存和Redis缓存。
     * 删除后下次查询会从数据库重新加载。
     * </p>
     *
     * @param productId 商品ID
     */
    void evictProductDetailCache(Long productId);

    /**
     * 延迟双删缓存
     * <p>
     * 先删一次缓存，然后延迟一段时间再删一次。
     * 为什么需要双删？因为并发场景下可能发生这种情况：
     * 1. 线程A更新数据库
     * 2. 线程B读缓存没命中，查数据库（此时数据库还是旧值）
     * 3. 线程A删除缓存
     * 4. 线程B把旧值写入缓存 ← 这就是问题所在
     * 延迟再删一次，就能把线程B写入的旧值也删掉。
     * </p>
     *
     * @param productId 商品ID
     */
    void delayDoubleEvict(Long productId);

    /**
     * 缓存商品列表结果（RL-13 引入，供限流兜底使用）
     * <p>
     * 正常查询商品列表时把结果缓存到Redis，5分钟过期。
     * 当接口被 Sentinel 限流时，blockHandler 可以从缓存读取数据返回。
     * </p>
     *
     * @param categoryId    分类ID（可为null）
     * @param pageRequest   分页参数
     * @param pageResult    查询结果
     */
    void cacheProductList(Long categoryId, PageRequest pageRequest, PageResult<ProductVO> pageResult);

    /**
     * 只查缓存中的商品列表（RL-13 引入，供限流兜底使用）
     * <p>
     * 当商品列表接口被限流时，尝试从Redis读取之前缓存的列表数据。
     * 缓存未命中返回 null，调用方需处理 null 的情况。
     * </p>
     *
     * @param categoryId   分类ID（可为null）
     * @param pageRequest  分页参数
     * @return 缓存的商品列表，缓存未命中返回 null
     */
    PageResult<ProductVO> getCachedProductList(Long categoryId, PageRequest pageRequest);
}
