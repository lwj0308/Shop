package com.shop.cart.controller;

import com.shop.cart.service.CartService;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.result.ErrorCode;
import com.shop.common.util.SecurityUtils;
import com.shop.model.cart.dto.CartAddDTO;
import com.shop.model.cart.dto.CartCheckDTO;
import com.shop.model.cart.dto.CartUpdateDTO;
import com.shop.model.cart.vo.CartItemVO;
import com.shop.model.cart.vo.CartVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CartController 切片测试（B-S-06）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 我们用 Standalone MockMvc 的方式，手动把Controller和它的依赖组装起来，
 * 这样测试跑得快，又不需要真的连数据库、Redis、Nacos等中间件。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确（URL能否映射到对应方法）
 * 2. 返回格式是否正确（统一Result格式，code=200表示成功）
 * 3. Service调用是否正确（参数传递、调用次数）
 * 4. 参数校验是否正确（@NotNull、@Min、@Max、@NotEmpty等注解触发校验）
 * 5. 异常处理是否正确（BusinessException返回业务错误码）
 * </p>
 * <p>
 * 注意：
 * 1. CartController 用 SecurityUtils.getCurrentUserId() 获取用户ID，
 *    测试中通过 mockStatic(SecurityUtils.class) 来模拟登录态。
 * 2. 所有接口都需要登录，未登录场景由Sa-Token拦截器处理，不在切片测试范围内。
 * 3. CartController 没有 LambdaQueryWrapper 使用，不需要初始化 MyBatis-Plus 缓存。
 * 4. 不含 LocalDateTime 字段，ObjectMapper 不需要注册 JavaTimeModule。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CartController 切片测试")
class CartControllerTest {

    /** Mock的购物车服务（假装的Service，不真的执行业务逻辑） */
    @Mock
    private CartService cartService;

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 模拟的登录用户ID */
    private static final Long MOCK_USER_ID = 1001L;

    /** SecurityUtils的静态mock（用来模拟登录态） */
    private MockedStatic<SecurityUtils> securityUtilsMock;

