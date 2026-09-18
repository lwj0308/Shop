package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.entity.*;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;
import com.shop.product.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 商品缓存服务（ProductCacheServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证商品详情的二级缓存（Caffeine + Redis）读写、删除、延迟双删等逻辑。
 * 简单理解：我们把所有外部依赖（Mapper、RedisTemplate）都"假装"一下（Mock），
 * 而 Caffeine 本地缓存和 ObjectMapper 是真实的，这样能测出真实的缓存命中/未命中行为。
 * </p>
 * <p>
 * 小白快速理解：
 * - Caffeine：JVM 本地缓存，速度最快。这里用真实的 Caffeine 实例测试，不 Mock。
 * - RedisTemplate：分布式缓存。这里用 Mock，因为单测不连真实 Redis。
 * - ObjectMapper：JSON 序列化工具。这里用真实的，方便测序列化/反序列化。
 * - @InjectMocks 会把 8 个 Mock 依赖通过构造方法注入，Caffeine 和 ObjectMapper 由字段初始化器自动创建。
 * </p>
 * <p>
 * 覆盖的方法：getProductDetailFromCache、getCachedProductDetail、evictProductDetailCache、
 * delayDoubleEvict、cacheProductList、getCachedProductList
 * </p>
 */
@DisplayName("商品缓存服务 ProductCacheServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCacheServiceImplTest {

    // ==================== Mock 依赖（都是假的，不真的连数据库/Redis） ====================

    /** Redis模板，操作分布式缓存 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    /** 商品Mapper，查数据库用 */
    @Mock
    private ProductMapper productMapper;
    /** SKU Mapper */
    @Mock
    private ProductSkuMapper productSkuMapper;
    /** 规格模板Mapper */
    @Mock
    private ProductSpecMapper productSpecMapper;
    /** 规格值Mapper */
    @Mock
    private ProductSpecValueMapper productSpecValueMapper;
    /** 评价Mapper */
    @Mock
    private ProductCommentMapper productCommentMapper;
    /** 分类Mapper */
    @Mock
    private CategoryMapper categoryMapper;
    /** 品牌Mapper */
    @Mock
    private BrandMapper brandMapper;
    /** Redis的Value操作对象，stringRedisTemplate.opsForValue()返回的就是它 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 被测试的缓存服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private ProductCacheServiceImpl productCacheService;

    /** 常用测试数据：商品ID */
    private static final Long PRODUCT_ID = 1001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：loadProductDetailFromDB 方法里用了 LambdaQueryWrapper，
     * 比如 .eq(ProductSku::getProductId, ...)。这些代码会让 MyBatis-Plus 去查
     * "productId 字段对应数据库哪一列"。正常启动 Spring 时框架会自动做这件事，
     * 但单元测试没有 Spring 环境，所以需要我们手动告诉 MyBatis-Plus：
     * ProductSku、ProductSpec 等实体有哪些字段、对应哪些列。
     * 不初始化会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        // 初始化所有用到 Lambda 查询的实体的缓存
        TableInfoHelper.initTableInfo(assistant, Product.class);
        TableInfoHelper.initTableInfo(assistant, ProductSku.class);
        TableInfoHelper.initTableInfo(assistant, ProductSpec.class);
        TableInfoHelper.initTableInfo(assistant, ProductSpecValue.class);
        TableInfoHelper.initTableInfo(assistant, ProductComment.class);
    }

    /**
     * 每个测试方法执行前，清空 Caffeine 本地缓存
     * <p>
     * 小白理解：Caffeine 是真实的缓存实例，测试之间会共享数据。
     * 比如测试A往缓存放了商品1001，测试B再查商品1001就会命中缓存，导致测试B的"缓存未命中"验证失败。
     * 所以每个测试前都清一下，保证每个测试都是独立的。
     * </p>
     */
    @BeforeEach
    void clearLocalCache() {
        Cache<Long, ProductDetailVO> cache = (Cache<Long, ProductDetailVO>)
                ReflectionTestUtils.getField(productCacheService, "localCache");
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个商品SPU实体（带品牌、分类）
     *
     * @return 构造好的Product实体
     */
    private Product buildProduct() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCategoryId(100L);
        product.setBrandId(10L);
        product.setShopId(2001L);
        product.setName("测试商品");
        product.setSubtitle("测试副标题");
        product.setMainImage("main.jpg");
        product.setImages(Arrays.asList("img1.jpg", "img2.jpg"));
        product.setDetail("<p>详情</p>");
        product.setStatus(1);
        return product;
    }

    /**
     * 构造一个SKU实体
     *
     * @param id          SKU ID
     * @param specValues  规格组合，如 {"颜色":"红色"}
     * @return 构造好的ProductSku实体
     */
    private ProductSku buildSku(Long id, Map<String, String> specValues) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(PRODUCT_ID);
        sku.setPrice(new BigDecimal("99.99"));
        sku.setOriginalPrice(new BigDecimal("199.00"));
        sku.setStock(50);
        sku.setStatus(1);
        sku.setSpecValues(specValues);
        return sku;
    }

    /**
     * 构造一个评价实体
     *
     * @param id     评价ID
     * @param score  评分（1-5分）
     * @return 构造好的ProductComment实体
     */
    private ProductComment buildComment(Long id, int score) {
        ProductComment comment = new ProductComment();
        comment.setId(id);
        comment.setProductId(PRODUCT_ID);
        comment.setScore(score);
        comment.setContent("评价内容");
        return comment;
    }

    /**
     * 往 Caffeine 本地缓存中放入一个商品详情
     * <p>
     * 小白理解：通过反射拿到 private 的 localCache 字段，直接往里塞数据，
     * 用来模拟"缓存命中"的场景。
     * </p>
     *
     * @param productId 商品ID
     * @param detail    商品详情
     */
    private void putInLocalCache(Long productId, ProductDetailVO detail) {
        Cache<Long, ProductDetailVO> cache = (Cache<Long, ProductDetailVO>)
                ReflectionTestUtils.getField(productCacheService, "localCache");
        if (cache != null) {
            cache.put(productId, detail);
        }
    }

    // ==================== 1. getProductDetailFromCache 三级缓存查询测试 ====================

    @Nested
    @DisplayName("getProductDetailFromCache 三级缓存查询")
    class GetProductDetailFromCacheTest {

        @Test
        @DisplayName("Caffeine本地缓存命中 → 直接返回，不查数据库")
        void getProductDetailFromCache_caffeineHit_returnDirectly() {
            // 场景：之前查过这个商品，数据已经缓存在 Caffeine 里了
            // 这次查应该直接返回缓存数据，不需要查数据库
            ProductDetailVO cached = new ProductDetailVO();
            cached.setId(PRODUCT_ID);
            cached.setName("缓存中的商品");
            putInLocalCache(PRODUCT_ID, cached);

            ProductDetailVO result = productCacheService.getProductDetailFromCache(PRODUCT_ID);

            // 验证返回的是缓存里的数据
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(PRODUCT_ID);
            assertThat(result.getName()).isEqualTo("缓存中的商品");
            // 验证没有查数据库（productMapper.selectById 没被调用）
            verify(productMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("缓存未命中+数据库查到 → 加载完整详情并写入本地缓存")
        void getProductDetailFromCache_dbHit_loadAndCache() {
            // 场景：缓存里没有，需要从数据库加载商品详情
            // 验证组装的VO包含：分类名、品牌名、SKU列表、规格列表、评价摘要
            Product product = buildProduct();
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            // 模拟查分类
            Category category = new Category();
            category.setId(100L);
            category.setName("手机数码");
            when(categoryMapper.selectById(100L)).thenReturn(category);

            // 模拟查品牌
            Brand brand = new Brand();
            brand.setId(10L);
            brand.setName("Apple");
            when(brandMapper.selectById(10L)).thenReturn(brand);

            // 模拟查SKU列表（1个SKU）
            Map<String, String> specValues = new HashMap<>();
            specValues.put("颜色", "红色");
            ProductSku sku = buildSku(5001L, specValues);
            when(productSkuMapper.selectList(any())).thenReturn(Collections.singletonList(sku));

            // 模拟查规格列表（1个规格，2个规格值）
            ProductSpec spec = new ProductSpec();
            spec.setId(2001L);
            spec.setProductId(PRODUCT_ID);
            spec.setName("颜色");
            when(productSpecMapper.selectList(any())).thenReturn(Collections.singletonList(spec));

            ProductSpecValue specValue1 = new ProductSpecValue();
            specValue1.setId(3001L);
            specValue1.setSpecId(2001L);
            specValue1.setValue("红色");
            ProductSpecValue specValue2 = new ProductSpecValue();
            specValue2.setId(3002L);
            specValue2.setSpecId(2001L);
            specValue2.setValue("蓝色");
            when(productSpecValueMapper.selectList(any()))
                    .thenReturn(Arrays.asList(specValue1, specValue2));

            // 模拟查评价（2条评价：5分和3分）
            when(productCommentMapper.selectCount(any())).thenReturn(2L);
            when(productCommentMapper.selectList(any()))
                    .thenReturn(Arrays.asList(buildComment(1L, 5), buildComment(2L, 3)));

            ProductDetailVO result = productCacheService.getProductDetailFromCache(PRODUCT_ID);

            // 验证基本信息
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(PRODUCT_ID);
            assertThat(result.getName()).isEqualTo("测试商品");
            assertThat(result.getCategoryName()).isEqualTo("手机数码");
            assertThat(result.getBrandName()).isEqualTo("Apple");

            // 验证SKU列表
            assertThat(result.getSkus()).hasSize(1);
            assertThat(result.getSkus().get(0).getId()).isEqualTo(5001L);
            assertThat(result.getSkus().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));

            // 验证最低价和总库存（从SKU计算）
            assertThat(result.getMinPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(result.getTotalStock()).isEqualTo(50);

            // 验证规格列表（从product_spec表查到）
            assertThat(result.getSpecs()).hasSize(1);
            assertThat(result.getSpecs().get(0).getName()).isEqualTo("颜色");
            assertThat(result.getSpecs().get(0).getValues()).containsExactly("红色", "蓝色");

            // 验证评价摘要（2条评价：5分+3分，平均分4.0，好评率50%）
            assertThat(result.getCommentSummary()).isNotNull();
            assertThat(result.getCommentSummary().getTotalCount()).isEqualTo(2L);
            assertThat(result.getCommentSummary().getAvgScore()).isEqualByComparingTo(new BigDecimal("4.0"));
            assertThat(result.getCommentSummary().getGoodRate()).isEqualByComparingTo(new BigDecimal("50.0"));

            // 验证数据已写入本地缓存（再查一次，不应再查数据库）
            verify(productMapper, times(1)).selectById(PRODUCT_ID);
            productCacheService.getProductDetailFromCache(PRODUCT_ID);
            verify(productMapper, times(1)).selectById(PRODUCT_ID);
        }

        @Test
        @DisplayName("缓存未命中+数据库查不到商品 → 抛出 PRODUCT_NOT_FOUND 异常")
        void getProductDetailFromCache_dbNotFound_throwsException() {
            // 场景：查一个不存在的商品，数据库返回null，应抛出业务异常
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

            assertThatThrownBy(() -> productCacheService.getProductDetailFromCache(PRODUCT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());

            // 验证没有查分类、品牌等关联数据（因为商品不存在，提前返回了）
            verify(categoryMapper, never()).selectById(anyLong());
            verify(brandMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("商品无品牌无评价 → brandName为null，评价默认5分100%好评")
        void getProductDetailFromCache_noBrandNoComment_useDefaults() {
            // 场景：商品没有关联品牌，也没有任何评价
            // 验证 brandName 为 null，评价摘要用默认值（平均分5.0，好评率100%）
            Product product = buildProduct();
            product.setBrandId(null); // 无品牌
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            Category category = new Category();
            category.setId(100L);
            category.setName("手机数码");
            when(categoryMapper.selectById(100L)).thenReturn(category);

            // SKU为空列表
            when(productSkuMapper.selectList(any())).thenReturn(Collections.emptyList());
            // 规格为空列表
            when(productSpecMapper.selectList(any())).thenReturn(Collections.emptyList());
            // 评价数为0
            when(productCommentMapper.selectCount(any())).thenReturn(0L);

            ProductDetailVO result = productCacheService.getProductDetailFromCache(PRODUCT_ID);

            assertThat(result).isNotNull();
            // 无品牌时 brandName 为 null
            assertThat(result.getBrandName()).isNull();
            // 品牌Mapper不应被调用（brandId为null时跳过）
            verify(brandMapper, never()).selectById(anyLong());
            // 评价摘要用默认值
            assertThat(result.getCommentSummary().getTotalCount()).isEqualTo(0L);
            assertThat(result.getCommentSummary().getAvgScore()).isEqualByComparingTo(new BigDecimal("5.0"));
            assertThat(result.getCommentSummary().getGoodRate()).isEqualByComparingTo(new BigDecimal("100.0"));
            // 无SKU时不计算最低价和总库存
            assertThat(result.getMinPrice()).isNull();
            assertThat(result.getTotalStock()).isNull();
        }

        @Test
        @DisplayName("product_spec表无数据时，从SKU的specValues动态生成规格列表")
        void getProductDetailFromCache_noSpecTable_generateFromSku() {
            // 场景：product_spec表没有规格数据，但SKU有specValues
            // 验证从SKU的specValues动态生成规格列表（保证前端规格选择器能正常显示）
            Product product = buildProduct();
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            when(categoryMapper.selectById(100L)).thenReturn(null);

            // 2个SKU，规格值不同
            Map<String, String> spec1 = new HashMap<>();
            spec1.put("颜色", "红色");
            spec1.put("存储", "128G");
            ProductSku sku1 = buildSku(5001L, spec1);

            Map<String, String> spec2 = new HashMap<>();
            spec2.put("颜色", "蓝色");
            spec2.put("存储", "256G");
            ProductSku sku2 = buildSku(5002L, spec2);

            when(productSkuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, sku2));

            // product_spec表为空
            when(productSpecMapper.selectList(any())).thenReturn(Collections.emptyList());
            // 评价数为0
            when(productCommentMapper.selectCount(any())).thenReturn(0L);

            ProductDetailVO result = productCacheService.getProductDetailFromCache(PRODUCT_ID);

            // 验证从SKU动态生成的规格列表
            assertThat(result.getSpecs()).hasSize(2);
            // 验证"颜色"规格有两个值：红色、蓝色（用LinkedHashSet去重保序）
            ProductVO.SpecVO colorSpec = result.getSpecs().stream()
                    .filter(s -> "颜色".equals(s.getName())).findFirst().orElse(null);
            assertThat(colorSpec).isNotNull();
            assertThat(colorSpec.getValues()).containsExactly("红色", "蓝色");
            // 验证"存储"规格有两个值：128G、256G
            ProductVO.SpecVO storageSpec = result.getSpecs().stream()
                    .filter(s -> "存储".equals(s.getName())).findFirst().orElse(null);
            assertThat(storageSpec).isNotNull();
            assertThat(storageSpec.getValues()).containsExactly("128G", "256G");
        }
    }

    // ==================== 2. getCachedProductDetail 降级查询测试 ====================

    @Nested
    @DisplayName("getCachedProductDetail 降级查询（只查缓存不查DB）")
    class GetCachedProductDetailTest {

        @Test
        @DisplayName("Caffeine本地缓存命中 → 返回缓存数据")
        void getCachedProductDetail_caffeineHit_returnData() {
            // 场景：数据库挂了走降级，但Caffeine里有旧数据，返回旧数据
            ProductDetailVO cached = new ProductDetailVO();
            cached.setId(PRODUCT_ID);
            cached.setName("降级时的旧数据");
            putInLocalCache(PRODUCT_ID, cached);

            ProductDetailVO result = productCacheService.getCachedProductDetail(PRODUCT_ID);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("降级时的旧数据");
            // 验证没有查数据库
            verify(productMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("缓存未命中 → 返回null（降级时不查数据库）")
        void getCachedProductDetail_cacheMiss_returnNull() {
            // 场景：数据库挂了走降级，缓存里也没有数据，返回null
            // 调用方需要处理null的情况（比如返回"商品信息暂时不可用"）
            ProductDetailVO result = productCacheService.getCachedProductDetail(PRODUCT_ID);

            assertThat(result).isNull();
            // 验证没有查数据库（降级方法的核心：绝不查DB）
            verify(productMapper, never()).selectById(anyLong());
        }
    }

    // ==================== 3. evictProductDetailCache 删除双层缓存测试 ====================

    @Nested
    @DisplayName("evictProductDetailCache 删除双层缓存")
    class EvictProductDetailCacheTest {

        @Test
        @DisplayName("删除缓存 → 同时清除Caffeine本地缓存和Redis缓存")
        void evictProductDetailCache_clearBothCaches() {
            // 场景：商品更新后删除缓存，确保下次查询从数据库重新加载
            // 先往Caffeine里放点数据
            ProductDetailVO cached = new ProductDetailVO();
            cached.setId(PRODUCT_ID);
            putInLocalCache(PRODUCT_ID, cached);

            // 执行删除
            productCacheService.evictProductDetailCache(PRODUCT_ID);

            // 验证删除了Redis缓存（key格式：product:detail:{productId}）
            verify(stringRedisTemplate).delete("product:detail:" + PRODUCT_ID);

            // 验证Caffeine本地缓存也被清空了（通过getIfPresent检查）
            Cache<Long, ProductDetailVO> cache = (Cache<Long, ProductDetailVO>)
                    ReflectionTestUtils.getField(productCacheService, "localCache");
            assertThat(cache).isNotNull();
            assertThat(cache.getIfPresent(PRODUCT_ID)).isNull();
        }
    }

    // ==================== 4. delayDoubleEvict 延迟双删测试 ====================

    @Nested
    @DisplayName("delayDoubleEvict 延迟双删")
    class DelayDoubleEvictTest {

        @Test
        @DisplayName("延迟双删 → 立即删一次，500ms后异步再删一次")
        void delayDoubleEvict_deleteTwiceWithDelay() throws InterruptedException {
            // 场景：并发更新商品时，先删一次缓存，延迟500ms再删一次
            // 解决并发场景下旧数据被重新写入缓存的问题
            // 小白理解：删两次是为了防止"线程A删了缓存→线程B把旧值写回缓存"的问题

            // 执行延迟双删
            productCacheService.delayDoubleEvict(PRODUCT_ID);

            // 验证立即执行了第一次删除
            verify(stringRedisTemplate, times(1)).delete("product:detail:" + PRODUCT_ID);

            // 等待异步线程执行第二次删除（延迟500ms，等1.5秒确保执行完）
            Thread.sleep(1500);

            // 验证总共删除了两次（第一次同步 + 第二次异步延迟）
            verify(stringRedisTemplate, times(2)).delete("product:detail:" + PRODUCT_ID);
        }
    }

    // ==================== 5. cacheProductList 列表缓存写入测试 ====================

    @Nested
    @DisplayName("cacheProductList 列表缓存写入")
    class CacheProductListTest {

        @Test
        @DisplayName("正常缓存商品列表 → 序列化JSON写入Redis（带5分钟过期时间）")
        void cacheProductList_normal_writeToRedis() {
            // 场景：查询商品列表后，把结果缓存到Redis，5分钟后自动过期
            // 小白理解：把PageResult转成JSON字符串存到Redis，key按分类+页码生成
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            PageResult<ProductVO> pageResult = new PageResult<>();
            pageResult.setTotal(100L);
            pageResult.setPageNum(1);
            pageResult.setPageSize(10);
            ProductVO vo = new ProductVO();
            vo.setId(PRODUCT_ID);
            vo.setName("测试商品");
            pageResult.setRecords(Collections.singletonList(vo));

            // 执行缓存写入
            productCacheService.cacheProductList(100L, pageRequest, pageResult);

            // 验证写入了Redis，key格式：product:list:{categoryId}:{pageNum}:{pageSize}
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(),
                    eq(5L), eq(TimeUnit.MINUTES));

            // 验证key格式正确
            assertThat(keyCaptor.getValue()).isEqualTo("product:list:100:1:10");
            // 验证JSON不为空且包含商品名称
            assertThat(jsonCaptor.getValue()).isNotEmpty();
            assertThat(jsonCaptor.getValue()).contains("测试商品");
        }

        @Test
        @DisplayName("categoryId为null → key中使用\"all\"代替分类ID")
        void cacheProductList_nullCategoryId_keyUsesAll() {
            // 场景：不按分类查询全站商品时，categoryId为null
            // key中的分类部分用"all"代替，保证key唯一性
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(2);
            pageRequest.setPageSize(20);

            PageResult<ProductVO> pageResult = PageResult.empty();

            productCacheService.cacheProductList(null, pageRequest, pageResult);

            // 验证key格式：product:list:all:{pageNum}:{pageSize}
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(keyCaptor.capture(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
            assertThat(keyCaptor.getValue()).isEqualTo("product:list:all:2:20");
        }

        @Test
        @DisplayName("序列化失败 → 不抛异常，不写入Redis（缓存是弱依赖）")
        void cacheProductList_serializeFail_noException() throws Exception {
            // 场景：ObjectMapper序列化PageResult时失败了（比如数据有循环引用）
            // 缓存是弱依赖，序列化失败只记录日志，不影响主流程
            // 小白理解：缓存挂了就挂了，不能影响商品列表的正常查询

            // 用Mock的ObjectMapper替换真实的，模拟序列化失败
            ObjectMapper originalMapper = (ObjectMapper)
                    ReflectionTestUtils.getField(productCacheService, "objectMapper");
            ObjectMapper mockMapper = mock(ObjectMapper.class);
            // 模拟 writeValueAsString 抛出 JsonProcessingException
            doThrow(new JsonProcessingException("模拟序列化失败") {})
                    .when(mockMapper).writeValueAsString(any());
            ReflectionTestUtils.setField(productCacheService, "objectMapper", mockMapper);

            try {
                PageRequest pageRequest = new PageRequest();
                pageRequest.setPageNum(1);
                pageRequest.setPageSize(10);
                PageResult<ProductVO> pageResult = PageResult.empty();

                // 不应抛异常
                productCacheService.cacheProductList(100L, pageRequest, pageResult);

                // 验证没有写入Redis（因为序列化失败了）
                verify(stringRedisTemplate, never()).opsForValue();
            } finally {
                // 恢复原始的ObjectMapper，避免影响其他测试
                ReflectionTestUtils.setField(productCacheService, "objectMapper", originalMapper);
            }
        }
    }

    // ==================== 6. getCachedProductList 列表缓存读取测试 ====================

    @Nested
    @DisplayName("getCachedProductList 列表缓存读取")
    class GetCachedProductListTest {

        @Test
        @DisplayName("缓存命中 → 反序列化JSON返回PageResult")
        void getCachedProductList_cacheHit_returnPageResult() throws Exception {
            // 场景：商品列表接口被限流了，从Redis读取之前缓存的列表数据
            // 小白理解：限流时返回旧数据，比直接报错体验好
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

            // 用真实的ObjectMapper序列化一个PageResult，作为Redis返回的JSON
            ObjectMapper realMapper = new ObjectMapper();
            realMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            PageResult<ProductVO> original = new PageResult<>();
            original.setTotal(50L);
            original.setPageNum(1);
            original.setPageSize(10);
            ProductVO vo = new ProductVO();
            vo.setId(PRODUCT_ID);
            vo.setName("缓存的商品");
            original.setRecords(Collections.singletonList(vo));
            String json = realMapper.writeValueAsString(original);

            // 模拟Redis返回这个JSON
            when(valueOperations.get("product:list:100:1:10")).thenReturn(json);

            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            PageResult<ProductVO> result = productCacheService.getCachedProductList(100L, pageRequest);

            // 验证反序列化成功
            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(50L);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getName()).isEqualTo("缓存的商品");
        }

        @Test
        @DisplayName("缓存未命中（Redis返回null） → 返回null")
        void getCachedProductList_cacheMiss_returnNull() {
            // 场景：Redis里没有缓存的列表数据（第一次查询或缓存已过期）
            // 返回null，调用方需要处理null（比如正常查数据库）
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            PageResult<ProductVO> result = productCacheService.getCachedProductList(100L, pageRequest);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("反序列化失败（JSON格式错误） → 返回null（异常容错）")
        void getCachedProductList_deserializeFail_returnNull() {
            // 场景：Redis里的JSON数据损坏了，反序列化失败
            // 返回null而不是抛异常，保证限流降级时不会因为脏数据崩溃
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // 模拟Redis返回无效的JSON
            when(valueOperations.get(anyString())).thenReturn("这不是有效的JSON{{{");

            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            PageResult<ProductVO> result = productCacheService.getCachedProductList(100L, pageRequest);

            // 验证返回null而不是抛异常
            assertThat(result).isNull();
        }
    }
}
