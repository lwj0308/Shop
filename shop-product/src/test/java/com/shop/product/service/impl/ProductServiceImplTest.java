package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.merchant.vo.ShopVO;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.dto.StockDeductItemDTO;
import com.shop.model.product.entity.*;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.product.feign.MerchantFeignClient;
import com.shop.product.mapper.*;
import com.shop.product.service.ProductCacheService;
import com.shop.product.service.StockService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
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

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 商品服务实现类（ProductServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证商品CRUD、上下架、库存扣减、推荐等核心方法。
 * 简单理解：我们把所有依赖（Mapper、Feign、MQ、缓存、库存服务）都"假装"一下（Mock），
 * 这样测试不需要真的连数据库、消息队列和远程服务，跑得又快又稳定。
 * </p>
 * <p>
 * 小白快速理解：
 * - JUnit 5：Java 最流行的测试框架
 * - Mockito：用来"假装"依赖的对象（Mock）
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * - @BeforeAll：所有测试运行前执行一次，这里用来初始化 MyBatis-Plus 缓存
 * </p>
 * <p>
 * 覆盖的方法：createProduct、updateProduct、onShelf、offShelf、deductStock、addStock、
 * deductStockWithIdempotent、getSkuById、getHotProducts、incrSales 等
 * </p>
 */