    /**
     * 每个测试方法执行前的准备工作
     * <p>
     * 1. 创建CartController实例，注入Mock的CartService
     * 2. 构建MockMvc，并设置全局异常处理器（这样异常能被正确处理）
     * 3. mock SecurityUtils.getCurrentUserId() 返回模拟的用户ID
     * </p>
     */
    @BeforeEach
    void setUp() {
        // 创建Controller并注入Mock依赖，再绑定全局异常处理器
        CartController controller = new CartController(cartService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 模拟SecurityUtils.getCurrentUserId()静态方法，返回模拟用户ID
        // 小白理解：原本这个方法会从Sa-Token获取登录用户ID，这里我们"假装"已登录用户ID是1001
        securityUtilsMock = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(MOCK_USER_ID);
    }

    /**
     * 每个测试方法执行后的清理工作
     * <p>
     * 关闭静态mock，避免影响后续测试。
     * </p>
     */
    @AfterEach
    void tearDown() {
        if (securityUtilsMock != null) {
            securityUtilsMock.close();
        }
    }

    // ==================== 1. 加入购物车测试 ====================

    /**
     * 加入购物车接口测试组
     * <p>
     * POST /cart，参数：CartAddDTO（productId、skuId、quantity）
     * </p>
     */
    @Nested
    @DisplayName("POST /cart - 加入购物车")
    class AddToCartTest {

        /**
         * 测试正常加入购物车
         * <p>
         * 场景：传入合法的商品ID、SKU ID和数量，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常加入购物车 - 返回成功")
        void addToCart_Success() throws Exception {
            // 准备测试数据
            CartAddDTO dto = new CartAddDTO();
            dto.setProductId(1L);
            dto.setSkuId(10L);
            dto.setQuantity(2);

            // 执行请求并验证结果
            mockMvc.perform(post("/cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("加入购物车成功"));

            // 验证Service被正确调用，参数传递正确
            verify(cartService).addToCart(eq(MOCK_USER_ID), any(CartAddDTO.class));
        }

        /**
         * 测试商品ID为空
         * <p>
         * 场景：不传productId，应返回400参数校验错误
         * </p>
         */
        @Test
        @DisplayName("商品ID为空 - 返回400")
        void addToCart_ProductIdNull() throws Exception {
            CartAddDTO dto = new CartAddDTO();
            dto.setSkuId(10L);
            dto.setQuantity(1);

            mockMvc.perform(post("/cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试SKU ID为空
         * <p>
         * 场景：不传skuId，应返回400参数校验错误
         * </p>
         */
        @Test
        @DisplayName("SKU ID为空 - 返回400")
        void addToCart_SkuIdNull() throws Exception {
            CartAddDTO dto = new CartAddDTO();
            dto.setProductId(1L);
            dto.setQuantity(1);

            mockMvc.perform(post("/cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试数量为空
         * <p>
         * 场景：不传quantity，应返回400参数校验错误
         * </p>
         */
        @Test
        @DisplayName("数量为空 - 返回400")
        void addToCart_QuantityNull() throws Exception {
            CartAddDTO dto = new CartAddDTO();
            dto.setProductId(1L);
            dto.setSkuId(10L);

            mockMvc.perform(post("/cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试数量小于1
         * <p>
         * 场景：传入quantity=0，应返回400参数校验错误（@Min(1)校验）
         * </p>
         */
        @Test
        @DisplayName("数量小于1 - 返回400")
        void addToCart_QuantityLessThanOne() throws Exception {
            CartAddDTO dto = new CartAddDTO();
            dto.setProductId(1L);
            dto.setSkuId(10L);
            dto.setQuantity(0);

            mockMvc.perform(post("/cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试Service抛出业务异常
         * <p>
         * 场景：Service抛出库存不足异常，应返回业务错误码
         * 验证GlobalExceptionHandler能正确处理BusinessException
         * </p>
         */
        @Test
        @DisplayName("Service抛出业务异常 - 返回业务错误码")
        void addToCart_BusinessException() throws Exception {
            CartAddDTO dto = new CartAddDTO();
            dto.setProductId(1L);
            dto.setSkuId(10L);
            dto.setQuantity(2);

            // 模拟Service抛出库存不足异常
            doThrow(new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH))
                    .when(cartService).addToCart(eq(MOCK_USER_ID), any(CartAddDTO.class));

            mockMvc.perform(post("/cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode()));
        }
    }

    // ==================== 2. 修改购物车项测试 ====================

    /**
     * 修改购物车项接口测试组
     * <p>
     * PUT /cart/{id}，参数：PathVariable id、CartUpdateDTO（quantity、checked）
     * </p>
     */
    @Nested
    @DisplayName("PUT /cart/{id} - 修改购物车项")
    class UpdateCartItemTest {

        /**
         * 测试正常修改数量
         * <p>
         * 场景：传入购物车项ID和合法的数量，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常修改数量 - 返回成功")
        void updateCartItem_Success() throws Exception {
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setQuantity(3);

            mockMvc.perform(put("/cart/{id}", 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"));

            // 验证Service被正确调用，参数传递正确
            verify(cartService).updateCartItem(eq(MOCK_USER_ID), eq(100L), any(CartUpdateDTO.class));
        }

        /**
         * 测试数量小于1
         * <p>
         * 场景：传入quantity=0，应返回400参数校验错误（@Min(1)校验）
         * </p>
         */
        @Test
        @DisplayName("数量小于1 - 返回400")
        void updateCartItem_QuantityLessThanOne() throws Exception {
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setQuantity(0);

            mockMvc.perform(put("/cart/{id}", 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试勾选状态不在0-1范围
         * <p>
         * 场景：传入checked=2，应返回400参数校验错误（@Max(1)校验）
         * </p>
         */
        @Test
        @DisplayName("勾选状态不在0-1范围 - 返回400")
        void updateCartItem_CheckedOutOfRange() throws Exception {
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setChecked(2);

            mockMvc.perform(put("/cart/{id}", 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试Service抛出业务异常
         * <p>
         * 场景：Service抛出购物车项不存在异常，应返回业务错误码
         * </p>
         */
        @Test
        @DisplayName("Service抛出业务异常 - 返回业务错误码")
        void updateCartItem_BusinessException() throws Exception {
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setQuantity(3);

            doThrow(new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND))
                    .when(cartService).updateCartItem(eq(MOCK_USER_ID), eq(100L), any(CartUpdateDTO.class));

            mockMvc.perform(put("/cart/{id}", 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.CART_ITEM_NOT_FOUND.getCode()));
        }
    }

    // ==================== 3. 删除购物车项测试 ====================

    /**
     * 删除购物车项接口测试组
     * <p>
     * DELETE /cart/{id}，参数：PathVariable id
     * </p>
     */
    @Nested
    @DisplayName("DELETE /cart/{id} - 删除购物车项")
    class DeleteCartItemTest {

        /**
         * 测试正常删除购物车项
         * <p>
         * 场景：传入合法的购物车项ID，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常删除购物车项 - 返回成功")
        void deleteCartItem_Success() throws Exception {
            mockMvc.perform(delete("/cart/{id}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("删除成功"));

            verify(cartService).deleteCartItem(MOCK_USER_ID, 100L);
        }

        /**
         * 测试Service抛出业务异常
         * <p>
         * 场景：Service抛出购物车项不存在异常，应返回业务错误码
         * </p>
         */
        @Test
        @DisplayName("Service抛出业务异常 - 返回业务错误码")
        void deleteCartItem_BusinessException() throws Exception {
            doThrow(new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND))
                    .when(cartService).deleteCartItem(MOCK_USER_ID, 100L);

            mockMvc.perform(delete("/cart/{id}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.CART_ITEM_NOT_FOUND.getCode()));
        }
    }

    // ==================== 4. 批量勾选测试 ====================

    /**
     * 批量勾选接口测试组
     * <p>
     * PUT /cart/check，参数：CartCheckDTO（cartItemIds、checked）
     * </p>
     */
    @Nested
    @DisplayName("PUT /cart/check - 批量勾选")
    class BatchCheckTest {

        /**
         * 测试正常批量勾选
         * <p>
         * 场景：传入合法的购物车项ID列表和勾选状态，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常批量勾选 - 返回成功")
        void batchCheck_Success() throws Exception {
            CartCheckDTO dto = new CartCheckDTO();
            dto.setCartItemIds(Arrays.asList(100L, 101L, 102L));
            dto.setChecked(1);

            mockMvc.perform(put("/cart/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("操作成功"));

            verify(cartService).batchCheck(eq(MOCK_USER_ID), any(CartCheckDTO.class));
        }

        /**
         * 测试购物车项ID列表为空
         * <p>
         * 场景：传入空列表，应返回400参数校验错误（@NotEmpty校验）
         * </p>
         */
        @Test
        @DisplayName("购物车项ID列表为空 - 返回400")
        void batchCheck_CartItemIdsEmpty() throws Exception {
            CartCheckDTO dto = new CartCheckDTO();
            dto.setCartItemIds(Collections.emptyList());
            dto.setChecked(1);

            mockMvc.perform(put("/cart/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试勾选状态为空
         * <p>
         * 场景：不传checked，应返回400参数校验错误（@NotNull校验）
         * </p>
         */
        @Test
        @DisplayName("勾选状态为空 - 返回400")
        void batchCheck_CheckedNull() throws Exception {
            CartCheckDTO dto = new CartCheckDTO();
            dto.setCartItemIds(Arrays.asList(100L, 101L));

            mockMvc.perform(put("/cart/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试勾选状态不在0-1范围
         * <p>
         * 场景：传入checked=5，应返回400参数校验错误（@Max(1)校验）
         * </p>
         */
        @Test
        @DisplayName("勾选状态不在0-1范围 - 返回400")
        void batchCheck_CheckedOutOfRange() throws Exception {
            CartCheckDTO dto = new CartCheckDTO();
            dto.setCartItemIds(Arrays.asList(100L, 101L));
            dto.setChecked(5);

            mockMvc.perform(put("/cart/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 5. 全选/取消全选测试 ====================

    /**
     * 全选/取消全选接口测试组
     * <p>
     * PUT /cart/check-all?checked={checked}，参数：RequestParam checked
     * </p>
     */
    @Nested
    @DisplayName("PUT /cart/check-all - 全选/取消全选")
    class CheckAllTest {

        /**
         * 测试全选
         * <p>
         * 场景：传入checked=true，应返回成功
         * </p>
         */
        @Test
        @DisplayName("全选 - 返回成功")
        void checkAll_SelectAll() throws Exception {
            mockMvc.perform(put("/cart/check-all")
                            .param("checked", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("操作成功"));

            verify(cartService).checkAll(MOCK_USER_ID, true);
        }

        /**
         * 测试取消全选
         * <p>
         * 场景：传入checked=false，应返回成功
         * </p>
         */
        @Test
        @DisplayName("取消全选 - 返回成功")
        void checkAll_UnselectAll() throws Exception {
            mockMvc.perform(put("/cart/check-all")
                            .param("checked", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("操作成功"));

            verify(cartService).checkAll(MOCK_USER_ID, false);
        }

        /**
         * 测试缺少checked参数
         * <p>
         * 场景：不传checked参数，应返回400（RequestParam required=true）
         * </p>
         */
        @Test
        @DisplayName("缺少checked参数 - 返回400")
        void checkAll_MissingParam() throws Exception {
            mockMvc.perform(put("/cart/check-all"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 6. 获取购物车列表测试 ====================

    /**
     * 获取购物车列表接口测试组
     * <p>
     * GET /cart/list，无参数
     * </p>
     */
    @Nested
    @DisplayName("GET /cart/list - 获取购物车列表")
    class GetCartListTest {

        /**
         * 测试正常获取购物车列表
         * <p>
         * 场景：购物车中有商品，应返回购物车列表及总数量、选中总价等信息
         * </p>
         */
        @Test
        @DisplayName("购物车有商品 - 返回列表")
        void getCartList_HasItems() throws Exception {
            // 构造购物车项VO
            CartItemVO item1 = new CartItemVO();
            item1.setId(100L);
            item1.setProductId(1L);
            item1.setSkuId(10L);
            item1.setProductName("iPhone 16");
            item1.setSkuPrice(new BigDecimal("5999.00"));
            item1.setQuantity(2);
            item1.setChecked(1);
            item1.setSubtotal(new BigDecimal("11998.00"));

            CartItemVO item2 = new CartItemVO();
            item2.setId(101L);
            item2.setProductId(2L);
            item2.setSkuId(20L);
            item2.setProductName("iPhone 16 Pro Case");
            item2.setSkuPrice(new BigDecimal("199.00"));
            item2.setQuantity(1);
            item2.setChecked(0);
            item2.setSubtotal(new BigDecimal("199.00"));

            // 构造购物车VO
            CartVO cartVO = new CartVO();
            cartVO.setItems(Arrays.asList(item1, item2));
            cartVO.setTotalCount(3);
            cartVO.setCheckedTotalPrice(new BigDecimal("11998.00"));
            cartVO.setIsAllChecked(false);

            when(cartService.getCartList(MOCK_USER_ID)).thenReturn(cartVO);

            mockMvc.perform(get("/cart/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.totalCount").value(3))
                    .andExpect(jsonPath("$.data.items[0].productName").value("iPhone 16"))
                    .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.data.checkedTotalPrice").value(11998.00))
                    .andExpect(jsonPath("$.data.isAllChecked").value(false));
        }

        /**
         * 测试空购物车
         * <p>
         * 场景：购物车中没有商品，应返回空列表和0总数量
         * </p>
         */
        @Test
        @DisplayName("空购物车 - 返回空列表")
        void getCartList_Empty() throws Exception {
            CartVO cartVO = new CartVO();
            cartVO.setItems(Collections.emptyList());
            cartVO.setTotalCount(0);
            cartVO.setCheckedTotalPrice(BigDecimal.ZERO);
            cartVO.setIsAllChecked(true);

            when(cartService.getCartList(MOCK_USER_ID)).thenReturn(cartVO);

            mockMvc.perform(get("/cart/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.totalCount").value(0))
                    .andExpect(jsonPath("$.data.isAllChecked").value(true));
        }
    }

    // ==================== 7. 清空购物车测试 ====================

    /**
     * 清空购物车接口测试组
     * <p>
     * DELETE /cart/clear，无参数
     * </p>
     */
    @Nested
    @DisplayName("DELETE /cart/clear - 清空购物车")
    class ClearCartTest {

        /**
         * 测试正常清空购物车
         * <p>
         * 场景：调用清空接口，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常清空购物车 - 返回成功")
        void clearCart_Success() throws Exception {
            mockMvc.perform(delete("/cart/clear"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("清空成功"));

            verify(cartService).clearCart(MOCK_USER_ID);
        }
    }

    // ==================== 8. 获取购物车数量测试 ====================

    /**
     * 获取购物车数量接口测试组
     * <p>
     * GET /cart/count，无参数
     * </p>
     */
    @Nested
    @DisplayName("GET /cart/count - 获取购物车数量")
    class GetCartCountTest {

        /**
         * 测试购物车有商品
         * <p>
         * 场景：购物车中有3件商品，应返回3
         * </p>
         */
        @Test
        @DisplayName("购物车有商品 - 返回数量")
        void getCartCount_HasItems() throws Exception {
            when(cartService.getCartCount(MOCK_USER_ID)).thenReturn(3);

            mockMvc.perform(get("/cart/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(3));
        }

        /**
         * 测试空购物车
         * <p>
         * 场景：购物车为空，应返回0
         * </p>
         */
        @Test
        @DisplayName("空购物车 - 返回0")
        void getCartCount_Empty() throws Exception {
            when(cartService.getCartCount(MOCK_USER_ID)).thenReturn(0);

            mockMvc.perform(get("/cart/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(0));
        }
    }

    // ==================== 9. 合并购物车测试 ====================

    /**
     * 合并购物车接口测试组
     * <p>
     * POST /cart/merge，参数：List<CartAddDTO>
     * </p>
     */
    @Nested
    @DisplayName("POST /cart/merge - 合并购物车")
    class MergeCartTest {

        /**
         * 测试正常合并购物车
         * <p>
         * 场景：传入合法的购物车项列表，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常合并购物车 - 返回成功")
        void mergeCart_Success() throws Exception {
            CartAddDTO item1 = new CartAddDTO();
            item1.setProductId(1L);
            item1.setSkuId(10L);
            item1.setQuantity(2);

            CartAddDTO item2 = new CartAddDTO();
            item2.setProductId(2L);
            item2.setSkuId(20L);
            item2.setQuantity(1);

            List<CartAddDTO> items = Arrays.asList(item1, item2);

            mockMvc.perform(post("/cart/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(items)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("合并成功"));

            verify(cartService).mergeCart(eq(MOCK_USER_ID), any());
        }

        /**
         * 测试合并列表中包含无效商品（数量小于1）
         * <p>
         * 小白理解：@Validated 加在 List 参数上时，Spring 不会对 List 里的每个元素
         * 做校验（这叫"级联校验"，List 参数默认不级联）。所以即使列表里有一项
         * quantity=0，Controller 也不会拦截，而是把整个列表原样传给 Service。
         * Service 内部会逐项校验商品状态和库存。
         * </p>
         * <p>
         * 场景：传入的列表中有一项quantity=0，Controller 不拦截，Service 被调用
         * </p>
         */
        @Test
        @DisplayName("合并列表含无效商品数量 - Controller不拦截，Service被调用")
        void mergeCart_InvalidQuantity_NotValidatedAtController() throws Exception {
            CartAddDTO item1 = new CartAddDTO();
            item1.setProductId(1L);
            item1.setSkuId(10L);
            item1.setQuantity(2);

            CartAddDTO invalidItem = new CartAddDTO();
            invalidItem.setProductId(2L);
            invalidItem.setSkuId(20L);
            invalidItem.setQuantity(0);

            List<CartAddDTO> items = Arrays.asList(item1, invalidItem);

            // Controller 不会对 List 元素做校验，直接返回成功
            mockMvc.perform(post("/cart/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(items)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // 验证 Service 仍然被调用（元素的合法性由 Service 内部校验）
            verify(cartService).mergeCart(eq(MOCK_USER_ID), any());
        }

        /**
         * 测试Service抛出业务异常
         * <p>
         * 场景：Service抛出商品已下架异常，应返回业务错误码
         * </p>
         */
        @Test
        @DisplayName("Service抛出业务异常 - 返回业务错误码")
        void mergeCart_BusinessException() throws Exception {
            CartAddDTO item = new CartAddDTO();
            item.setProductId(1L);
            item.setSkuId(10L);
            item.setQuantity(1);

            List<CartAddDTO> items = Collections.singletonList(item);

            doThrow(new BusinessException(ErrorCode.PRODUCT_OFF_SHELF))
                    .when(cartService).mergeCart(eq(MOCK_USER_ID), any());

            mockMvc.perform(post("/cart/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(items)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_OFF_SHELF.getCode()));
        }
    }
}
