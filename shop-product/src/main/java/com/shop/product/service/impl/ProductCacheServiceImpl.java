package com.shop.product.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.entity.*;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.product.vo.ProductVO;
import com.shop.product.mapper.*;
import com.shop.product.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品缓存服务实现类
 * <p>
 * 使用 Redis + Caffeine 二级缓存存储商品详情。
 * Caffeine是本地缓存（JVM内存），速度最快，但只在当前服务实例有效；
 * Redis是分布式缓存，所有服务实例共享，速度稍慢但数据一致性好。
 * </p>
 * <p>
 * 查询顺序：Caffeine → Redis → 数据库
 * 这样大部分请求在本地缓存就能命中，只有少量请求打到Redis或数据库。
 * </p>
 * <p>
 * 注意：这个类直接注入Mapper查数据库，而不是调用ProductService，
 * 避免和ProductService产生循环依赖。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    /** Redis模板，操作分布式缓存 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 商品Mapper，查数据库用 */
    private final ProductMapper productMapper;

    /** SKU Mapper */
    private final ProductSkuMapper productSkuMapper;

    /** 规格模板Mapper */
    private final ProductSpecMapper productSpecMapper;

    /** 规格值Mapper */
    private final ProductSpecValueMapper productSpecValueMapper;

    /** 评价Mapper */
    private final ProductCommentMapper productCommentMapper;

    /** 分类Mapper */
    private final CategoryMapper categoryMapper;

    /** 品牌Mapper */
    private final BrandMapper brandMapper;

    /** 商品详情Redis缓存key前缀 */
    private static final String PRODUCT_DETAIL_CACHE_KEY = "product:detail:";

    /** 延迟双删的延迟时间（毫秒），500ms足够覆盖大部分并发场景 */
    private static final long DELAY_DOUBLE_DELETE_MS = 500;

    /**
     * Caffeine本地缓存
     * <p>
     * 最多缓存1000个商品详情，每个缓存5分钟过期。
     * 为什么本地缓存比Redis短？因为本地缓存无法主动通知其他实例删除，
     * 过期时间短一些，减少数据不一致的窗口期。
     * </p>
     */
    private final Cache<Long, ProductDetailVO> localCache = Caffeine.newBuilder()
            .maximumSize(1000)                          // 最多缓存1000个
            .expireAfterWrite(5, TimeUnit.MINUTES)      // 写入5分钟后过期
            .recordStats()                              // 记录缓存命中率统计
            .build();

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
    @Override
    public ProductDetailVO getProductDetailFromCache(Long productId) {
        // 1. 先查Caffeine本地缓存（最快）
        ProductDetailVO detail = localCache.getIfPresent(productId);
        if (detail != null) {
            log.debug("Caffeine本地缓存命中: productId={}", productId);
            return detail;
        }

        // 2. 再查Redis分布式缓存
        // 注意：这里简化处理，实际应该用JSON序列化/反序列化ProductDetailVO
        // 由于ProductDetailVO结构复杂，当前版本直接查数据库
        // TODO: 后续优化为Redis JSON缓存

        // 3. 查数据库
        detail = loadProductDetailFromDB(productId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 4. 写入本地缓存
        localCache.put(productId, detail);

        log.debug("从数据库加载商品详情到缓存: productId={}", productId);
        return detail;
    }

    /**
     * 删除商品详情缓存
     * <p>
     * 同时删除Caffeine本地缓存和Redis缓存，确保下次查询从数据库重新加载。
     * </p>
     *
     * @param productId 商品ID
     */
    @Override
    public void evictProductDetailCache(Long productId) {
        // 删除本地缓存
        localCache.invalidate(productId);
        // 删除Redis缓存
        stringRedisTemplate.delete(PRODUCT_DETAIL_CACHE_KEY + productId);
        log.debug("删除商品详情缓存: productId={}", productId);
    }

    /**
     * 延迟双删缓存
     * <p>
     * 先删一次缓存，然后延迟500ms再删一次。
     * 解决并发场景下旧数据被重新写入缓存的问题。
     * </p>
     *
     * @param productId 商品ID
     */
    @Override
    public void delayDoubleEvict(Long productId) {
        // 第一次删除
        evictProductDetailCache(productId);

        // 延迟后再删一次（异步执行，不阻塞当前线程）
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(DELAY_DOUBLE_DELETE_MS);
                evictProductDetailCache(productId);
                log.debug("延迟双删完成: productId={}", productId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("延迟双删被中断: productId={}", productId);
            }
        });
    }

    // ==================== 私有方法 ====================

    /**
     * 从数据库加载商品详情
     * <p>
     * 直接查数据库组装商品详情，不走ProductService避免循环依赖。
     * </p>
     *
     * @param productId 商品ID
     * @return 商品详情
     */
    private ProductDetailVO loadProductDetailFromDB(Long productId) {
        // 查询商品SPU
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }

        ProductDetailVO detailVO = new ProductDetailVO();
        detailVO.setId(product.getId());
        detailVO.setCategoryId(product.getCategoryId());
        detailVO.setBrandId(product.getBrandId());
        detailVO.setShopId(product.getShopId());
        detailVO.setName(product.getName());
        detailVO.setSubtitle(product.getSubtitle());
        detailVO.setMainImage(product.getMainImage());
        detailVO.setImages(product.getImages());
        detailVO.setDetail(product.getDetail());
        detailVO.setStatus(product.getStatus());
        detailVO.setCreateTime(product.getCreateTime());

        // 查询分类名称
        Category category = categoryMapper.selectById(product.getCategoryId());
        if (category != null) {
            detailVO.setCategoryName(category.getName());
        }

        // 查询品牌名称
        if (product.getBrandId() != null) {
            Brand brand = brandMapper.selectById(product.getBrandId());
            if (brand != null) {
                detailVO.setBrandName(brand.getName());
            }
        }

        // 查询SKU列表
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, productId)
        );
        List<ProductSkuVO> skuVOS = skus.stream().map(this::convertSkuToVO).collect(Collectors.toList());
        detailVO.setSkus(skuVOS);

        // 计算最低价格和总库存
        if (!skus.isEmpty()) {
            BigDecimal minPrice = skus.stream()
                    .map(ProductSku::getPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            int totalStock = skus.stream().mapToInt(ProductSku::getStock).sum();
            detailVO.setMinPrice(minPrice);
            detailVO.setTotalStock(totalStock);
        }

        // 查询规格列表（从product_spec表查询）
        List<ProductSpec> specs = productSpecMapper.selectList(
                new LambdaQueryWrapper<ProductSpec>().eq(ProductSpec::getProductId, productId)
        );
        List<ProductVO.SpecVO> specVOS = new ArrayList<>();
        for (ProductSpec spec : specs) {
            ProductVO.SpecVO specVO = new ProductVO.SpecVO();
            specVO.setName(spec.getName());
            List<ProductSpecValue> specValues = productSpecValueMapper.selectList(
                    new LambdaQueryWrapper<ProductSpecValue>().eq(ProductSpecValue::getSpecId, spec.getId())
            );
            specVO.setValues(specValues.stream().map(ProductSpecValue::getValue).collect(Collectors.toList()));
            specVOS.add(specVO);
        }

        // 如果product_spec表没有记录，但SKU有规格数据，则从SKU的specValues中动态生成规格列表
        // 这样即使product_spec表缺少数据，前端也能正常显示规格选择器
        if (specVOS.isEmpty() && !skus.isEmpty()) {
            // 用LinkedHashMap保持规格顺序，key是规格名（如"颜色"），value是规格值集合（如["红色","蓝色"]）
            Map<String, LinkedHashSet<String>> specMap = new LinkedHashMap<>();
            for (ProductSku sku : skus) {
                Map<String, String> specValues = sku.getSpecValues();
                if (specValues != null) {
                    for (Map.Entry<String, String> entry : specValues.entrySet()) {
                        // computeIfAbsent：如果key不存在就新建一个LinkedHashSet，然后把value加进去
                        specMap.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).add(entry.getValue());
                    }
                }
            }
            // 把收集到的规格数据转换成SpecVO列表
            for (Map.Entry<String, LinkedHashSet<String>> entry : specMap.entrySet()) {
                ProductVO.SpecVO specVO = new ProductVO.SpecVO();
                specVO.setName(entry.getKey());
                specVO.setValues(new ArrayList<>(entry.getValue()));
                specVOS.add(specVO);
            }
        }
        detailVO.setSpecs(specVOS);

        // 查询评价摘要
        ProductDetailVO.CommentSummary commentSummary = new ProductDetailVO.CommentSummary();
        Long totalCount = productCommentMapper.selectCount(
                new LambdaQueryWrapper<ProductComment>().eq(ProductComment::getProductId, productId)
        );
        commentSummary.setTotalCount(totalCount);

        if (totalCount > 0) {
            List<ProductComment> comments = productCommentMapper.selectList(
                    new LambdaQueryWrapper<ProductComment>().eq(ProductComment::getProductId, productId)
            );
            double avgScore = comments.stream().mapToInt(ProductComment::getScore).average().orElse(5.0);
            commentSummary.setAvgScore(BigDecimal.valueOf(avgScore).setScale(1, BigDecimal.ROUND_HALF_UP));

            long goodCount = comments.stream().filter(c -> c.getScore() >= 4).count();
            double goodRate = (double) goodCount / totalCount * 100;
            commentSummary.setGoodRate(BigDecimal.valueOf(goodRate).setScale(1, BigDecimal.ROUND_HALF_UP));
        } else {
            commentSummary.setAvgScore(BigDecimal.valueOf(5.0));
            commentSummary.setGoodRate(BigDecimal.valueOf(100.0));
        }
        detailVO.setCommentSummary(commentSummary);

        return detailVO;
    }

    /**
     * ProductSku实体转ProductSkuVO
     */
    private ProductSkuVO convertSkuToVO(ProductSku sku) {
        ProductSkuVO vo = new ProductSkuVO();
        vo.setId(sku.getId());
        vo.setProductId(sku.getProductId());
        vo.setSpecValues(sku.getSpecValues());
        vo.setPrice(sku.getPrice());
        vo.setOriginalPrice(sku.getOriginalPrice());
        vo.setStock(sku.getStock());
        vo.setImage(sku.getImage());
        vo.setStatus(sku.getStatus());
        return vo;
    }
}