@DisplayName("商品服务 ProductServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    // ==================== Mock 依赖（都是假的，不真的连数据库/网络） ====================

    /** 商品SPU Mapper */
    @Mock
    private ProductMapper productMapper;
    /** SKU Mapper */
    @Mock
    private ProductSkuMapper productSkuMapper;
    /** 规格模板 Mapper */
    @Mock
    private ProductSpecMapper productSpecMapper;
    /** 规格值 Mapper */
    @Mock
    private ProductSpecValueMapper productSpecValueMapper;
    /** 商品图片 Mapper */
    @Mock
    private ProductImageMapper productImageMapper;
    /** 评价 Mapper */
    @Mock
    private ProductCommentMapper productCommentMapper;
    /** 分类 Mapper */
    @Mock
    private CategoryMapper categoryMapper;
    /** 品牌 Mapper */
    @Mock
    private BrandMapper brandMapper;
    /** 搜索服务（同步ES） */
    @Mock
    private com.shop.product.service.ProductSearchService productSearchService;
    /** 缓存服务（Redis+Caffeine二级缓存） */
    @Mock
    private ProductCacheService productCacheService;
    /** 库存服务（Redis Lua脚本扣库存） */
    @Mock
    private StockService stockService;
    /** RocketMQ模板，发送ES同步消息 */
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    /** 商家服务Feign客户端 */
    @Mock
    private MerchantFeignClient merchantFeignClient;
    /** 用户服务Feign客户端（记录足迹、猜你喜欢） */
    @Mock
    private com.shop.product.feign.UserFeignClient userFeignClient;

    /** 被测试的商品服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private ProductServiceImpl productService;

    /** 常用测试数据：店铺ID */
    private static final Long SHOP_ID = 2001L;
    /** 常用测试数据：商品ID */
    private static final Long PRODUCT_ID = 1001L;
    /** 常用测试数据：SKU ID */
    private static final Long SKU_ID = 5001L;
    /** 常用测试数据：用户ID */
    private static final Long USER_ID = 1001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：ProductServiceImpl 用了大量 LambdaQueryWrapper 和 LambdaUpdateWrapper，
     * 比如 .eq(Product::getCategoryId, ...)。这些代码会让 MyBatis-Plus 去查
     * "categoryId 字段对应数据库哪一列"。正常启动 Spring 时框架会自动做这件事，
     * 但单元测试没有 Spring 环境，所以需要我们手动告诉 MyBatis-Plus：
     * Product、ProductSku、ProductSpec、ProductSpecValue 这些实体有哪些字段、对应哪些列。
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
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个商品SPU实体
     *
     * @param id     商品ID
     * @param shopId 店铺ID（用于归属校验）
     * @param status 商品状态：0下架 1上架
     * @return 构造好的Product实体
     */
    private Product buildProduct(Long id, Long shopId, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setShopId(shopId);
        product.setCategoryId(100L);
        product.setBrandId(10L);
        product.setName("测试商品");
        product.setStatus(status);
        product.setSales(100);
        product.setViewCount(500);
        return product;
    }

    /**
     * 构造一个SKU实体
     *
     * @param id        SKU ID
     * @param productId 归属的商品ID
     * @param stock     库存数量
     * @param version   乐观锁版本号
     * @return 构造好的ProductSku实体
     */
    private ProductSku buildSku(Long id, Long productId, Integer stock, Integer version) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setPrice(new BigDecimal("99.99"));
        sku.setStock(stock);
        sku.setVersion(version);
        sku.setStatus(1);
        Map<String, String> specValues = new HashMap<>();
        specValues.put("颜色", "红色");
        sku.setSpecValues(specValues);
        return sku;
    }

    /**
     * 构造一个发布商品DTO（带规格和SKU）
     *
     * @return 构造好的ProductCreateDTO
     */
    private ProductCreateDTO buildCreateDTO() {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setCategoryId(100L);
        dto.setBrandId(10L);
        dto.setName("iPhone 15");
        dto.setSubtitle("全新A17芯片");
        dto.setMainImage("iphone15.jpg");
        dto.setImages(Arrays.asList("img1.jpg", "img2.jpg"));
        dto.setDetail("<p>商品详情</p>");

        // 规格列表
        ProductCreateDTO.SpecDTO specDTO = new ProductCreateDTO.SpecDTO();
        specDTO.setName("颜色");
        specDTO.setValues(Arrays.asList("红色", "蓝色"));

        List<ProductCreateDTO.SpecDTO> specs = new ArrayList<>();
        specs.add(specDTO);
        dto.setSpecs(specs);

        // SKU列表
        ProductCreateDTO.SkuDTO skuDTO = new ProductCreateDTO.SkuDTO();
        Map<String, String> specValues = new HashMap<>();
        specValues.put("颜色", "红色");
        skuDTO.setSpecValues(specValues);
        skuDTO.setPrice(new BigDecimal("5999.00"));
        skuDTO.setOriginalPrice(new BigDecimal("6999.00"));
        skuDTO.setStock(100);
        skuDTO.setImage("red.jpg");

        List<ProductCreateDTO.SkuDTO> skus = new ArrayList<>();
        skus.add(skuDTO);
        dto.setSkus(skus);

        return dto;
    }

    // ==================== 1. createProduct 发布商品测试 ====================

    @Nested
    @DisplayName("createProduct 发布商品")
    class CreateProductTest {

        @Test
        @DisplayName("正常发布商品：含规格和SKU，应创建SPU+规格+SKU+初始化库存+发MQ消息")
        void createProduct_normal_createAllAndSendMQ() {
            // 场景：商家发布一个带规格和SKU的商品，所有依赖都正常
            ProductCreateDTO dto = buildCreateDTO();

            // 模拟 productMapper.insert 会回填ID（MyBatis-Plus 默认行为）
            // 这里不需要特别 mock，因为 insert 返回 int，product.getId() 由 MyBatis 回填
            // 但是为了让 product.getId() 有值，我们用 doAnswer 模拟回填
            doAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                p.setId(PRODUCT_ID);
                return 1;
            }).when(productMapper).insert(any(Product.class));

            // 模拟 specMapper.insert 回填ID
            doAnswer(invocation -> {
                ProductSpec spec = invocation.getArgument(0);
                spec.setId(2001L);
                return 1;
            }).when(productSpecMapper).insert(any(ProductSpec.class));

            // 模拟 skuMapper.insert 回填ID
            doAnswer(invocation -> {
                ProductSku sku = invocation.getArgument(0);
                sku.setId(SKU_ID);
                return 1;
            }).when(productSkuMapper).insert(any(ProductSku.class));

            // 执行发布商品
            Long resultId = productService.createProduct(SHOP_ID, dto);

            // 验证返回的商品ID
            assertThat(resultId).isEqualTo(PRODUCT_ID);

            // 验证创建了SPU
            verify(productMapper).insert(any(Product.class));
            // 验证创建了1个规格模板
            verify(productSpecMapper, times(1)).insert(any(ProductSpec.class));
            // 验证创建了2个规格值（红色、蓝色）
            verify(productSpecValueMapper, times(2)).insert(any(ProductSpecValue.class));
            // 验证创建了1个SKU
            verify(productSkuMapper, times(1)).insert(any(ProductSku.class));
            // 验证初始化了SKU库存到Redis
            verify(stockService).initStock(SKU_ID, 100);
            // 验证发送了ES同步消息（RocketMQ）
            verify(rocketMQTemplate).convertAndSend(eq("topic_product_sync"), eq(String.valueOf(PRODUCT_ID)));
        }

        @Test
        @DisplayName("发布商品无规格无SKU：只创建SPU+发MQ消息，不创建规格和SKU")
        void createProduct_noSpecsNoSkus_onlyCreateSPU() {
            // 场景：商家发布一个极简商品，没有规格和SKU
            ProductCreateDTO dto = new ProductCreateDTO();
            dto.setCategoryId(100L);
            dto.setName("简版商品");
            dto.setMainImage("simple.jpg");
            // 不设置 specs 和 skus，它们为 null

            doAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                p.setId(PRODUCT_ID);
                return 1;
            }).when(productMapper).insert(any(Product.class));

            Long resultId = productService.createProduct(SHOP_ID, dto);

            assertThat(resultId).isEqualTo(PRODUCT_ID);
            // 验证只创建了SPU，没有创建规格和SKU
            verify(productMapper).insert(any(Product.class));
            verify(productSpecMapper, never()).insert(any(ProductSpec.class));
            verify(productSkuMapper, never()).insert(any(ProductSku.class));
            verify(stockService, never()).initStock(anyLong(), any());
            // 仍然发送了ES同步消息
            verify(rocketMQTemplate).convertAndSend(eq("topic_product_sync"), anyString());
        }

        @Test
        @DisplayName("发MQ消息失败：不影响主流程，商品仍然发布成功")
        void createProduct_mqFail_stillSuccess() {
            // 场景：发送ES同步消息时MQ挂了，但商品发布主流程不应受影响
            ProductCreateDTO dto = new ProductCreateDTO();
            dto.setCategoryId(100L);
            dto.setName("测试商品");
            dto.setMainImage("test.jpg");

            doAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                p.setId(PRODUCT_ID);
                return 1;
            }).when(productMapper).insert(any(Product.class));

            // 模拟MQ发送抛异常
            doThrow(new RuntimeException("MQ连接失败"))
                    .when(rocketMQTemplate).convertAndSend(anyString(), anyString());

            // 即使MQ失败，方法不应抛异常，商品仍然创建成功
            Long resultId = productService.createProduct(SHOP_ID, dto);

            assertThat(resultId).isEqualTo(PRODUCT_ID);
            verify(productMapper).insert(any(Product.class));
        }
    }

    // ==================== 2. updateProduct 编辑商品测试 ====================

    @Nested
    @DisplayName("updateProduct 编辑商品")
    class UpdateProductTest {

        @Test
        @DisplayName("商品不存在 → 抛出 PRODUCT_NOT_FOUND 异常")
        void updateProduct_notExists_throwsException() {
            // 场景：编辑一个不存在的商品
            ProductUpdateDTO dto = new ProductUpdateDTO();
            dto.setName("新名字");
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

            assertThatThrownBy(() -> productService.updateProduct(PRODUCT_ID, dto, SHOP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());

            // 验证没有执行更新
            verify(productMapper, never()).updateById(any(Product.class));
        }

        @Test
        @DisplayName("归属校验失败：商品属于其他店铺 → 抛出 FORBIDDEN 异常")
        void updateProduct_notOwner_throwsForbidden() {
            // 场景：商家A想编辑商家B的商品
            ProductUpdateDTO dto = new ProductUpdateDTO();
            dto.setName("恶意修改");
            // 商品属于店铺2002，当前用户是店铺2001
            Product product = buildProduct(PRODUCT_ID, 2002L, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            assertThatThrownBy(() -> productService.updateProduct(PRODUCT_ID, dto, SHOP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            verify(productMapper, never()).updateById(any(Product.class));
        }

        @Test
        @DisplayName("正常更新商品名称 → 调用updateById+延迟双删+发MQ消息")
        void updateProduct_normal_updateAndEvictCache() {
            // 场景：商家编辑自己的商品，只更新名称
            ProductUpdateDTO dto = new ProductUpdateDTO();
            dto.setName("新商品名称");
            Product product = buildProduct(PRODUCT_ID, SHOP_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            productService.updateProduct(PRODUCT_ID, dto, SHOP_ID);

            // 验证调用了updateById
            verify(productMapper).updateById(any(Product.class));
            // 验证执行了延迟双删缓存
            verify(productCacheService).delayDoubleEvict(PRODUCT_ID);
            // 验证发送了ES同步消息
            verify(rocketMQTemplate).convertAndSend(eq("topic_product_sync"), eq(String.valueOf(PRODUCT_ID)));
            // 验证没有动规格和SKU（dto里没传）
            verify(productSpecMapper, never()).delete(any(LambdaQueryWrapper.class));
            verify(productSkuMapper, never()).delete(any(LambdaQueryWrapper.class));
        }
    }

    // ==================== 3. onShelf / offShelf 上下架测试 ====================

    @Nested
    @DisplayName("onShelf / offShelf 商品上下架")
    class ShelfTest {

        @Test
        @DisplayName("上架-商品不存在 → 抛出 PRODUCT_NOT_FOUND 异常")
        void onShelf_notExists_throwsException() {
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

            assertThatThrownBy(() -> productService.onShelf(PRODUCT_ID, SHOP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());

            verify(productMapper, never()).updateStatus(anyLong(), anyInt());
        }

        @Test
        @DisplayName("上架-归属校验失败 → 抛出 FORBIDDEN 异常")
        void onShelf_notOwner_throwsForbidden() {
            Product product = buildProduct(PRODUCT_ID, 2002L, 0); // 商品属于其他店铺
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            assertThatThrownBy(() -> productService.onShelf(PRODUCT_ID, SHOP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            verify(productMapper, never()).updateStatus(anyLong(), anyInt());
        }

        @Test
        @DisplayName("正常上架 → 调用updateStatus(1)+延迟双删+发MQ消息")
        void onShelf_normal_updateStatusTo1() {
            Product product = buildProduct(PRODUCT_ID, SHOP_ID, 0); // 当前下架状态
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            productService.onShelf(PRODUCT_ID, SHOP_ID);

            // 验证状态更新为1（上架）
            verify(productMapper).updateStatus(PRODUCT_ID, 1);
            verify(productCacheService).delayDoubleEvict(PRODUCT_ID);
            verify(rocketMQTemplate).convertAndSend(eq("topic_product_sync"), eq(String.valueOf(PRODUCT_ID)));
        }

        @Test
        @DisplayName("正常下架 → 调用updateStatus(0)+延迟双删+发MQ消息")
        void offShelf_normal_updateStatusTo0() {
            Product product = buildProduct(PRODUCT_ID, SHOP_ID, 1); // 当前上架状态
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            productService.offShelf(PRODUCT_ID, SHOP_ID);

            // 验证状态更新为0（下架）
            verify(productMapper).updateStatus(PRODUCT_ID, 0);
            verify(productCacheService).delayDoubleEvict(PRODUCT_ID);
            verify(rocketMQTemplate).convertAndSend(eq("topic_product_sync"), eq(String.valueOf(PRODUCT_ID)));
        }
    }

    // ==================== 4. deductStock 扣减库存测试 ====================

    @Nested
    @DisplayName("deductStock 扣减库存（乐观锁）")
    class DeductStockTest {

        @Test
        @DisplayName("SKU不存在 → 抛出 PRODUCT_NOT_FOUND 异常")
        void deductStock_skuNotExists_throwsException() {
            when(productSkuMapper.selectById(SKU_ID)).thenReturn(null);

            assertThatThrownBy(() -> productService.deductStock(SKU_ID, 5))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());

            verify(productSkuMapper, never()).deductStock(anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("扣减成功：乐观锁返回1行 → 方法返回true")
        void deductStock_success_returnTrue() {
            ProductSku sku = buildSku(SKU_ID, PRODUCT_ID, 100, 0);
            when(productSkuMapper.selectById(SKU_ID)).thenReturn(sku);
            // 模拟乐观锁扣减成功（返回1行）
            when(productSkuMapper.deductStock(SKU_ID, 5, 0)).thenReturn(1);

            boolean result = productService.deductStock(SKU_ID, 5);

            assertThat(result).isTrue();
            verify(productSkuMapper).deductStock(SKU_ID, 5, 0);
        }

        @Test
        @DisplayName("扣减失败：乐观锁冲突或库存不足 → 方法返回false")
        void deductStock_fail_returnFalse() {
            ProductSku sku = buildSku(SKU_ID, PRODUCT_ID, 0, 0);
            when(productSkuMapper.selectById(SKU_ID)).thenReturn(sku);
            // 模拟乐观锁扣减失败（返回0行，说明版本号变了或库存不够）
            when(productSkuMapper.deductStock(SKU_ID, 5, 0)).thenReturn(0);

            boolean result = productService.deductStock(SKU_ID, 5);

            assertThat(result).isFalse();
        }
    }

    // ==================== 5. addStock 回滚库存测试 ====================

    @Nested
    @DisplayName("addStock 回滚库存")
    class AddStockTest {

        @Test
        @DisplayName("回滚失败：addStock返回0 → 抛出 PRODUCT_NOT_FOUND 异常")
        void addStock_fail_throwsException() {
            // 模拟 addStock 返回0行（SKU不存在）
            when(productSkuMapper.addStock(SKU_ID, 5)).thenReturn(0);

            assertThatThrownBy(() -> productService.addStock(SKU_ID, 5))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());
        }
    }

    // ==================== 6. deductStockWithIdempotent 幂等扣减库存测试 ====================

    @Nested
    @DisplayName("deductStockWithIdempotent 幂等扣减库存")
    class DeductStockWithIdempotentTest {

        @Test
        @DisplayName("Redis扣减失败 → 直接返回false，不调用数据库扣减")
        void redisFail_returnFalseWithoutDB() {
            // 场景：Redis Lua脚本扣减库存失败（库存不足或已扣减过）
            when(stockService.deductStock(SKU_ID, 5, "ORDER_001")).thenReturn(false);

            boolean result = productService.deductStockWithIdempotent(SKU_ID, 5, "ORDER_001");

            assertThat(result).isFalse();
            // 验证没有调用数据库扣减
            verify(productSkuMapper, never()).selectById(anyLong());
            verify(productSkuMapper, never()).deductStock(anyLong(), anyInt(), anyInt());
            // 验证没有回滚Redis库存
            verify(stockService, never()).addStock(anyLong(), anyInt(), anyString());
        }

        @Test
        @DisplayName("Redis成功但数据库扣减失败 → 回滚Redis库存并返回false")
        void redisSuccessButDBFail_rollbackRedis() {
            // 场景：Redis扣减成功，但数据库扣减失败（乐观锁冲突）
            when(stockService.deductStock(SKU_ID, 5, "ORDER_002")).thenReturn(true);
            // 数据库查到SKU
            ProductSku sku = buildSku(SKU_ID, PRODUCT_ID, 100, 0);
            when(productSkuMapper.selectById(SKU_ID)).thenReturn(sku);
            // 数据库乐观锁扣减失败
            when(productSkuMapper.deductStock(SKU_ID, 5, 0)).thenReturn(0);

            boolean result = productService.deductStockWithIdempotent(SKU_ID, 5, "ORDER_002");

            assertThat(result).isFalse();
            // 验证回滚了Redis库存（注意回滚的orderNo前缀是"rollback_"）
            verify(stockService).addStock(SKU_ID, 5, "rollback_ORDER_002");
        }
    }

    // ==================== 7. getSkuById 查询SKU测试 ====================

    @Nested
    @DisplayName("getSkuById 查询SKU信息")
    class GetSkuByIdTest {

        @Test
        @DisplayName("SKU不存在 → 抛出 PRODUCT_NOT_FOUND 异常")
        void getSkuById_notExists_throwsException() {
            when(productSkuMapper.selectById(SKU_ID)).thenReturn(null);

            assertThatThrownBy(() -> productService.getSkuById(SKU_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常查询：返回SKU VO，Feign查商家ID失败时merchantId为null")
        void getSkuById_normal_returnVO() {
            // 场景：查询SKU信息，Feign调用商家服务查merchantId失败（弱依赖，不阻塞主流程）
            ProductSku sku = buildSku(SKU_ID, PRODUCT_ID, 100, 0);
            when(productSkuMapper.selectById(SKU_ID)).thenReturn(sku);
            // 模拟查商品
            Product product = buildProduct(PRODUCT_ID, SHOP_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            // 模拟商家服务Feign调用抛异常（弱依赖）
            when(merchantFeignClient.getShopById(SHOP_ID)).thenThrow(new RuntimeException("商家服务不可用"));

            ProductSkuVO vo = productService.getSkuById(SKU_ID);

            // 验证返回的VO字段正确
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(SKU_ID);
            assertThat(vo.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(vo.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
            // Feign调用失败时merchantId为null（不阻塞主流程）
            assertThat(vo.getMerchantId()).isNull();
        }

        @Test
        @DisplayName("正常查询：Feign调用成功，VO中merchantId为店铺归属的商家ID")
        void getSkuById_feignSuccess_returnVOWithMerchantId() {
            ProductSku sku = buildSku(SKU_ID, PRODUCT_ID, 100, 0);
            when(productSkuMapper.selectById(SKU_ID)).thenReturn(sku);
            Product product = buildProduct(PRODUCT_ID, SHOP_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            // 模拟商家服务Feign调用成功
            ShopVO shopVO = new ShopVO();
            shopVO.setId(SHOP_ID);
            shopVO.setMerchantId(3001L);
            when(merchantFeignClient.getShopById(SHOP_ID)).thenReturn(Result.success(shopVO));

            ProductSkuVO vo = productService.getSkuById(SKU_ID);

            assertThat(vo.getMerchantId()).isEqualTo(3001L);
        }
    }

    // ==================== 8. getHotProducts 热销推荐测试 ====================

    @Nested
    @DisplayName("getHotProducts 热销推荐")
    class GetHotProductsTest {

        @Test
        @DisplayName("查询热销商品：返回VO列表，包含分类名、品牌名、最低价、总库存")
        void getHotProducts_normal_returnVOList() {
            // 场景：查询热销商品，验证批量查询分类名/品牌名/SKU是否正确
            Product p1 = buildProduct(1001L, SHOP_ID, 1);
            p1.setSales(500);
            Product p2 = buildProduct(1002L, SHOP_ID, 1);
            p2.setSales(300);
            when(productMapper.selectList(any())).thenReturn(Arrays.asList(p1, p2));

            // 模拟分类查询
            Category cat = new Category();
            cat.setId(100L);
            cat.setName("手机");
            when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(cat));

            // 模拟品牌查询
            Brand brand = new Brand();
            brand.setId(10L);
            brand.setName("Apple");
            when(brandMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(brand));

            // 模拟SKU查询（每个商品2个SKU）
            ProductSku sku1 = buildSku(5001L, 1001L, 50, 0);
            sku1.setPrice(new BigDecimal("5999.00"));
            ProductSku sku2 = buildSku(5002L, 1001L, 30, 0);
            sku2.setPrice(new BigDecimal("6999.00"));
            ProductSku sku3 = buildSku(5003L, 1002L, 20, 0);
            sku3.setPrice(new BigDecimal("2999.00"));
            when(productSkuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, sku2, sku3));

            List<com.shop.model.product.vo.ProductVO> result = productService.getHotProducts(2);

            assertThat(result).hasSize(2);
            // 验证第一个商品的VO字段
            com.shop.model.product.vo.ProductVO vo1 = result.get(0);
            assertThat(vo1.getId()).isEqualTo(1001L);
            assertThat(vo1.getCategoryName()).isEqualTo("手机");
            assertThat(vo1.getBrandName()).isEqualTo("Apple");
            // 最低价取2个SKU中较小的
            assertThat(vo1.getMinPrice()).isEqualByComparingTo(new BigDecimal("5999.00"));
            // 总库存 = 50 + 30 = 80
            assertThat(vo1.getTotalStock()).isEqualTo(80);
        }
    }

    // ==================== 9. incrSales 销量累加测试 ====================

    @Nested
    @DisplayName("incrSales 销量累加")
    class IncrSalesTest {

        @Test
        @DisplayName("productId为null → 直接返回，不调用Mapper")
        void incrSales_nullProductId_doNothing() {
            productService.incrSales(null, 5);
            verify(productMapper, never()).incrSales(anyLong(), anyInt());
        }

        @Test
        @DisplayName("quantity为null或≤0 → 直接返回，不调用Mapper")
        void incrSales_invalidQuantity_doNothing() {
            productService.incrSales(PRODUCT_ID, null);
            verify(productMapper, never()).incrSales(anyLong(), anyInt());

            productService.incrSales(PRODUCT_ID, 0);
            verify(productMapper, never()).incrSales(anyLong(), anyInt());

            productService.incrSales(PRODUCT_ID, -1);
            verify(productMapper, never()).incrSales(anyLong(), anyInt());
        }

        @Test
        @DisplayName("正常累加：调用incrSales(productId, quantity)")
        void incrSales_normal_callMapper() {
            productService.incrSales(PRODUCT_ID, 3);
            verify(productMapper).incrSales(PRODUCT_ID, 3);
        }
    }

    // ==================== 10. batchDeductStock 批量扣减库存测试 ====================

    @Nested
    @DisplayName("batchDeductStock 批量扣减库存")
    class BatchDeductStockTest {

        @Test
        @DisplayName("items为null → 直接返回true，不调用任何Mapper")
        void batchDeductStock_nullItems_returnTrue() {
            boolean result = productService.batchDeductStock(null, "ORDER_001");
            assertThat(result).isTrue();
            verify(stockService, never()).deductStock(anyLong(), anyInt(), anyString());
        }

        @Test
        @DisplayName("items为空列表 → 直接返回true，不调用任何Mapper")
        void batchDeductStock_emptyItems_returnTrue() {
            boolean result = productService.batchDeductStock(new ArrayList<>(), "ORDER_001");
            assertThat(result).isTrue();
            verify(stockService, never()).deductStock(anyLong(), anyInt(), anyString());
        }

        @Test
        @DisplayName("部分SKU扣减失败 → 回滚已扣减的SKU，返回false")
        void batchDeductStock_partialFail_rollbackAndReturnFalse() {
            // 场景：3个SKU扣减，前2个成功，第3个失败 → 回滚前2个
            StockDeductItemDTO item1 = new StockDeductItemDTO();
            item1.setSkuId(5001L);
            item1.setQuantity(1);

            StockDeductItemDTO item2 = new StockDeductItemDTO();
            item2.setSkuId(5002L);
            item2.setQuantity(2);

            StockDeductItemDTO item3 = new StockDeductItemDTO();
            item3.setSkuId(5003L);
            item3.setQuantity(3);

            // 前2个SKU扣减成功（Redis + DB都成功）
            when(stockService.deductStock(5001L, 1, "ORDER_001")).thenReturn(true);
            when(stockService.deductStock(5002L, 2, "ORDER_001")).thenReturn(true);
            // 第3个SKU Redis扣减失败
            when(stockService.deductStock(5003L, 3, "ORDER_001")).thenReturn(false);

            // 前2个SKU的数据库扣减成功
            ProductSku sku1 = buildSku(5001L, PRODUCT_ID, 100, 0);
            ProductSku sku2 = buildSku(5002L, PRODUCT_ID, 100, 0);
            when(productSkuMapper.selectById(5001L)).thenReturn(sku1);
            when(productSkuMapper.selectById(5002L)).thenReturn(sku2);
            when(productSkuMapper.deductStock(eq(5001L), eq(1), anyInt())).thenReturn(1);
            when(productSkuMapper.deductStock(eq(5002L), eq(2), anyInt())).thenReturn(1);

            boolean result = productService.batchDeductStock(
                    Arrays.asList(item1, item2, item3), "ORDER_001");

            // 验证返回false（整体失败）
            assertThat(result).isFalse();
            // 验证回滚了前2个SKU的库存
            verify(stockService).addStock(5001L, 1, "ORDER_001");
            verify(stockService).addStock(5002L, 2, "ORDER_001");
        }
    }

    // ==================== 11. recordView 记录浏览测试 ====================

    @Nested
    @DisplayName("recordView 记录浏览")
    class RecordViewTest {

        @Test
        @DisplayName("浏览量累加失败 → 不影响主流程，足迹仍尝试记录")
        void recordView_viewCountFail_notAffectMain() {
            // 场景：浏览量+1 失败（数据库异常），但不应抛异常，应继续记录足迹
            when(productMapper.incrViewCount(PRODUCT_ID)).thenThrow(new RuntimeException("DB异常"));
            // 模拟查商品成功（用于记录足迹）
            Product product = buildProduct(PRODUCT_ID, SHOP_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

            // 不抛异常即可
            productService.recordView(PRODUCT_ID, USER_ID);

            // 验证调用了足迹记录（弱依赖：浏览量失败不影响足迹记录）
            verify(userFeignClient).recordFootprint(USER_ID, PRODUCT_ID, 100L);
        }

        @Test
        @DisplayName("未登录用户 → 只累加浏览量，不记录足迹")
        void recordView_anonymousUser_onlyViewCount() {
            // 场景：未登录用户浏览商品（userId为null），只累加浏览量，不记录足迹
            productService.recordView(PRODUCT_ID, null);

            verify(productMapper).incrViewCount(PRODUCT_ID);
            // 验证没有调用用户服务记录足迹
            verify(userFeignClient, never()).recordFootprint(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("足迹记录失败 → 不影响主流程，浏览量已累加")
        void recordView_footprintFail_notAffectMain() {
            // 场景：用户服务挂了，记录足迹失败，但浏览量累加已成功
            when(productMapper.incrViewCount(PRODUCT_ID)).thenReturn(1);
            Product product = buildProduct(PRODUCT_ID, SHOP_ID, 1);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            // 模拟用户服务Feign调用抛异常
            doThrow(new RuntimeException("用户服务不可用"))
                    .when(userFeignClient).recordFootprint(anyLong(), anyLong(), anyLong());

            // 不抛异常即可
            productService.recordView(PRODUCT_ID, USER_ID);

            // 验证浏览量已累加
            verify(productMapper).incrViewCount(PRODUCT_ID);
        }
    }

    // ==================== 12. getGuessProducts 猜你喜欢测试 ====================

    @Nested
    @DisplayName("getGuessProducts 猜你喜欢")
    class GetGuessProductsTest {

        @Test
        @DisplayName("未登录用户 → 降级为全站热销推荐")
        void getGuessProducts_anonymousUser_degradeToHot() {
            // 场景：未登录用户（userId为null）访问猜你喜欢，降级为全站热销
            Product p = buildProduct(1001L, SHOP_ID, 1);
            when(productMapper.selectList(any())).thenReturn(Collections.singletonList(p));
            // 模拟批量查询分类/品牌/SKU都返回空
            when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
            when(productSkuMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<com.shop.model.product.vo.ProductVO> result = productService.getGuessProducts(null, 10);

            assertThat(result).hasSize(1);
            // 验证没有调用用户服务查足迹
            verify(userFeignClient, never()).getFootprintCategories(anyLong());
        }

        @Test
        @DisplayName("用户无足迹 → 降级为全站热销推荐")
        void getGuessProducts_noFootprint_degradeToHot() {
            // 场景：登录用户但没浏览过任何商品，Feign返回空列表 → 降级为全站热销
            when(userFeignClient.getFootprintCategories(USER_ID))
                    .thenReturn(Result.success(Collections.emptyList()));

            Product p = buildProduct(1001L, SHOP_ID, 1);
            when(productMapper.selectList(any())).thenReturn(Collections.singletonList(p));
            when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
            when(productSkuMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<com.shop.model.product.vo.ProductVO> result = productService.getGuessProducts(USER_ID, 10);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Feign查足迹失败 → 降级为全站热销推荐")
        void getGuessProducts_feignFail_degradeToHot() {
            // 场景：用户服务挂了，查足迹分类抛异常 → 降级为全站热销
            when(userFeignClient.getFootprintCategories(USER_ID))
                    .thenThrow(new RuntimeException("用户服务不可用"));

            Product p = buildProduct(1001L, SHOP_ID, 1);
            when(productMapper.selectList(any())).thenReturn(Collections.singletonList(p));
            when(categoryMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
            when(productSkuMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<com.shop.model.product.vo.ProductVO> result = productService.getGuessProducts(USER_ID, 10);

            assertThat(result).hasSize(1);
        }
    }
}
