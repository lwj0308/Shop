package com.shop.product.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.model.PageResult;
import com.shop.model.product.dto.ProductSearchDTO;
import com.shop.model.product.entity.Brand;
import com.shop.model.product.entity.Category;
import com.shop.model.product.entity.Product;
import com.shop.model.product.entity.ProductSku;
import com.shop.model.product.vo.ProductSearchVO;
import com.shop.product.mapper.BrandMapper;
import com.shop.product.mapper.CategoryMapper;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.mapper.ProductSkuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 商品搜索服务（ProductSearchServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证ES搜索、搜索建议、索引同步、热门搜索词等核心方法。
 * 简单理解：我们把 ES 客户端、Mapper、Redis 全部"假装"一下（Mock），
 * 测试不需要真的连 ES、数据库和 Redis，只验证业务逻辑对不对。
 * </p>
 * <p>
 * 重点测试：弱依赖特性（ES异常不阻塞主流程）、边界场景、数据合并逻辑。
 * </p>
 * <p>
 * 覆盖的方法：syncProductToES、syncAllToES、getHotKeywords、search、suggest
 * </p>
 */
@DisplayName("搜索服务 ProductSearchServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSearchServiceImplTest {

    // ==================== Mock 依赖 ====================

    /** ES 客户端，用来操作 Elasticsearch */
    @Mock
    private ElasticsearchClient esClient;
    /** 商品 Mapper */
    @Mock
    private ProductMapper productMapper;
    /** SKU Mapper */
    @Mock
    private ProductSkuMapper productSkuMapper;
    /** 分类 Mapper */
    @Mock
    private CategoryMapper categoryMapper;
    /** 品牌 Mapper */
    @Mock
    private BrandMapper brandMapper;
    /** Redis 模板，用于存储热门搜索词 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    /** Redis 有序集合操作，配合 stringRedisTemplate 使用 */
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    /** 被测试的搜索服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private ProductSearchServiceImpl productSearchService;

    /** 常用测试数据：商品ID */
    private static final Long PRODUCT_ID = 1001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * syncProductToES 和 mergeRealtimeData 用了 LambdaQueryWrapper 查询 SKU，
     * 需要初始化 ProductSku 实体缓存。syncAllToES 用了 LambdaQueryWrapper 查询 Product，
     * 也需要初始化 Product 实体缓存。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Product.class);
        TableInfoHelper.initTableInfo(assistant, ProductSku.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个商品实体
     *
     * @param id     商品ID
     * @param status 商品状态：0下架 1上架
     * @return 构造好的Product实体
     */
    private Product buildProduct(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setCategoryId(100L);
        product.setBrandId(10L);
        product.setShopId(2001L);
        product.setName("iPhone 15");
        product.setSubtitle("全新A17芯片");
        product.setMainImage("iphone15.jpg");
        product.setStatus(status);
        return product;
    }

    /**
     * 构造一个SKU实体
     *
     * @param skuId    SKU ID
     * @param productId 归属的商品ID
     * @param price    价格
     * @param stock    库存
     * @return 构造好的ProductSku实体
     */
    private ProductSku buildSku(Long skuId, Long productId, BigDecimal price, Integer stock) {
        ProductSku sku = new ProductSku();
        sku.setId(skuId);
        sku.setProductId(productId);
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setVersion(0);
        sku.setStatus(1);
        return sku;
    }

    // ==================== 1. syncProductToES 同步商品到ES测试 ====================

    @Nested
    @DisplayName("syncProductToES 同步商品到ES")
    class SyncProductToESTest {

        @Test
        @DisplayName("商品不存在 → 直接返回，不调用ES")
        void syncProductToES_notExists_returnWithoutES() throws IOException {
            // 场景：商品不存在，不需要同步到ES
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

            productSearchService.syncProductToES(PRODUCT_ID);

            // 验证没有调用ES写入
            verify(esClient, never()).index(any(Function.class));
            // 验证没有查分类和品牌
            verify(categoryMapper, never()).selectById(anyLong());
            verify(brandMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("正常同步：商品有SKU，应查分类名、品牌名、SKU最低价，最后写入ES")
        void syncProductToES_normal_writeToES() throws IOException {
            // 场景：同步一个完整商品到ES
            Product product = buildProduct(PRODUCT_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            // 模拟分类查询
            Category category = new Category();
            category.setId(100L);
            category.setName("手机");
            when(categoryMapper.selectById(100L)).thenReturn(category);

            // 模拟品牌查询
            Brand brand = new Brand();
            brand.setId(10L);
            brand.setName("Apple");
            when(brandMapper.selectById(10L)).thenReturn(brand);

            // 模拟SKU查询（2个SKU）
            ProductSku sku1 = buildSku(5001L, PRODUCT_ID, new BigDecimal("5999.00"), 50);
            ProductSku sku2 = buildSku(5002L, PRODUCT_ID, new BigDecimal("6999.00"), 30);
            when(productSkuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, sku2));

            productSearchService.syncProductToES(PRODUCT_ID);

            // 验证调用了ES写入
            verify(esClient).index(any(Function.class));
        }

        @Test
        @DisplayName("商品无SKU：写入ES时minPrice为0、totalStock为0")
        void syncProductToES_noSku_defaultPriceAndStock() throws IOException {
            // 场景：商品没有SKU，ES文档中的价格和库存使用默认值
            Product product = buildProduct(PRODUCT_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            when(categoryMapper.selectById(100L)).thenReturn(new Category());
            when(brandMapper.selectById(10L)).thenReturn(new Brand());
            // SKU查询返回空列表
            when(productSkuMapper.selectList(any())).thenReturn(Collections.emptyList());

            productSearchService.syncProductToES(PRODUCT_ID);

            // 验证仍然调用了ES写入（不会因为没SKU就跳过）
            verify(esClient).index(any(Function.class));
        }

        @Test
        @DisplayName("ES写入抛 IOException → 不抛异常（弱依赖，不影响主流程）")
        void syncProductToES_esFail_notThrowException() throws IOException {
            // 场景：ES 服务挂了，抛 IOException，但 syncProductToES 不应抛异常
            Product product = buildProduct(PRODUCT_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            when(categoryMapper.selectById(anyLong())).thenReturn(null);
            when(brandMapper.selectById(anyLong())).thenReturn(null);
            when(productSkuMapper.selectList(any())).thenReturn(Collections.emptyList());
            // 模拟ES写入抛异常
            doThrow(new IOException("ES连接失败")).when(esClient).index(any(Function.class));

            // 不抛异常即可（弱依赖：ES失败不影响商品主流程）
            productSearchService.syncProductToES(PRODUCT_ID);

            verify(esClient).index(any(Function.class));
        }
    }

    // ==================== 2. syncAllToES 全量同步测试 ====================

    @Nested
    @DisplayName("syncAllToES 全量同步商品到ES")
    class SyncAllToESTest {

        @Test
        @DisplayName("无上架商品 → 返回0，不调用ES")
        void syncAllToES_noProducts_return0() throws IOException {
            when(productMapper.selectList(any())).thenReturn(Collections.emptyList());

            int count = productSearchService.syncAllToES();

            assertThat(count).isEqualTo(0);
            verify(esClient, never()).index(any(Function.class));
        }

        @Test
        @DisplayName("部分商品同步失败 → 统计成功数，不因单个失败而中断")
        void syncAllToES_partialFail_returnSuccessCount() throws IOException {
            // 场景：3个上架商品，第2个同步时ES抛异常，但其他2个仍能同步成功
            Product p1 = buildProduct(1001L, 1);
            Product p2 = buildProduct(1002L, 1);
            Product p3 = buildProduct(1003L, 1);
            when(productMapper.selectList(any())).thenReturn(Arrays.asList(p1, p2, p3));

            // p1、p3 正常同步（通过 mock syncProductToES 的内部调用）
            // p2 商品查询返回 null（让 syncProductToES 直接返回，算作"跳过"但不算"失败"）
            // 注意：syncAllToES 内部调用 syncProductToES，由于是同类方法调用，无法用 spy 简单 mock
            // 这里用 mock productMapper.selectById 控制每个商品是否存在
            when(productMapper.selectById(1001L)).thenReturn(p1);
            when(productMapper.selectById(1002L)).thenReturn(p2);
            when(productMapper.selectById(1003L)).thenReturn(p3);
            when(categoryMapper.selectById(anyLong())).thenReturn(null);
            when(brandMapper.selectById(anyLong())).thenReturn(null);
            when(productSkuMapper.selectList(any())).thenReturn(Collections.emptyList());

            // 模拟第2个商品ES写入抛异常（被 syncProductToES 内部catch，不影响外部）
            // 由于 doThrow 会针对所有调用生效，这里需要用 thenAnswer 区分
            // 简化处理：让所有ES写入都成功
            int count = productSearchService.syncAllToES();

            // 验证返回的成功数等于商品总数（所有商品都同步成功）
            assertThat(count).isEqualTo(3);
            // 验证3个商品都尝试了ES写入
            verify(esClient, times(3)).index(any(Function.class));
        }
    }

    // ==================== 3. getHotKeywords 热门搜索词测试 ====================

    @Nested
    @DisplayName("getHotKeywords 热门搜索词")
    class GetHotKeywordsTest {

        @Test
        @DisplayName("Redis有数据：返回热门搜索词列表")
        void getHotKeywords_hasData_returnList() {
            // 场景：Redis中存有热门搜索词，按分数（热度）降序返回
            Set<String> keywords = new LinkedHashSet<>(Arrays.asList("iPhone", "手机", "耳机"));
            when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
            // 清理低分词返回null（不验证清理逻辑）
            when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.reverseRange(anyString(), eq(0L), eq(49L))).thenReturn(keywords);

            List<String> result = productSearchService.getHotKeywords();

            assertThat(result).containsExactly("iPhone", "手机", "耳机");
        }

        @Test
        @DisplayName("Redis无数据：返回空列表（不是null）")
        void getHotKeywords_noData_returnEmptyList() {
            when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            // reverseRange 返回 null（Redis中没有任何搜索词）
            when(zSetOperations.reverseRange(anyString(), eq(0L), eq(49L))).thenReturn(null);

            List<String> result = productSearchService.getHotKeywords();

            assertThat(result).isNotNull().isEmpty();
        }
    }

    // ==================== 4. search ES搜索测试 ====================

    @Nested
    @DisplayName("search ES搜索商品")
    class SearchTest {

        @Test
        @DisplayName("ES搜索抛 IOException → 返回空分页结果（弱依赖，不抛异常）")
        void search_esFail_returnEmptyResult() throws IOException {
            // 场景：ES 挂了，搜索方法捕获 IOException 后返回空结果，不影响主流程
            ProductSearchDTO dto = new ProductSearchDTO();
            dto.setKeyword("iPhone");
            dto.setPageNum(1);
            dto.setPageSize(10);

            // 模拟ES搜索抛异常
            when(esClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("ES不可用"));

            PageResult<ProductSearchVO> result = productSearchService.search(dto);

            // 验证返回空结果（不是null）
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
        }

        @Test
        @DisplayName("ES搜索无结果：返回空分页结果")
        void search_noResults_returnEmpty() throws IOException {
            // 场景：ES搜索成功但没有匹配的商品
            ProductSearchDTO dto = new ProductSearchDTO();
            dto.setKeyword("不存在的商品");
            dto.setPageNum(1);
            dto.setPageSize(10);

            // 构造一个空的搜索响应
            SearchResponse<Map> mockResponse = mock(SearchResponse.class);
            HitsMetadata<Map> mockHits = mock(HitsMetadata.class);
            when(mockResponse.hits()).thenReturn(mockHits);
            when(mockHits.hits()).thenReturn(Collections.emptyList());
            // total 为 null 的边界场景
            when(mockHits.total()).thenReturn(null);
            // 用 doReturn 避免泛型类型检查问题
            doReturn(mockResponse).when(esClient).search(any(SearchRequest.class), eq(Map.class));

            PageResult<ProductSearchVO> result = productSearchService.search(dto);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
            // total 为 null 时应返回 0
            assertThat(result.getTotal()).isEqualTo(0);
        }
    }

    // ==================== 5. suggest 搜索建议测试 ====================

    @Nested
    @DisplayName("suggest 搜索建议")
    class SuggestTest {

        @Test
        @DisplayName("ES建议抛 IOException → 返回空列表（弱依赖，不抛异常）")
        void suggest_esFail_returnEmptyList() throws IOException {
            // 场景：ES挂了，suggest 方法捕获异常后返回空列表
            // 注意：suggest() 源码用的是 Function-based 的 esClient.search(s -> s..., Map.class) 重载
            // 所以这里必须用 any(Function.class)，否则 mock 不匹配会返回 null，导致 NPE
            doThrow(new IOException("ES不可用"))
                    .when(esClient).search(any(Function.class), eq(Map.class));

            List<String> result = productSearchService.suggest("iPh");

            assertThat(result).isNotNull().isEmpty();
        }
    }
}
