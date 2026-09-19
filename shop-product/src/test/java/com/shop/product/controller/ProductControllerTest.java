package com.shop.product.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductSearchDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.entity.Product;
import com.shop.model.product.entity.ProductSku;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductSearchVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.product.vo.ProductVO;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.mapper.ProductSkuMapper;
import com.shop.product.service.ProductSearchService;
import com.shop.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductController 切片测试（B-S-03）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 我们用 Standalone MockMvc 的方式，手动把Controller和它的依赖组装起来，
 * 这样测试跑得快，又不需要真的连数据库、Redis、Nacos、ES等中间件。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确（URL能否映射到对应方法）
 * 2. 参数校验是否生效（@Valid校验不通过时返回400）
 * 3. 返回格式是否正确（统一Result格式，code=200表示成功）
 * 4. Service调用是否正确（参数传递、调用次数）
 * 5. 异常处理是否正确（BusinessException返回业务错误码）
 * </p>
 * <p>
 * 注意：
 * 1. 商家端接口通过 getShopId() 从请求头 X-Shop-Id 获取店铺ID，测试时需要设置请求头。
 * 2. 商品详情接口用 StpUtil.isLogin() 判断是否登录，登录时记录用户足迹。
 * 3. @SaCheckLogin 注解在切片测试中AOP不生效，所以不会拦截请求，测试中不需要处理。
 * 4. adminListProducts 用到了 LambdaQueryWrapper，需要初始化 MyBatis-Plus 字段缓存。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductController 切片测试")
class ProductControllerTest {

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 假装商品服务 */
    @Mock
    private ProductService productService;

    /** 假装搜索服务 */
    @Mock
    private ProductSearchService productSearchService;

    /** 假装商品Mapper（管理端直接查数据库用） */
    @Mock
    private ProductMapper productMapper;

    /** 假装SKU Mapper（管理端列表算最低价和总库存用） */
    @Mock
    private ProductSkuMapper productSkuMapper;

    /** Sa-Token静态方法mock（模拟登录状态） */
    private MockedStatic<StpUtil> stpUtilMock;

    /** 测试中模拟的店铺ID（通过X-Shop-Id请求头传递） */
    private static final Long MOCK_SHOP_ID = 5001L;

