package com.shop.product.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.entity.*;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.product.vo.ProductVO;
import com.shop.product.feign.MerchantFeignClient;
import com.shop.product.mapper.*;
import com.shop.product.service.ProductCacheService;
import com.shop.common.result.Result;
import com.shop.model.merchant.vo.ShopVO;
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

    /** 商家服务Feign客户端（N-P 性能测试整改：用于查询 shopId→merchantId 映射） */
    private final MerchantFeignClient merchantFeignClient;

    /**
     * shopId → merchantId 本地缓存（N-P 性能测试整改）
     * <p>
     * 避免每次查商品详情都通过 Feign 调用商家服务，缓存命中后直接返回 merchantId。
     * 缓存策略：最多 500 个店铺，写入 30 分钟后过期（店铺归属变更极少）。
     * </p>
     */
    private final Cache<Long, Long> shopIdToMerchantIdCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /** 商品详情Redis缓存key前缀 */
    private static final String PRODUCT_DETAIL_CACHE_KEY = "product:detail:";

    /** 商品列表Redis缓存key前缀（RL-13 引入） */
    private static final String PRODUCT_LIST_CACHE_KEY = "product:list:";

    /** 商品列表缓存过期时间（分钟），5分钟后自动失效 */
    private static final long LIST_CACHE_EXPIRE_MINUTES = 5;

    /** 延迟双删的延迟时间（毫秒），500ms足够覆盖大部分并发场景 */
    private static final long DELAY_DOUBLE_DELETE_MS = 500;

    /**
     * JSON序列化工具（RL-13 引入，用于商品列表缓存序列化）
     * 直接new而不通过Spring注入，避免某些Spring Boot版本下ObjectMapper Bean未自动配置的问题。
     * 注册JavaTimeModule支持LocalDateTime序列化。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
     * 只查缓存不查数据库（RL-06 引入，供降级使用）
     * <p>
     * 当数据库异常时，Sentinel fallback 会调用这个方法，
     * 返回缓存中的旧数据，而不是抛出 500 错误。
     * </p>
     */
    @Override
    public ProductDetailVO getCachedProductDetail(Long productId) {
        // 1. 先查Caffeine本地缓存
        ProductDetailVO detail = localCache.getIfPresent(productId);
        if (detail != null) {
            log.info("降级时Caffeine本地缓存命中: productId={}", productId);
            return detail;
        }

        // 2. 再查Redis分布式缓存（虽然当前未实现JSON缓存，保留接口供后续扩展）
        // TODO: 后续实现Redis JSON缓存后，这里可以查Redis

        // 3. 缓存都没有，返回null（调用方需处理null情况）
        log.warn("降级时缓存未命中，返回null: productId={}", productId);
        return null;
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

    /**
     * 缓存商品列表结果（RL-13 引入）
     * <p>
     * 把商品列表查询结果序列化成JSON存到Redis，5分钟后自动过期。
     * 缓存key根据 categoryId + pageNum + pageSize 生成，不同参数组合缓存各自的结果。
     * </p>
     *
     * @param categoryId    分类ID（可为null）
     * @param pageRequest   分页参数
     * @param pageResult    查询结果
     */
    @Override
    public void cacheProductList(Long categoryId, PageRequest pageRequest, PageResult<ProductVO> pageResult) {
        try {
            String key = buildListCacheKey(categoryId, pageRequest);
            String json = objectMapper.writeValueAsString(pageResult);
            stringRedisTemplate.opsForValue().set(key, json, LIST_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("商品列表已缓存: key={}", key);
        } catch (JsonProcessingException e) {
            // 序列化失败只记录日志，不影响正常流程（缓存是弱依赖）
            log.warn("商品列表缓存失败: categoryId={}, pageNum={}", categoryId, pageRequest.getPageNum(), e);
        }
    }

    /**
     * 只查缓存中的商品列表（RL-13 引入）
     * <p>
     * 当商品列表接口被限流时调用，从Redis读取之前缓存的列表数据。
     * 缓存未命中返回 null，调用方需处理 null 的情况。
     * </p>
     *
     * @param categoryId   分类ID（可为null）
     * @param pageRequest  分页参数
     * @return 缓存的商品列表，缓存未命中返回 null
     */
    @Override
    public PageResult<ProductVO> getCachedProductList(Long categoryId, PageRequest pageRequest) {
        try {
            String key = buildListCacheKey(categoryId, pageRequest);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                log.info("商品列表缓存未命中: categoryId={}, pageNum={}", categoryId, pageRequest.getPageNum());
                return null;
            }
            // 反序列化成 PageResult<ProductVO>
            PageResult<ProductVO> result = objectMapper.readValue(json, new TypeReference<PageResult<ProductVO>>() {});
            log.info("商品列表缓存命中: categoryId={}, pageNum={}", categoryId, pageRequest.getPageNum());
            return result;
        } catch (Exception e) {
            log.warn("读取商品列表缓存失败: categoryId={}, pageNum={}", categoryId, pageRequest.getPageNum(), e);
            return null;
        }
    }

    /**
     * 构建商品列表缓存key
     * <p>
     * key格式：product:list:{categoryId}:{pageNum}:{pageSize}
     * categoryId为null时用"all"代替，保证key唯一性。
     * </p>
     *
     * @param categoryId   分类ID（可为null）
     * @param pageRequest  分页参数
     * @return Redis缓存key
     */
    private String buildListCacheKey(Long categoryId, PageRequest pageRequest) {
        String categoryPart = categoryId != null ? String.valueOf(categoryId) : "all";
        return PRODUCT_LIST_CACHE_KEY + categoryPart + ":" + pageRequest.getPageNum() + ":" + pageRequest.getPageSize();
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
        // N-P 性能测试整改：查询并设置 merchantId（带 Caffeine 本地缓存，避免每次 Feign 调用）
        vo.setMerchantId(queryMerchantIdByProductId(sku.getProductId()));
        return vo;
    }

    /**
     * 根据商品ID查询商家ID（带 Caffeine 本地缓存优化）
     * <p>
     * N-P 性能测试整改：原 convertSkuToVO 未设置 merchantId，导致商品详情接口返回的 SKU 中
     * merchantId 始终为 null。此方法与 ProductServiceImpl 中的实现保持一致：
     * 1. 查商品 SPU 拿 shopId
     * 2. 先查 Caffeine 缓存（命中率 >99%）
     * 3. 缓存未命中走 Feign 调用商家服务，结果写入缓存
     * 4. 任何异常降级返回 null，不阻塞主流程
     * </p>
     *
     * @param productId 商品ID
     * @return 商家ID，查询失败返回 null
     */
    private Long queryMerchantIdByProductId(Long productId) {
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

            // 4. 取出merchantId并写入缓存
            Long merchantId = shopResult.getData().getMerchantId();
            if (merchantId != null) {
                shopIdToMerchantIdCache.put(shopId, merchantId);
                log.debug("shopId→merchantId 缓存写入: shopId={}, merchantId={}", shopId, merchantId);
            }

            return merchantId;
        } catch (Exception e) {
            log.warn("查询merchantId异常，不影响主流程: productId={}", productId, e);
            return null;
        }
    }
}
