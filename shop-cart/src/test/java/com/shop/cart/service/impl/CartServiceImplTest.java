package com.shop.cart.service.impl;

import com.shop.cart.feign.ProductFeignClient;
import com.shop.cart.mapper.CartItemMapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.cart.dto.CartAddDTO;
import com.shop.model.cart.dto.CartCheckDTO;
import com.shop.model.cart.dto.CartUpdateDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.model.cart.entity.CartItem;
import com.shop.model.cart.vo.CartVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 购物车服务实现类（CartServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证购物车的各种操作能不能正常工作，比如加购、修改、删除、勾选等。
 * 简单理解：我们把真正访问数据库的 Mapper 和访问商品服务的 FeignClient 都"假装"一下（Mock），
 * 这样测试就不需要真的连数据库和网络，跑得又快又稳定。
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值，比如假装数据库查到了一条记录
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1) 比 assertEquals(1, x) 更好懂
 * </p>
 * <p>
 * 测试覆盖的10个方法：addToCart、updateCartItem、deleteCartItem、batchCheck、checkAll、
 * getCartList、clearCart、getCartCount、deleteBySkuIds、mergeCart
 * </p>
 */
@DisplayName("购物车服务 CartServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    /** 假装数据库操作的 Mapper，不真的连数据库 */
    @Mock
    private CartItemMapper cartItemMapper;

    /** 假装远程调用商品服务的 Feign 客户端，不真的发 HTTP 请求 */
    @Mock
    private ProductFeignClient productFeignClient;

    /** 被测试的购物车服务，Mockito 会自动把上面两个 Mock 注入进来 */
    @InjectMocks
    private CartServiceImpl cartService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long USER_ID = 1001L;          // 当前用户ID
    private static final Long OTHER_USER_ID = 1002L;     // 另一个用户ID（测试越权用）
    private static final Long PRODUCT_ID = 2001L;        // 商品ID
    private static final Long SKU_ID = 3001L;            // SKU规格ID
    private static final Long CART_ITEM_ID = 5001L;      // 购物车项ID

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：CartServiceImpl 的 getCartCount 方法用到了 .select(CartItem::getQuantity)，
     * 这行代码会让 MyBatis-Plus 去查"quantity 字段对应数据库哪一列"。
     * 正常启动 Spring 时框架会自动做这件事，但单元测试没有 Spring 环境，
     * 所以需要我们手动告诉 MyBatis-Plus：CartItem 这个实体有哪些字段、对应哪些列。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CartItem.class
        );
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个加购请求DTO
     * 小白理解：用户点"加入购物车"时前端传过来的参数
     *
     * @param productId 商品ID
     * @param skuId     SKU规格ID
     * @param quantity  数量
     * @return 构造好的CartAddDTO
     */
    private CartAddDTO buildCartAddDTO(Long productId, Long skuId, Integer quantity) {
        CartAddDTO dto = new CartAddDTO();
        dto.setProductId(productId);
        dto.setSkuId(skuId);
        dto.setQuantity(quantity);
        return dto;
    }

    /**
     * 构造一个购物车项实体
     * 小白理解：数据库 cart_item 表里的一条记录
     *
     * @param id       购物车项ID
     * @param userId   用户ID
     * @param productId 商品ID
     * @param skuId    SKU ID
     * @param quantity 数量
     * @param checked  勾选状态：0未勾选 1已勾选
     * @return 构造好的CartItem
     */
    private CartItem buildCartItem(Long id, Long userId, Long productId, Long skuId, Integer quantity, Integer checked) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(userId);
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setChecked(checked);
        return item;
    }

    /**
     * 模拟商品服务正常返回（商品上架、SKU存在、库存充足）
     * 小白理解：让假的商品服务返回"一切正常"的结果，加购校验就能通过
     *
     * @param productId 商品ID
     * @param skuId     SKU ID
     */
    private void mockProductFeignSuccess(Long productId, Long skuId) {
        // 商品基本信息：status=1表示上架中
        Map<String, Object> basicInfo = new HashMap<>();
        basicInfo.put("status", 1);
        when(productFeignClient.getProductBasicInfo(productId)).thenReturn(Result.success(basicInfo));
        // SKU价格：返回10.00元（非null表示SKU存在）
        when(productFeignClient.getSkuPrice(skuId)).thenReturn(Result.success(new BigDecimal("10.00")));
        // 库存状态：true表示有货
        when(productFeignClient.getStockStatus(skuId)).thenReturn(Result.success(true));
    }

    // ==================== 1. addToCart 加入购物车 ====================

    @Nested
    @DisplayName("addToCart 加入购物车")
    class AddToCartTest {

        @Test
        @DisplayName("加购数量超过99件上限 → 抛出购物车数量超限异常")
        void addToCart_quantityExceedLimit_throwsException() {
            // 场景：用户一次性加购100件，超过了单条99件的上限
            CartAddDTO dto = buildCartAddDTO(PRODUCT_ID, SKU_ID, 100);

            // 验证：应该抛出 BusinessException，错误码是 CART_ITEM_LIMIT_EXCEED（60003）
            assertThatThrownBy(() -> cartService.addToCart(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.CART_ITEM_LIMIT_EXCEED.getCode());

            // 验证：数量校验在最前面就失败了，不应该调用任何数据库或商品服务
            verifyNoInteractions(cartItemMapper);
            verifyNoInteractions(productFeignClient);
        }

        @Test
        @DisplayName("SKU已存在 → 数量累加并调用updateById")
        void addToCart_skuExists_quantityAccumulated_updateById() {
            // 场景：购物车里已有2件该SKU，再加3件，变成5件（累加而不是新增记录）
            CartAddDTO dto = buildCartAddDTO(PRODUCT_ID, SKU_ID, 3);
            CartItem existItem = buildCartItem(CART_ITEM_ID, USER_ID, PRODUCT_ID, SKU_ID, 2, 1);

            // 模拟商品服务正常 + 购物车里已存在该SKU
            mockProductFeignSuccess(PRODUCT_ID, SKU_ID);
            when(cartItemMapper.selectOne(any())).thenReturn(existItem);

            // 执行加购
            cartService.addToCart(USER_ID, dto);

            // 验证：调用了updateById（数量累加），没有调用insert（不新增记录）
            verify(cartItemMapper).updateById(any(CartItem.class));
            verify(cartItemMapper, never()).insert(any(CartItem.class));
            // 验证累加后数量为5（2+3）
            assertThat(existItem.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("SKU已存在且累加后超过99件 → 抛出异常")
        void addToCart_skuExists_accumulatedExceedLimit_throwsException() {
            // 场景：购物车里已有60件，再加50件，累加后110件超过99件上限
            CartAddDTO dto = buildCartAddDTO(PRODUCT_ID, SKU_ID, 50);
            CartItem existItem = buildCartItem(CART_ITEM_ID, USER_ID, PRODUCT_ID, SKU_ID, 60, 1);

            mockProductFeignSuccess(PRODUCT_ID, SKU_ID);
            when(cartItemMapper.selectOne(any())).thenReturn(existItem);

            // 验证：抛出数量超限异常
            assertThatThrownBy(() -> cartService.addToCart(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.CART_ITEM_LIMIT_EXCEED.getCode());

            // 验证：校验失败，没有调用updateById
            verify(cartItemMapper, never()).updateById(any(CartItem.class));
        }

        @Test
        @DisplayName("新SKU且购物车种类已达100上限 → 抛出异常")
        void addToCart_newSku_skuCountReachesLimit_throwsException() {
            // 场景：加购一个新SKU，但购物车里已经有100种商品了（达到SKU种类上限）
            CartAddDTO dto = buildCartAddDTO(PRODUCT_ID, SKU_ID, 1);

            mockProductFeignSuccess(PRODUCT_ID, SKU_ID);
            // SKU不在购物车里（selectOne返回null）
            when(cartItemMapper.selectOne(any())).thenReturn(null);
            // 购物车已有100种SKU，达到上限
            when(cartItemMapper.selectCount(any())).thenReturn(100L);

            // 验证：抛出数量超限异常
            assertThatThrownBy(() -> cartService.addToCart(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.CART_ITEM_LIMIT_EXCEED.getCode());

            // 验证：没有调用insert
            verify(cartItemMapper, never()).insert(any(CartItem.class));
        }

        @Test
        @DisplayName("新SKU正常加购 → 调用insert新增记录")
        void addToCart_newSku_normal_insert() {
            // 场景：加购一个新SKU，购物车里还没满，正常新增一条记录
            CartAddDTO dto = buildCartAddDTO(PRODUCT_ID, SKU_ID, 1);

            mockProductFeignSuccess(PRODUCT_ID, SKU_ID);
            // SKU不在购物车里
            when(cartItemMapper.selectOne(any())).thenReturn(null);
            // 购物车只有5种SKU，没满
            when(cartItemMapper.selectCount(any())).thenReturn(5L);

            // 执行加购
            cartService.addToCart(USER_ID, dto);

            // 验证：调用了insert（新增记录），没有调用updateById
            verify(cartItemMapper).insert(any(CartItem.class));
            verify(cartItemMapper, never()).updateById(any(CartItem.class));
        }

        @Test
        @DisplayName("商品服务降级时仍允许加购（validateProductAndSku捕获异常）")
        void addToCart_productServiceDegraded_stillAllowAdd() {
            // 场景：商品服务挂了，但购物车服务应该降级放行，允许加购（结算时再校验）
            CartAddDTO dto = buildCartAddDTO(PRODUCT_ID, SKU_ID, 1);

            // 模拟商品服务调用抛异常（服务降级场景）
            when(productFeignClient.getProductBasicInfo(PRODUCT_ID)).thenThrow(new RuntimeException("服务不可用"));
            // SKU不在购物车里
            when(cartItemMapper.selectOne(any())).thenReturn(null);
            // 购物车没满
            when(cartItemMapper.selectCount(any())).thenReturn(5L);

            // 执行加购（不应该抛异常，降级放行）
            cartService.addToCart(USER_ID, dto);

            // 验证：仍然调用了insert（降级不阻止加购）
            verify(cartItemMapper).insert(any(CartItem.class));
        }
    }

    // ==================== 2. updateCartItem 修改购物车项 ====================

    @Nested
    @DisplayName("updateCartItem 修改购物车项")
    class UpdateCartItemTest {

        @Test
        @DisplayName("购物车项不存在 → 抛出CART_ITEM_NOT_FOUND异常")
        void updateCartItem_notFound_throwsException() {
            // 场景：修改一个不存在的购物车项
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setQuantity(5);

            // 模拟数据库查不到
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(null);

            // 验证：抛出购物车项不存在异常
            assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, CART_ITEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.CART_ITEM_NOT_FOUND.getCode());

            // 验证：没有调用updateById
            verify(cartItemMapper, never()).updateById(any(CartItem.class));
        }

        @Test
        @DisplayName("不属于当前用户 → 抛出FORBIDDEN异常")
        void updateCartItem_notOwner_throwsForbidden() {
            // 场景：购物车项存在，但属于另一个用户，当前用户想修改→无权操作
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setQuantity(5);

            // 购物车项存在，但属于OTHER_USER_ID（不是当前用户）
            CartItem cartItem = buildCartItem(CART_ITEM_ID, OTHER_USER_ID, PRODUCT_ID, SKU_ID, 2, 1);
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(cartItem);

            // 验证：抛出无权限异常（403）
            assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, CART_ITEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            // 验证：没有调用updateById
            verify(cartItemMapper, never()).updateById(any(CartItem.class));
        }

        @Test
        @DisplayName("修改数量超过99件上限 → 抛出异常")
        void updateCartItem_quantityExceedLimit_throwsException() {
            // 场景：把数量改成100件，超过单条99件上限
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setQuantity(100);

            CartItem cartItem = buildCartItem(CART_ITEM_ID, USER_ID, PRODUCT_ID, SKU_ID, 2, 1);
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(cartItem);

            // 验证：抛出数量超限异常
            assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, CART_ITEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.CART_ITEM_LIMIT_EXCEED.getCode());

            // 验证：没有调用updateById
            verify(cartItemMapper, never()).updateById(any(CartItem.class));
        }

        @Test
        @DisplayName("正常修改数量 → 调用updateById")
        void updateCartItem_normalUpdateQuantity() {
            // 场景：把数量从2改成5，库存充足
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setQuantity(5);

            CartItem cartItem = buildCartItem(CART_ITEM_ID, USER_ID, PRODUCT_ID, SKU_ID, 2, 1);
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(cartItem);
            // 模拟库存充足（修改数量时会校验库存）
            when(productFeignClient.getStockStatus(SKU_ID)).thenReturn(Result.success(true));

            // 执行修改
            cartService.updateCartItem(USER_ID, CART_ITEM_ID, dto);

            // 验证：调用了updateById，数量已改为5
            verify(cartItemMapper).updateById(any(CartItem.class));
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("正常修改勾选状态 → 调用updateById")
        void updateCartItem_normalUpdateChecked() {
            // 场景：只改勾选状态（取消勾选），不改数量
            CartUpdateDTO dto = new CartUpdateDTO();
            dto.setChecked(0); // 取消勾选

            CartItem cartItem = buildCartItem(CART_ITEM_ID, USER_ID, PRODUCT_ID, SKU_ID, 2, 1);
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(cartItem);

            // 执行修改
            cartService.updateCartItem(USER_ID, CART_ITEM_ID, dto);

            // 验证：调用了updateById，勾选状态已改为0
            verify(cartItemMapper).updateById(any(CartItem.class));
            assertThat(cartItem.getChecked()).isEqualTo(0);
            // 验证：只改勾选不需要查库存，所以没有调用商品服务
            verifyNoInteractions(productFeignClient);
        }
    }

    // ==================== 3. deleteCartItem 删除购物车项 ====================

    @Nested
    @DisplayName("deleteCartItem 删除购物车项")
    class DeleteCartItemTest {

        @Test
        @DisplayName("购物车项不存在 → 抛出CART_ITEM_NOT_FOUND异常")
        void deleteCartItem_notFound_throwsException() {
            // 场景：删除一个不存在的购物车项
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(null);

            // 验证：抛出购物车项不存在异常
            assertThatThrownBy(() -> cartService.deleteCartItem(USER_ID, CART_ITEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.CART_ITEM_NOT_FOUND.getCode());

            // 验证：没有调用deleteById
            verify(cartItemMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("不属于当前用户 → 抛出FORBIDDEN异常")
        void deleteCartItem_notOwner_throwsForbidden() {
            // 场景：购物车项属于别人，当前用户无权删除
            CartItem cartItem = buildCartItem(CART_ITEM_ID, OTHER_USER_ID, PRODUCT_ID, SKU_ID, 2, 1);
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(cartItem);

            // 验证：抛出无权限异常
            assertThatThrownBy(() -> cartService.deleteCartItem(USER_ID, CART_ITEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            // 验证：没有调用deleteById
            verify(cartItemMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("正常删除 → 调用deleteById")
        void deleteCartItem_normalDelete() {
            // 场景：删除自己的购物车项，正常删除
            CartItem cartItem = buildCartItem(CART_ITEM_ID, USER_ID, PRODUCT_ID, SKU_ID, 2, 1);
            when(cartItemMapper.selectById(CART_ITEM_ID)).thenReturn(cartItem);

            // 执行删除
            cartService.deleteCartItem(USER_ID, CART_ITEM_ID);

            // 验证：调用了deleteById
            verify(cartItemMapper).deleteById(CART_ITEM_ID);
        }
    }

    // ==================== 4. batchCheck 批量勾选 ====================

    @Nested
    @DisplayName("batchCheck 批量勾选")
    class BatchCheckTest {

        @Test
        @DisplayName("正常批量勾选 → 调用update方法")
        void batchCheck_normalBatchCheck() {
            // 场景：用户勾选了3个购物车项准备结算
            CartCheckDTO dto = new CartCheckDTO();
            dto.setCartItemIds(Arrays.asList(1L, 2L, 3L));
            dto.setChecked(1); // 勾选

            // 执行批量勾选
            cartService.batchCheck(USER_ID, dto);

            // 验证：调用了update方法（批量更新勾选状态）
            verify(cartItemMapper).update(any(CartItem.class), any());
        }
    }

    // ==================== 5. checkAll 全选/取消全选 ====================

    @Nested
    @DisplayName("checkAll 全选/取消全选")
    class CheckAllTest {

        @Test
        @DisplayName("全选（checked=true）→ 调用update设置checked=1")
        void checkAll_true() {
            // 场景：用户点击"全选"按钮，一键勾选所有商品
            cartService.checkAll(USER_ID, true);

            // 验证：调用了update方法（更新所有购物车项的勾选状态为1）
            verify(cartItemMapper).update(any(CartItem.class), any());
        }

        @Test
        @DisplayName("取消全选（checked=false）→ 调用update设置checked=0")
        void checkAll_false() {
            // 场景：用户点击"取消全选"按钮，一键取消所有勾选
            cartService.checkAll(USER_ID, false);

            // 验证：调用了update方法（更新所有购物车项的勾选状态为0）
            verify(cartItemMapper).update(any(CartItem.class), any());
        }
    }

    // ==================== 6. getCartList 获取购物车列表 ====================

    @Nested
    @DisplayName("getCartList 获取购物车列表")
    class GetCartListTest {

        @Test
        @DisplayName("空购物车 → isAllChecked=false，总价为0")
        void getCartList_emptyCart_isAllCheckedFalse() {
            // 场景：用户的购物车是空的，什么都没加过
            when(cartItemMapper.selectList(any())).thenReturn(Collections.emptyList());

            // 执行查询
            CartVO cartVO = cartService.getCartList(USER_ID);

            // 验证：购物车为空时，全选状态为false，总数和总价都是0
            assertThat(cartVO.getItems()).isEmpty();
            assertThat(cartVO.getTotalCount()).isEqualTo(0);
            assertThat(cartVO.getCheckedTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(cartVO.getIsAllChecked()).isFalse();
        }

        @Test
        @DisplayName("有商品且全选 → isAllChecked=true，正确计算总价")
        void getCartList_allChecked_calculateTotalPrice() {
            // 场景：购物车有2个商品，都勾选了
            // 商品1：数量2，单价10元 → 小计20元
            // 商品2：数量3，单价5元 → 小计15元
            // 选中总价 = 20 + 15 = 35元
            CartItem item1 = buildCartItem(1L, USER_ID, 100L, 200L, 2, 1);
            CartItem item2 = buildCartItem(2L, USER_ID, 101L, 201L, 3, 1);
            when(cartItemMapper.selectList(any())).thenReturn(Arrays.asList(item1, item2));

            // 模拟批量获取SKU信息（价格、库存、规格名称）
            Map<Long, Map<String, Object>> skuInfoMap = new HashMap<>();
            Map<String, Object> sku1Info = new HashMap<>();
            sku1Info.put("price", new BigDecimal("10.00"));
            sku1Info.put("stock", true);
            sku1Info.put("skuName", "规格A");
            Map<String, Object> sku2Info = new HashMap<>();
            sku2Info.put("price", new BigDecimal("5.00"));
            sku2Info.put("stock", true);
            sku2Info.put("skuName", "规格B");
            skuInfoMap.put(200L, sku1Info);
            skuInfoMap.put(201L, sku2Info);
            when(productFeignClient.batchGetSkuInfo(anyList())).thenReturn(Result.success(skuInfoMap));

            // 模拟获取商品基本信息（名称、图片）
            Map<String, Object> product1Info = new HashMap<>();
            product1Info.put("name", "商品A");
            product1Info.put("image", "imgA");
            when(productFeignClient.getProductBasicInfo(100L)).thenReturn(Result.success(product1Info));
            Map<String, Object> product2Info = new HashMap<>();
            product2Info.put("name", "商品B");
            product2Info.put("image", "imgB");
            when(productFeignClient.getProductBasicInfo(101L)).thenReturn(Result.success(product2Info));

            // 执行查询
            CartVO cartVO = cartService.getCartList(USER_ID);

            // 验证：总数=5（2+3），选中总价=35元，全选=true
            assertThat(cartVO.getTotalCount()).isEqualTo(5);
            assertThat(cartVO.getCheckedTotalPrice()).isEqualByComparingTo(new BigDecimal("35.00"));
            assertThat(cartVO.getIsAllChecked()).isTrue();
            assertThat(cartVO.getItems()).hasSize(2);
            // 验证第一个商品的小计金额 = 10 × 2 = 20元
            assertThat(cartVO.getItems().get(0).getSubtotal()).isEqualByComparingTo(new BigDecimal("20.00"));
            // 验证商品名称正确填充
            assertThat(cartVO.getItems().get(0).getProductName()).isEqualTo("商品A");
        }

        @Test
        @DisplayName("有商品部分选中 → 只计算选中商品的总价，isAllChecked=false")
        void getCartList_partialChecked_calculateCheckedTotalPrice() {
            // 场景：购物车有2个商品，只勾选了第一个
            // 商品1：勾选，数量2，单价10元 → 小计20元（计入总价）
            // 商品2：未勾选，数量3，单价5元 → 小计15元（不计入总价）
            // 选中总价 = 20元（只算勾选的）
            CartItem item1 = buildCartItem(1L, USER_ID, 100L, 200L, 2, 1);  // 勾选
            CartItem item2 = buildCartItem(2L, USER_ID, 101L, 201L, 3, 0);  // 未勾选
            when(cartItemMapper.selectList(any())).thenReturn(Arrays.asList(item1, item2));

            // 模拟批量获取SKU信息
            Map<Long, Map<String, Object>> skuInfoMap = new HashMap<>();
            Map<String, Object> sku1Info = new HashMap<>();
            sku1Info.put("price", new BigDecimal("10.00"));
            sku1Info.put("stock", true);
            sku1Info.put("skuName", "规格A");
            Map<String, Object> sku2Info = new HashMap<>();
            sku2Info.put("price", new BigDecimal("5.00"));
            sku2Info.put("stock", true);
            sku2Info.put("skuName", "规格B");
            skuInfoMap.put(200L, sku1Info);
            skuInfoMap.put(201L, sku2Info);
            when(productFeignClient.batchGetSkuInfo(anyList())).thenReturn(Result.success(skuInfoMap));

            // 模拟获取商品基本信息
            Map<String, Object> product1Info = new HashMap<>();
            product1Info.put("name", "商品A");
            product1Info.put("image", "imgA");
            when(productFeignClient.getProductBasicInfo(100L)).thenReturn(Result.success(product1Info));
            Map<String, Object> product2Info = new HashMap<>();
            product2Info.put("name", "商品B");
            product2Info.put("image", "imgB");
            when(productFeignClient.getProductBasicInfo(101L)).thenReturn(Result.success(product2Info));

            // 执行查询
            CartVO cartVO = cartService.getCartList(USER_ID);

            // 验证：总数=5（包括未勾选的），选中总价=20元（只算勾选的），全选=false
            assertThat(cartVO.getTotalCount()).isEqualTo(5);
            assertThat(cartVO.getCheckedTotalPrice()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(cartVO.getIsAllChecked()).isFalse();
        }

        @Test
        @DisplayName("商品服务降级 → 显示'商品信息暂不可用'")
        void getCartList_productServiceDegraded_showDefaultMessage() {
            // 场景：商品服务挂了，购物车列表仍能展示，但显示"商品信息暂不可用"
            CartItem item1 = buildCartItem(1L, USER_ID, 100L, 200L, 2, 1);
            when(cartItemMapper.selectList(any())).thenReturn(Arrays.asList(item1));

            // 模拟商品服务全部抛异常（降级场景）
            when(productFeignClient.batchGetSkuInfo(anyList())).thenThrow(new RuntimeException("服务不可用"));
            when(productFeignClient.getProductBasicInfo(anyLong())).thenThrow(new RuntimeException("服务不可用"));

            // 执行查询（不应该抛异常，降级处理）
            CartVO cartVO = cartService.getCartList(USER_ID);

            // 验证：商品名称和规格名称显示降级提示
            assertThat(cartVO.getItems()).hasSize(1);
            assertThat(cartVO.getItems().get(0).getProductName()).isEqualTo("商品信息暂不可用");
            assertThat(cartVO.getItems().get(0).getSkuName()).isEqualTo("规格信息暂不可用");
        }
    }

    // ==================== 7. clearCart 清空购物车 ====================

    @Nested
    @DisplayName("clearCart 清空购物车")
    class ClearCartTest {

        @Test
        @DisplayName("清空购物车 → 调用delete方法")
        void clearCart_verifyDelete() {
            // 场景：用户点击"清空购物车"按钮
            cartService.clearCart(USER_ID);

            // 验证：调用了delete方法（按userId删除所有购物车项）
            verify(cartItemMapper).delete(any());
        }
    }

    // ==================== 8. getCartCount 获取购物车数量 ====================

    @Nested
    @DisplayName("getCartCount 获取购物车商品数量")
    class GetCartCountTest {

        @Test
        @DisplayName("多个商品 → 返回数量之和")
        void getCartCount_sumQuantities() {
            // 场景：购物车有3个商品，数量分别是2、3、5，总数应该是10
            // 注意：返回的是数量之和，不是SKU种类数
            CartItem item1 = buildCartItem(1L, USER_ID, 100L, 200L, 2, 1);
            CartItem item2 = buildCartItem(2L, USER_ID, 101L, 201L, 3, 1);
            CartItem item3 = buildCartItem(3L, USER_ID, 102L, 202L, 5, 1);
            when(cartItemMapper.selectList(any())).thenReturn(Arrays.asList(item1, item2, item3));

            // 执行查询
            Integer count = cartService.getCartCount(USER_ID);

            // 验证：返回数量之和 = 2 + 3 + 5 = 10
            assertThat(count).isEqualTo(10);
        }
    }

    // ==================== 9. deleteBySkuIds 根据SKU ID批量删除 ====================

    @Nested
    @DisplayName("deleteBySkuIds 根据SKU ID批量删除")
    class DeleteBySkuIdsTest {

        @Test
        @DisplayName("空列表 → 不调用delete方法")
        void deleteBySkuIds_emptyList_noDelete() {
            // 场景：传入空列表，应该直接返回，不执行删除
            cartService.deleteBySkuIds(USER_ID, Collections.emptyList());

            // 验证：没有调用delete方法
            verify(cartItemMapper, never()).delete(any());
        }

        @Test
        @DisplayName("正常批量删除 → 调用delete方法")
        void deleteBySkuIds_normalDelete() {
            // 场景：订单创建成功后，删除已购买的SKU
            cartService.deleteBySkuIds(USER_ID, Arrays.asList(200L, 201L));

            // 验证：调用了delete方法
            verify(cartItemMapper).delete(any());
        }
    }

    // ==================== 10. mergeCart 合并购物车 ====================

    @Nested
    @DisplayName("mergeCart 合并购物车")
    class MergeCartTest {

        @Test
        @DisplayName("空列表 → 直接返回，不执行任何操作")
        void mergeCart_emptyList_returnImmediately() {
            // 场景：未登录时没有加购任何商品，登录后合并空列表
            cartService.mergeCart(USER_ID, Collections.emptyList());

            // 验证：没有调用任何数据库方法和商品服务
            verifyNoInteractions(cartItemMapper);
            verifyNoInteractions(productFeignClient);
        }

        @Test
        @DisplayName("正常合并多个商品 → 逐项insert")
        void mergeCart_normalMerge() {
            // 场景：未登录时加了2个商品，登录后合并到服务端购物车
            CartAddDTO item1 = buildCartAddDTO(100L, 200L, 2);
            CartAddDTO item2 = buildCartAddDTO(101L, 201L, 3);

            // 模拟商品服务正常 + 两个SKU都不在购物车里（新SKU走insert）
            mockProductFeignSuccess(100L, 200L);
            mockProductFeignSuccess(101L, 201L);
            when(cartItemMapper.selectOne(any())).thenReturn(null);
            when(cartItemMapper.selectCount(any())).thenReturn(0L);

            // 执行合并
            cartService.mergeCart(USER_ID, Arrays.asList(item1, item2));

            // 验证：调用了2次insert（每个商品新增一条记录）
            verify(cartItemMapper, times(2)).insert(any(CartItem.class));
        }

        @Test
        @DisplayName("单项失败 → 跳过该项继续合并其他")
        void mergeCart_singleItemFail_skipAndContinue() {
            // 场景：合并2个商品，第一个数量超限（失败），第二个正常（成功）
            // mergeCart会捕获第一个的异常，继续合并第二个，不会因为一个失败就全部失败
            CartAddDTO failItem = buildCartAddDTO(100L, 200L, 100);  // 数量100超限，会失败
            CartAddDTO normalItem = buildCartAddDTO(101L, 201L, 1);  // 正常

            // 第二个商品的服务正常（第一个在数量校验阶段就失败了，不会调商品服务）
            mockProductFeignSuccess(101L, 201L);
            when(cartItemMapper.selectOne(any())).thenReturn(null);
            when(cartItemMapper.selectCount(any())).thenReturn(0L);

            // 执行合并（不应该抛异常，第一个失败被捕获跳过）
            cartService.mergeCart(USER_ID, Arrays.asList(failItem, normalItem));

            // 验证：只调用了1次insert（第一个失败跳过，第二个成功）
            verify(cartItemMapper, times(1)).insert(any(CartItem.class));
        }
    }
}