    /** 测试中模拟的登录用户ID */
    private static final Long MOCK_USER_ID = 1001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：adminListProducts 方法用到了 .eq(Product::getStatus, ...) 这种写法，
     * MyBatis-Plus 需要知道 Product::getStatus 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，切片测试没有完整 Spring 环境，所以要手动初始化。
     * 这里同时初始化 Product 和 ProductSku 两个实体。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Product.class);
        TableInfoHelper.initTableInfo(assistant, ProductSku.class);
    }

    @BeforeEach
    void setUp() {
        // 1. 创建真实的 ProductController 实例，注入 mock 依赖
        ProductController controller = new ProductController(
                productService, productSearchService, productMapper, productSkuMapper);

        // 2. 用 Standalone 方式构建 MockMvc，手动注册全局异常处理器
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 3. mock StpUtil：默认未登录（isLogin返回false）
        stpUtilMock = org.mockito.Mockito.mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        // 每个测试结束后关闭静态mock，避免影响其他测试
        stpUtilMock.close();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造一个合法的商品发布DTO
     *
     * @return 构造好的 ProductCreateDTO
     */
    private ProductCreateDTO buildValidCreateDTO() {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setCategoryId(10L);
        dto.setBrandId(20L);
        dto.setName("Apple iPhone 15 Pro Max");
        dto.setSubtitle("全新A17 Pro芯片，钛金属设计");
        dto.setMainImage("https://example.com/iphone15.jpg");

        // 构造一个SKU
        ProductCreateDTO.SkuDTO sku = new ProductCreateDTO.SkuDTO();
        sku.setSpecValues(Map.of("颜色", "蓝色", "存储", "256G"));
        sku.setPrice(new BigDecimal("9999.00"));
        sku.setOriginalPrice(new BigDecimal("10999.00"));
        sku.setStock(100);
        dto.setSkus(List.of(sku));

        return dto;
    }

    /**
     * 构造一个商品详情VO
     *
     * @param id 商品ID
     * @return 构造好的 ProductDetailVO
     */
    private ProductDetailVO buildDetailVO(Long id) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(id);
        vo.setCategoryId(10L);
        vo.setCategoryName("手机数码");
        vo.setShopId(MOCK_SHOP_ID);
        vo.setShopName("苹果官方旗舰店");
        vo.setName("Apple iPhone 15 Pro Max");
        vo.setSubtitle("全新A17 Pro芯片");
        vo.setMainImage("https://example.com/iphone15.jpg");
        vo.setStatus(1);
        vo.setSales(500);
        vo.setViewCount(10000);
        vo.setMinPrice(new BigDecimal("9999.00"));
        vo.setTotalStock(100);
        vo.setCreateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        return vo;
    }

    /**
     * 构造一个商品VO（用于列表展示）
     *
     * @param id       商品ID
     * @param name     商品名称
     * @param status   状态：0下架 1上架
     * @return 构造好的 ProductVO
     */
    private ProductVO buildProductVO(Long id, String name, Integer status) {
        ProductVO vo = new ProductVO();
        vo.setId(id);
        vo.setName(name);
        vo.setMainImage("https://example.com/product.jpg");
        vo.setStatus(status);
        vo.setCategoryId(10L);
        vo.setShopId(MOCK_SHOP_ID);
        vo.setMinPrice(new BigDecimal("9999.00"));
        vo.setTotalStock(100);
        vo.setCreateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        return vo;
    }

    /**
     * 构造一个商品实体（用于Mapper返回值）
     *
     * @param id     商品ID
     * @param name   商品名称
     * @param status 状态
     * @return 构造好的 Product
     */
    private Product buildProduct(Long id, String name, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setMainImage("https://example.com/product.jpg");
        product.setStatus(status);
        product.setCategoryId(10L);
        product.setBrandId(20L);
        product.setShopId(MOCK_SHOP_ID);
        product.setCreateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        return product;
    }

    /**
     * 构造一个SKU实体
     *
     * @param productId 商品ID
     * @param price     价格
     * @param stock     库存
     * @return 构造好的 ProductSku
     */
    private ProductSku buildSku(Long productId, BigDecimal price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setPrice(price);
        sku.setStock(stock);
        return sku;
    }

    // ==================== 发布商品 ====================

    @Nested
    @DisplayName("发布商品 POST /product")
    class CreateProductTest {

        @Test
        @DisplayName("正常发布商品 → 返回200和商品ID")
        void createProduct_success_returnsProductId() throws Exception {
            ProductCreateDTO dto = buildValidCreateDTO();

            when(productService.createProduct(eq(MOCK_SHOP_ID), any(ProductCreateDTO.class)))
                    .thenReturn(8001L);

            mockMvc.perform(post("/product")
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(8001));

            verify(productService).createProduct(eq(MOCK_SHOP_ID), any(ProductCreateDTO.class));
        }

        @Test
        @DisplayName("商品名称为空 → 参数校验失败返回400")
        void createProduct_nameEmpty_returns400() throws Exception {
            ProductCreateDTO dto = buildValidCreateDTO();
            dto.setName(""); // 商品名称为空

            mockMvc.perform(post("/product")
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("分类ID为空 → 参数校验失败返回400")
        void createProduct_categoryIdNull_returns400() throws Exception {
            ProductCreateDTO dto = buildValidCreateDTO();
            dto.setCategoryId(null); // 分类ID为空

            mockMvc.perform(post("/product")
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }
    }

    // ==================== 编辑商品 ====================

    @Nested
    @DisplayName("编辑商品 PUT /product/{id}")
    class UpdateProductTest {

        @Test
        @DisplayName("正常编辑商品 → 返回200")
        void updateProduct_success() throws Exception {
            ProductUpdateDTO dto = new ProductUpdateDTO();
            dto.setName("iPhone 15 Pro Max 修改版");

            mockMvc.perform(put("/product/{id}", 8001L)
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"));

            verify(productService).updateProduct(eq(8001L), any(ProductUpdateDTO.class), eq(MOCK_SHOP_ID));
        }

        @Test
        @DisplayName("非自己店铺的商品 → Service抛业务异常 → 返回业务错误码")
        void updateProduct_notYourShop_throwsBusinessException() throws Exception {
            ProductUpdateDTO dto = new ProductUpdateDTO();
            dto.setName("测试");

            // mock Service抛出"无权操作"业务异常
            doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权操作此商品"))
                    .when(productService).updateProduct(anyLong(), any(ProductUpdateDTO.class), anyLong());

            mockMvc.perform(put("/product/{id}", 8001L)
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
        }
    }

    // ==================== 上架商品 ====================

    @Nested
    @DisplayName("上架商品 PUT /product/{id}/on-shelf")
    class OnShelfTest {

        @Test
        @DisplayName("正常上架商品 → 返回200")
        void onShelf_success() throws Exception {
            mockMvc.perform(put("/product/{id}/on-shelf", 8001L)
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("上架成功"));

            verify(productService).onShelf(eq(8001L), eq(MOCK_SHOP_ID));
        }

        @Test
        @DisplayName("商品不存在 → Service抛业务异常 → 返回业务错误码")
        void onShelf_notFound_throwsBusinessException() throws Exception {
            doThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                    .when(productService).onShelf(anyLong(), anyLong());

            mockMvc.perform(put("/product/{id}/on-shelf", 9999L)
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_FOUND.getCode()));
        }
    }

    // ==================== 下架商品 ====================

    @Nested
    @DisplayName("下架商品 PUT /product/{id}/off-shelf")
    class OffShelfTest {

        @Test
        @DisplayName("正常下架商品 → 返回200")
        void offShelf_success() throws Exception {
            mockMvc.perform(put("/product/{id}/off-shelf", 8001L)
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("下架成功"));

            verify(productService).offShelf(eq(8001L), eq(MOCK_SHOP_ID));
        }

        @Test
        @DisplayName("商品不存在 → Service抛业务异常 → 返回业务错误码")
        void offShelf_notFound_throwsBusinessException() throws Exception {
            doThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                    .when(productService).offShelf(anyLong(), anyLong());

            mockMvc.perform(put("/product/{id}/off-shelf", 9999L)
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_FOUND.getCode()));
        }
    }

    // ==================== 管理端上下架 ====================

    @Nested
    @DisplayName("管理端上下架 PUT /product/admin/{id}/on-shelf|off-shelf")
    class AdminShelfTest {

        @Test
        @DisplayName("管理端下架 → 映射到 adminOffShelf，不传任何店铺上下文")
        void adminOffShelf_success() throws Exception {
            mockMvc.perform(put("/product/admin/{id}/off-shelf", 8001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("下架成功"));

            verify(productService).adminOffShelf(8001L);
            // 关键：不能误走商家侧那条带归属校验的路径
            verify(productService, never()).offShelf(anyLong(), anyLong());
        }

        @Test
        @DisplayName("管理端上架 → 映射到 adminOnShelf")
        void adminOnShelf_success() throws Exception {
            mockMvc.perform(put("/product/admin/{id}/on-shelf", 8001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("上架成功"));

            verify(productService).adminOnShelf(8001L);
            verify(productService, never()).onShelf(anyLong(), anyLong());
        }

        @Test
        @DisplayName("商家端缺少 X-Shop-Id → 返回403而不是拿登录ID当店铺ID")
        void offShelf_withoutShopId_throwsForbidden() throws Exception {
            mockMvc.perform(put("/product/{id}/off-shelf", 8001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

            verify(productService, never()).offShelf(anyLong(), anyLong());
        }
    }

    // ==================== 商品详情 ====================

    @Nested
    @DisplayName("商品详情 GET /product/{id}")
    class GetProductDetailTest {

        @Test
        @DisplayName("正常获取商品详情（已登录用户，记录足迹） → 返回200和详情")
        void getProductDetail_loginUser_success() throws Exception {
            ProductDetailVO detailVO = buildDetailVO(8001L);

            when(productService.getProductDetail(eq(8001L))).thenReturn(detailVO);
            // 已登录：isLogin返回true，getLoginIdAsLong返回用户ID
            stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
            stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(MOCK_USER_ID);

            mockMvc.perform(get("/product/{id}", 8001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(8001))
                    .andExpect(jsonPath("$.data.name").value("Apple iPhone 15 Pro Max"))
                    .andExpect(jsonPath("$.data.minPrice").value(9999.00))
                    .andExpect(jsonPath("$.data.totalStock").value(100));

            // 验证记录了用户浏览（传入用户ID记录足迹）
            verify(productService).getProductDetail(eq(8001L));
            verify(productService).recordView(eq(8001L), eq(MOCK_USER_ID));
        }

        @Test
        @DisplayName("正常获取商品详情（未登录用户，不记录足迹） → 返回200和详情")
        void getProductDetail_anonymous_success() throws Exception {
            ProductDetailVO detailVO = buildDetailVO(8001L);

            when(productService.getProductDetail(eq(8001L))).thenReturn(detailVO);
            // 未登录：isLogin返回false（在@BeforeEach中已设置默认值）

            mockMvc.perform(get("/product/{id}", 8001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(8001));

            // 验证记录了浏览，但userId为null（未登录不记录足迹）
            verify(productService).recordView(eq(8001L), eq(null));
        }

        @Test
        @DisplayName("商品不存在 → Service抛业务异常 → 返回业务错误码")
        void getProductDetail_notFound_throwsBusinessException() throws Exception {
            when(productService.getProductDetail(eq(9999L)))
                    .thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            mockMvc.perform(get("/product/{id}", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_FOUND.getCode()));
        }
    }

    // ==================== 商品列表 ====================

    @Nested
    @DisplayName("商品列表 GET /product/list")
    class GetProductListTest {

        @Test
        @DisplayName("正常获取商品列表 → 返回200和分页数据")
        void getProductList_success_returnsPageResult() throws Exception {
            List<ProductVO> voList = List.of(
                    buildProductVO(8001L, "iPhone 15", 1),
                    buildProductVO(8002L, "iPhone 14", 1));
            PageResult<ProductVO> pageResult = new PageResult<>();
            pageResult.setRecords(voList);
            pageResult.setTotal(2);
            pageResult.setPageNum(1);
            pageResult.setPageSize(10);
            pageResult.setPages(1);

            when(productService.getProductList(eq(null), any())).thenReturn(pageResult);

            mockMvc.perform(get("/product/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records[0].id").value(8001))
                    .andExpect(jsonPath("$.data.records[1].id").value(8002));

            verify(productService).getProductList(eq(null), any());
        }

        @Test
        @DisplayName("按分类筛选 → 返回200和筛选结果")
        void getProductList_byCategory_returnsFilteredResult() throws Exception {
            ProductVO vo = buildProductVO(8001L, "iPhone 15", 1);
            PageResult<ProductVO> pageResult = new PageResult<>();
            pageResult.setRecords(List.of(vo));
            pageResult.setTotal(1);

            when(productService.getProductList(eq(10L), any())).thenReturn(pageResult);

            mockMvc.perform(get("/product/list")
                            .param("categoryId", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(8001));

            verify(productService).getProductList(eq(10L), any());
        }
    }

    // ==================== 搜索商品 ====================

    @Nested
    @DisplayName("搜索商品 GET /product/search")
    class SearchTest {

        @Test
        @DisplayName("正常搜索商品 → 返回200和搜索结果")
        void search_success_returnsSearchResult() throws Exception {
            ProductSearchVO searchVO = new ProductSearchVO();
            searchVO.setId(8001L);
            searchVO.setName("Apple <em>iPhone</em> 15");
            searchVO.setMinPrice(new BigDecimal("9999.00"));

            PageResult<ProductSearchVO> pageResult = new PageResult<>();
            pageResult.setRecords(List.of(searchVO));
            pageResult.setTotal(1);

            when(productSearchService.search(any(ProductSearchDTO.class))).thenReturn(pageResult);

            mockMvc.perform(get("/product/search")
                            .param("keyword", "iPhone")
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(8001));

            verify(productSearchService).search(any(ProductSearchDTO.class));
        }
    }

    // ==================== 搜索建议和热门搜索词 ====================

    @Nested
    @DisplayName("搜索建议和热门搜索词")
    class SuggestAndHotKeywordsTest {

        @Test
        @DisplayName("获取搜索建议 → 返回200和建议词列表")
        void suggest_success_returnsSuggestionList() throws Exception {
            List<String> suggestions = List.of("iPhone 15", "iPhone 14", "iPhone壳");

            when(productSearchService.suggest(eq("iPhone"))).thenReturn(suggestions);

            mockMvc.perform(get("/product/suggest")
                            .param("keyword", "iPhone"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0]").value("iPhone 15"));

            verify(productSearchService).suggest(eq("iPhone"));
        }

        @Test
        @DisplayName("获取热门搜索词 → 返回200和热词列表")
        void getHotKeywords_success_returnsHotKeywords() throws Exception {
            List<String> hotKeywords = List.of("手机", "电脑", "耳机");

            when(productSearchService.getHotKeywords()).thenReturn(hotKeywords);

            mockMvc.perform(get("/product/hot-keywords"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0]").value("手机"));

            verify(productSearchService).getHotKeywords();
        }
    }

    // ==================== Feign接口：获取SKU信息 ====================

    @Nested
    @DisplayName("获取SKU信息 GET /product/sku/{skuId}")
    class GetSkuByIdTest {

        @Test
        @DisplayName("正常获取SKU信息 → 返回200和SKU详情")
        void getSkuById_success() throws Exception {
            ProductSkuVO skuVO = new ProductSkuVO();
            skuVO.setId(9001L);
            skuVO.setProductId(8001L);
            skuVO.setPrice(new BigDecimal("9999.00"));
            skuVO.setStock(100);
            skuVO.setSpecValues(Map.of("颜色", "蓝色", "存储", "256G"));

            when(productService.getSkuById(eq(9001L))).thenReturn(skuVO);

            mockMvc.perform(get("/product/sku/{skuId}", 9001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(9001))
                    .andExpect(jsonPath("$.data.price").value(9999.00))
                    .andExpect(jsonPath("$.data.stock").value(100));

            verify(productService).getSkuById(eq(9001L));
        }
    }

    // ==================== Feign接口：扣减库存 ====================

    @Nested
    @DisplayName("扣减库存 POST /product/sku/{skuId}/deduct")
    class DeductStockTest {

        @Test
        @DisplayName("正常扣减库存 → 返回200")
        void deductStock_success() throws Exception {
            when(productService.deductStockWithIdempotent(eq(9001L), eq(2), eq("ORD20240101001")))
                    .thenReturn(true);

            mockMvc.perform(post("/product/sku/{skuId}/deduct", 9001L)
                            .param("quantity", "2")
                            .param("orderNo", "ORD20240101001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("扣减成功"));

            verify(productService).deductStockWithIdempotent(eq(9001L), eq(2), eq("ORD20240101001"));
        }

        @Test
        @DisplayName("库存不足 → 返回业务错误码")
        void deductStock_insufficientStock_returnsErrorCode() throws Exception {
            when(productService.deductStockWithIdempotent(anyLong(), anyInt(), anyString()))
                    .thenReturn(false);

            mockMvc.perform(post("/product/sku/{skuId}/deduct", 9001L)
                            .param("quantity", "200")
                            .param("orderNo", "ORD20240101002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode()))
                    .andExpect(jsonPath("$.message").value("库存不足或扣减失败"));
        }
    }

    // ==================== Feign接口：回滚库存 ====================

    @Nested
    @DisplayName("回滚库存 POST /product/sku/{skuId}/add")
    class AddStockTest {

        @Test
        @DisplayName("正常回滚库存 → 返回200")
        void addStock_success() throws Exception {
            mockMvc.perform(post("/product/sku/{skuId}/add", 9001L)
                            .param("quantity", "2")
                            .param("orderNo", "ORD20240101001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("回滚成功"));

            verify(productService).addStockWithIdempotent(eq(9001L), eq(2), eq("ORD20240101001"));
        }
    }

    // ==================== 全量同步ES ====================

    @Nested
    @DisplayName("全量同步ES POST /product/sync-all")
    class SyncAllToESTest {

        @Test
        @DisplayName("正常同步ES → 返回200和同步数量")
        void syncAllToES_success() throws Exception {
            when(productSearchService.syncAllToES()).thenReturn(500);

            mockMvc.perform(post("/product/sync-all")
                            .header("X-Shop-Id", MOCK_SHOP_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("同步完成"))
                    .andExpect(jsonPath("$.data").value(500));

            verify(productSearchService).syncAllToES();
        }
    }

    // ==================== 管理后台：分页查询商品列表 ====================

    @Nested
    @DisplayName("管理后台：分页查询商品列表 GET /product/admin/list")
    class AdminListProductsTest {

        @Test
        @DisplayName("默认查询 → 返回200和分页数据（含SKU最低价和总库存）")
        void adminListProducts_default_returnsPageResult() throws Exception {
            // 1. 准备分页查询结果（2个商品）
            Product product1 = buildProduct(8001L, "iPhone 15", 1);
            Product product2 = buildProduct(8002L, "iPhone 14", 0);
            Page<Product> page = new Page<>(1, 10);
            page.setRecords(List.of(product1, product2));
            page.setTotal(2);

            // 2. 准备SKU列表（用于计算最低价和总库存）
            ProductSku sku1 = buildSku(8001L, new BigDecimal("9999.00"), 50);
            ProductSku sku2 = buildSku(8001L, new BigDecimal("10999.00"), 50);
            ProductSku sku3 = buildSku(8002L, new BigDecimal("5999.00"), 100);

            // 3. mock Mapper行为
            when(productMapper.selectPage(any(Page.class), any())).thenReturn(page);
            when(productSkuMapper.selectList(any())).thenReturn(List.of(sku1, sku2, sku3));

            // 4. 发送请求并验证结果
            mockMvc.perform(get("/product/admin/list")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    // 商品1：最低价9999，总库存100
                    .andExpect(jsonPath("$.data.records[0].id").value(8001))
                    .andExpect(jsonPath("$.data.records[0].minPrice").value(9999.00))
                    .andExpect(jsonPath("$.data.records[0].totalStock").value(100))
                    // 商品2：最低价5999，总库存100
                    .andExpect(jsonPath("$.data.records[1].id").value(8002))
                    .andExpect(jsonPath("$.data.records[1].minPrice").value(5999.00))
                    .andExpect(jsonPath("$.data.records[1].totalStock").value(100));

            verify(productMapper).selectPage(any(Page.class), any());
            verify(productSkuMapper).selectList(any());
        }

        @Test
        @DisplayName("按分类、状态和关键词筛选 → 返回200和筛选结果")
        void adminListProducts_withFilters_returnsFilteredResult() throws Exception {
            Product product = buildProduct(8001L, "iPhone 15", 1);
            Page<Product> page = new Page<>(1, 10);
            page.setRecords(List.of(product));
            page.setTotal(1);

            ProductSku sku = buildSku(8001L, new BigDecimal("9999.00"), 50);

            when(productMapper.selectPage(any(Page.class), any())).thenReturn(page);
            when(productSkuMapper.selectList(any())).thenReturn(List.of(sku));

            mockMvc.perform(get("/product/admin/list")
                            .param("page", "1")
                            .param("size", "10")
                            .param("categoryId", "10")
                            .param("status", "1")
                            .param("keyword", "iPhone"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(8001))
                    .andExpect(jsonPath("$.data.records[0].name").value("iPhone 15"));

            verify(productMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("查询结果为空 → 返回200和空列表")
        void adminListProducts_emptyResult_returnsEmptyPage() throws Exception {
            Page<Product> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(productMapper.selectPage(any(Page.class), any())).thenReturn(page);

            mockMvc.perform(get("/product/admin/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.records").isArray());
        }
    }
}
