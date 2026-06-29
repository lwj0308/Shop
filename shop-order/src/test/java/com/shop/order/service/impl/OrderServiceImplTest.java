package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.OrderAddress;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.entity.OrderLog;
import com.shop.model.order.entity.OrderLogistics;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.user.vo.AddressVO;
import com.shop.order.feign.CartFeignClient;
import com.shop.order.feign.CouponFeignClient;
import com.shop.order.feign.MerchantFeignClient;
import com.shop.order.feign.NotificationFeignClient;
import com.shop.order.feign.ProductFeignClient;
import com.shop.order.feign.PromotionFeignClient;
import com.shop.order.feign.UserFeignClient;
import com.shop.order.mapper.OrderAddressMapper;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderLogisticsMapper;
import com.shop.order.mapper.OrderLogMapper;
import com.shop.order.util.OrderNoGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单服务实现类（OrderServiceImpl）的单元测试
 * <p>
 * 这个测试类验证订单的创建、取消、查询、确认收货、自动取消、支付成功等核心业务逻辑。
 * 简单理解：我们把数据库 Mapper、远程服务 Feign 客户端、消息队列、Redis 都"假装"一下（Mock），
 * 这样测试就不需要真的连数据库、网络和中间件，跑得又快又稳定。
 * </p>
 * <p>
 * 测试覆盖的7个方法：createOrder、cancelOrder、getOrderDetail、getOrderList、
 * confirmReceive、autoCancelOrder、paySuccess
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * </p>
 */
@DisplayName("订单服务 OrderServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    // ==================== 依赖的 Mock 对象（15个） ====================

    /** 假装订单主表 Mapper */
    @Mock
    private OrderInfoMapper orderInfoMapper;

    /** 假装订单明细 Mapper */
    @Mock
    private OrderItemMapper orderItemMapper;

    /** 假装订单地址快照 Mapper */
    @Mock
    private OrderAddressMapper orderAddressMapper;

    /** 假装物流信息 Mapper */
    @Mock
    private OrderLogisticsMapper orderLogisticsMapper;

    /** 假装订单状态日志 Mapper */
    @Mock
    private OrderLogMapper orderLogMapper;

    /** 假装商品服务 Feign 客户端 */
    @Mock
    private ProductFeignClient productFeignClient;

    /** 假装用户服务 Feign 客户端 */
    @Mock
    private UserFeignClient userFeignClient;

    /** 假装购物车服务 Feign 客户端 */
    @Mock
    private CartFeignClient cartFeignClient;

    /** 假装商家服务 Feign 客户端 */
    @Mock
    private MerchantFeignClient merchantFeignClient;

    /** 假装通知服务 Feign 客户端 */
    @Mock
    private NotificationFeignClient notificationFeignClient;

    /** 假装优惠券服务 Feign 客户端 */
    @Mock
    private CouponFeignClient couponFeignClient;

    /** 假装满减活动 Feign 客户端 */
    @Mock
    private PromotionFeignClient promotionFeignClient;

    /** 假装订单号生成器 */
    @Mock
    private OrderNoGenerator orderNoGenerator;

    /** 假装 RocketMQ 消息模板 */
    @Mock
    private RocketMQTemplate rocketMQTemplate;

    /** 假装 Redis 模板（用于分布式锁和幂等校验） */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 被测试的订单服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private OrderServiceImpl orderService;

    // ==================== 测试常量 ====================

    private static final Long USER_ID = 1001L;          // 当前用户ID
    private static final Long OTHER_USER_ID = 1002L;    // 另一个用户ID（测试越权用）
    private static final Long ORDER_ID = 5001L;         // 订单ID
    private static final String ORDER_NO = "1829384756102345678"; // 订单号
    private static final Long SKU_ID = 3001L;           // SKU规格ID
    private static final Long PRODUCT_ID = 2001L;       // 商品ID
    private static final Long ADDRESS_ID = 4001L;       // 收货地址ID

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：OrderServiceImpl 里用到了 LambdaQueryWrapper 和 LambdaUpdateWrapper，
     * 比如 .eq(OrderInfo::getId, orderId)。这些代码会让 MyBatis-Plus 去查
     * "OrderInfo 的 id 字段对应数据库哪一列"。正常启动 Spring 时框架会自动做这件事，
     * 但单元测试没有 Spring 环境，所以需要我们手动初始化。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        // 把所有用到的实体类都初始化一遍
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        TableInfoHelper.initTableInfo(assistant, OrderAddress.class);
        TableInfoHelper.initTableInfo(assistant, OrderLogistics.class);
        TableInfoHelper.initTableInfo(assistant, OrderLog.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个创建订单的请求参数
     * 小白理解：用户点"提交订单"时前端传过来的参数
     *
     * @return 构造好的 OrderCreateDTO
     */
    private OrderCreateDTO buildCreateDTO() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setAddressId(ADDRESS_ID);
        dto.setRemark("放门口");
        // 买1件商品，数量2
        OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
        item.setSkuId(SKU_ID);
        item.setQuantity(2);
        dto.setItems(Collections.singletonList(item));
        return dto;
    }

    /**
     * 构造一个取消订单的请求参数
     */
    private OrderCancelDTO buildCancelDTO() {
        OrderCancelDTO dto = new OrderCancelDTO();
        dto.setReason("不想买了");
        return dto;
    }

    /**
     * 构造一个SKU信息（库存充足、已上架）
     */
    private ProductSkuVO buildSku() {
        ProductSkuVO sku = new ProductSkuVO();
        sku.setId(SKU_ID);
        sku.setProductId(PRODUCT_ID);
        sku.setPrice(new BigDecimal("10.00"));
        sku.setStock(100);
        sku.setStatus(1);
        sku.setImage("img-url");
        sku.setMerchantId(0L);
        return sku;
    }

    /**
     * 构造一个收货地址
     */
    private AddressVO buildAddress() {
        AddressVO address = new AddressVO();
        address.setId(ADDRESS_ID);
        address.setName("张三");
        address.setPhone("13800138000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路1号");
        return address;
    }

    /**
     * 构造一个订单实体
     *
     * @param id     订单ID
     * @param userId 用户ID
     * @param status 订单状态
     * @return 构造好的 OrderInfo
     */
    private OrderInfo buildOrder(Long id, Long userId, int status) {
        OrderInfo order = new OrderInfo();
        order.setId(id);
        order.setOrderNo(ORDER_NO);
        order.setUserId(userId);
        order.setMerchantId(0L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setPayAmount(new BigDecimal("20.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPromotionDiscount(BigDecimal.ZERO);
        return order;
    }

    /**
     * 构造一个订单明细
     */
    private OrderItem buildOrderItem() {
        OrderItem item = new OrderItem();
        item.setId(6001L);
        item.setOrderId(ORDER_ID);
        item.setOrderNo(ORDER_NO);
        item.setProductId(PRODUCT_ID);
        item.setSkuId(SKU_ID);
        item.setProductName("测试商品");
        item.setSkuSpec("黑色 128G");
        item.setProductImage("img-url");
        item.setPrice(new BigDecimal("10.00"));
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("20.00"));
        return item;
    }

    /**
     * 构造一个订单地址快照
     */
    private OrderAddress buildOrderAddress() {
        OrderAddress address = new OrderAddress();
        address.setId(7001L);
        address.setOrderId(ORDER_ID);
        address.setOrderNo(ORDER_NO);
        address.setName("张三");
        address.setPhone("13800138000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路1号");
        return address;
    }

    // ==================== 1. createOrder 创建订单 ====================

    @Nested
    @DisplayName("createOrder 创建订单")
    class CreateOrderTest {

        @Test
        @DisplayName("正常创建订单 → 返回订单详情，验证insert和扣减库存")
        void createOrder_normal_success() {
            // 场景：用户正常下单，买1件商品数量2，库存充足，地址有效
            OrderCreateDTO dto = buildCreateDTO();

            // mock Redis 分布式锁：返回 true 表示加锁成功
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

            // mock 商品服务：返回SKU信息（库存100，已上架）
            ProductSkuVO sku = buildSku();
            when(productFeignClient.batchGetSkuByIds(anyList())).thenReturn(Result.success(Collections.singletonList(sku)));

            // mock 用户服务：返回收货地址
            when(userFeignClient.getAddressById(ADDRESS_ID)).thenReturn(Result.success(buildAddress()));

            // mock 订单号生成器
            when(orderNoGenerator.generate()).thenReturn(ORDER_NO);

            // mock 满减计算：返回0（无满减优惠）
            // 小白理解：不 stub 也可以，返回 null 时 calculatePromotion 会降级返回0

            // mock insert 订单主表：模拟 MyBatis-Plus 自动回填 id
            when(orderInfoMapper.insert(any(OrderInfo.class))).thenAnswer(invocation -> {
                OrderInfo o = invocation.getArgument(0);
                o.setId(ORDER_ID);
                return 1;
            });
            // mock insert 订单明细、地址快照、状态日志
            when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);
            when(orderAddressMapper.insert(any(OrderAddress.class))).thenReturn(1);
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);

            // mock 批量扣减库存：返回成功
            when(productFeignClient.batchDeductStock(anyList(), anyString())).thenReturn(Result.success());

            // mock getOrderDetail 的依赖（createOrder 最后会调用 getOrderDetail 返回详情）
            OrderInfo savedOrder = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(savedOrder);
            when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(buildOrderItem()));
            when(orderAddressMapper.selectOne(any())).thenReturn(buildOrderAddress());
            when(orderLogisticsMapper.selectOne(any())).thenReturn(null);

            // 执行创建订单
            OrderDetailVO vo = orderService.createOrder(USER_ID, dto);

            // 验证返回的订单详情
            assertThat(vo).isNotNull();
            assertThat(vo.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(vo.getStatus()).isEqualTo(OrderStatusEnum.UNPAID.getCode());
            assertThat(vo.getItems()).hasSize(1);

            // 验证关键调用都发生了
            verify(orderInfoMapper).insert(any(OrderInfo.class));        // 插入订单主表
            verify(orderItemMapper).insert(any(OrderItem.class));        // 插入订单明细
            verify(orderAddressMapper).insert(any(OrderAddress.class));  // 插入地址快照
            verify(orderLogMapper).insert(any(OrderLog.class));          // 记录状态日志
            verify(productFeignClient).batchDeductStock(anyList(), eq(ORDER_NO)); // 扣减库存
            verify(cartFeignClient).deleteBySkuIds(eq(USER_ID), anyList());       // 删除购物车
            verify(rocketMQTemplate).syncSend(anyString(), any(), anyLong(), anyInt()); // 发送延时消息
        }

        @Test
        @DisplayName("分布式锁获取失败 → 抛出ORDER_CREATE_FAIL异常")
        void createOrder_lockFail_throwsException() {
            // 场景：同一用户短时间内重复下单，Redis 分布式锁获取失败
            OrderCreateDTO dto = buildCreateDTO();

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            // setIfAbsent 返回 false 表示锁已被占用
            when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(false);

            // 验证：抛出 BusinessException，错误码是 ORDER_CREATE_FAIL
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_CREATE_FAIL.getCode());

            // 验证：加锁失败后不应该继续执行下单逻辑
            verify(productFeignClient, never()).batchGetSkuByIds(anyList());
        }

        @Test
        @DisplayName("幂等Token校验失败（重复提交） → 抛出ORDER_CREATE_FAIL异常")
        void createOrder_idempotentTokenFail_throwsException() {
            // 场景：用户网络卡顿重复点击"提交订单"，第二次提交时幂等Token已被消费
            OrderCreateDTO dto = buildCreateDTO();
            dto.setIdempotentToken("duplicate-token");

            // mock Redis delete 返回 false（Token 已被删除，说明是重复请求）
            when(stringRedisTemplate.delete("order:idempotent:duplicate-token")).thenReturn(false);

            // 验证：抛出 BusinessException，错误码是 ORDER_CREATE_FAIL
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_CREATE_FAIL.getCode());

            // 验证：幂等校验失败后不应该继续执行下单逻辑
            verify(stringRedisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("商品信息获取失败 → 抛出ORDER_CREATE_FAIL异常")
        void createOrder_skuInfoFail_throwsException() {
            // 场景：商品服务不可用或返回失败，无法获取SKU信息
            OrderCreateDTO dto = buildCreateDTO();

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

            // mock 商品服务返回失败
            when(productFeignClient.batchGetSkuByIds(anyList())).thenReturn(Result.fail(500, "商品服务不可用"));

            // 验证：抛出 BusinessException，错误码是 ORDER_CREATE_FAIL
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_CREATE_FAIL.getCode());

            // 验证：商品信息获取失败后不应该插入订单
            verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
        }

        @Test
        @DisplayName("库存不足 → 抛出PRODUCT_STOCK_NOT_ENOUGH异常")
        void createOrder_stockNotEnough_throwsException() {
            // 场景：商品库存只有5件，但用户要买10件
            // 小白理解：代码执行顺序是"先获取地址→再检查库存"，所以地址也要 mock 成功
            OrderCreateDTO dto = buildCreateDTO();
            dto.getItems().get(0).setQuantity(10); // 买10件

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

            // mock SKU 库存只有5件
            ProductSkuVO sku = buildSku();
            sku.setStock(5);
            when(productFeignClient.batchGetSkuByIds(anyList())).thenReturn(Result.success(Collections.singletonList(sku)));

            // mock 收货地址返回成功（代码先获取地址再检查库存，必须 mock 地址才能走到库存检查）
            when(userFeignClient.getAddressById(ADDRESS_ID)).thenReturn(Result.success(buildAddress()));

            // 验证：抛出 BusinessException，错误码是 PRODUCT_STOCK_NOT_ENOUGH
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode());

            // 验证：库存不足不应该插入订单
            verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
        }
    }

    // ==================== 2. cancelOrder 取消订单 ====================

    @Nested
    @DisplayName("cancelOrder 取消订单")
    class CancelOrderTest {

        @Test
        @DisplayName("订单不存在 → 抛出ORDER_NOT_FOUND异常")
        void cancelOrder_notFound_throwsException() {
            // 场景：取消一个不存在的订单
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);

            // 验证：抛出 ORDER_NOT_FOUND 异常
            assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, ORDER_ID, buildCancelDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_NOT_FOUND.getCode());

            // 验证：没有调用 update
            verify(orderInfoMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("非本人订单 → 抛出FORBIDDEN异常")
        void cancelOrder_notOwner_throwsForbidden() {
            // 场景：订单存在，但属于另一个用户，当前用户无权取消
            OrderInfo order = buildOrder(ORDER_ID, OTHER_USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            // 验证：抛出 FORBIDDEN 异常（code=403）
            assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, ORDER_ID, buildCancelDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            // 验证：没有调用 update
            verify(orderInfoMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("订单已支付不能取消 → 抛出IllegalArgumentException（状态机校验）")
        void cancelOrder_alreadyPaid_throwsStateException() {
            // 场景：订单已支付（PAID状态），不能再取消
            // 小白理解：状态机规定只有"待付款"才能取消，"待发货"不能取消
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.PAID.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            // 验证：状态机校验抛出 IllegalArgumentException（不是 BusinessException）
            assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, ORDER_ID, buildCancelDTO()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("取消订单");

            // 验证：状态不允许时没有调用 update
            verify(orderInfoMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("正常取消订单 → 更新状态、回滚库存、记录日志")
        void cancelOrder_normalSuccess() {
            // 场景：待付款订单正常取消
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            // mock 乐观锁更新：返回1表示更新成功
            when(orderInfoMapper.update(any(), any())).thenReturn(1);

            // mock 回滚库存：查询订单明细
            when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(buildOrderItem()));
            when(productFeignClient.addStock(anyLong(), anyInt())).thenReturn(Result.success());

            // mock 日志插入
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);

            // 执行取消订单
            orderService.cancelOrder(USER_ID, ORDER_ID, buildCancelDTO());

            // 验证：更新了订单状态（待付款 → 已取消）
            verify(orderInfoMapper).update(any(), any());
            // 验证：回滚了库存
            verify(productFeignClient).addStock(eq(SKU_ID), eq(2));
            // 验证：记录了状态变更日志
            verify(orderLogMapper).insert(any(OrderLog.class));
        }
    }

    // ==================== 3. getOrderDetail 获取订单详情 ====================

    @Nested
    @DisplayName("getOrderDetail 获取订单详情")
    class GetOrderDetailTest {

        @Test
        @DisplayName("订单不存在 → 抛出ORDER_NOT_FOUND异常")
        void getOrderDetail_notFound_throwsException() {
            // 场景：查询一个不存在的订单详情
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> orderService.getOrderDetail(USER_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("非本人订单 → 抛出FORBIDDEN异常")
        void getOrderDetail_notOwner_throwsForbidden() {
            // 场景：查看别人的订单详情（越权访问）
            OrderInfo order = buildOrder(ORDER_ID, OTHER_USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            assertThatThrownBy(() -> orderService.getOrderDetail(USER_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());
        }

        @Test
        @DisplayName("正常返回订单详情（含明细、地址、物流）")
        void getOrderDetail_normal_returnsDetail() {
            // 场景：正常查询自己的订单详情
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);
            when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(buildOrderItem()));
            when(orderAddressMapper.selectOne(any())).thenReturn(buildOrderAddress());
            // 物流信息为null（未发货）
            when(orderLogisticsMapper.selectOne(any())).thenReturn(null);

            // 执行查询
            OrderDetailVO vo = orderService.getOrderDetail(USER_ID, ORDER_ID);

            // 验证返回的详情
            assertThat(vo).isNotNull();
            assertThat(vo.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(vo.getStatus()).isEqualTo(OrderStatusEnum.UNPAID.getCode());
            assertThat(vo.getStatusDesc()).isEqualTo("待付款");
            assertThat(vo.getItems()).hasSize(1);
            assertThat(vo.getItems().get(0).getSkuId()).isEqualTo(SKU_ID);
            // 验证地址快照
            assertThat(vo.getAddress()).isNotNull();
            assertThat(vo.getAddress().getName()).isEqualTo("张三");
            // 验证物流为null（未发货）
            assertThat(vo.getLogistics()).isNull();
        }
    }

    // ==================== 4. getOrderList 获取订单列表 ====================

    @Nested
    @DisplayName("getOrderList 获取订单列表")
    class GetOrderListTest {

        @Test
        @DisplayName("分页查询订单列表 → 返回分页结果")
        void getOrderList_normal_returnsPageResult() {
            // 场景：查询用户第1页订单，每页10条
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            // mock 分页查询：返回1条订单
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.UNPAID.getCode());
            Page<OrderInfo> page = new Page<>(1, 10);
            page.setRecords(Collections.singletonList(order));
            page.setTotal(1);
            when(orderInfoMapper.selectPage(any(), any())).thenReturn(page);

            // mock 订单明细查询（convertToVO 里会查明细）
            when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(buildOrderItem()));

            // 执行查询
            PageResult<OrderVO> result = orderService.getOrderList(USER_ID, null, pageRequest);

            // 验证分页结果
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            // 验证订单VO字段
            OrderVO vo = result.getRecords().get(0);
            assertThat(vo.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(vo.getStatus()).isEqualTo(OrderStatusEnum.UNPAID.getCode());
            assertThat(vo.getStatusDesc()).isEqualTo("待付款");
            // 验证商品总数量（2件）
            assertThat(vo.getTotalQuantity()).isEqualTo(2);
        }
    }

    // ==================== 5. confirmReceive 确认收货 ====================

    @Nested
    @DisplayName("confirmReceive 确认收货")
    class ConfirmReceiveTest {

        @Test
        @DisplayName("订单不存在 → 抛出ORDER_NOT_FOUND异常")
        void confirmReceive_notFound_throwsException() {
            // 场景：确认收货一个不存在的订单
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> orderService.confirmReceive(USER_ID, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_NOT_FOUND.getCode());

            verify(orderInfoMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("订单未发货不能确认收货 → 抛出IllegalArgumentException（状态机校验）")
        void confirmReceive_notShipped_throwsStateException() {
            // 场景：订单还是待付款状态，不能确认收货
            // 小白理解：状态机规定只有"运输中"才能确认收货
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            assertThatThrownBy(() -> orderService.confirmReceive(USER_ID, ORDER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("确认收货");

            verify(orderInfoMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("正常确认收货 → 状态从运输中变为已收货")
        void confirmReceive_normalSuccess() {
            // 场景：运输中的订单正常确认收货
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.SHIPPING.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            // mock 乐观锁更新：返回1表示更新成功
            when(orderInfoMapper.update(any(), any())).thenReturn(1);
            // mock 日志插入
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);

            // 执行确认收货
            orderService.confirmReceive(USER_ID, ORDER_ID);

            // 验证：更新了订单状态（运输中 → 已收货）
            verify(orderInfoMapper).update(any(), any());
            // 验证：记录了状态变更日志
            verify(orderLogMapper).insert(any(OrderLog.class));
        }
    }

    // ==================== 6. autoCancelOrder 自动取消超时订单 ====================

    @Nested
    @DisplayName("autoCancelOrder 自动取消超时订单")
    class AutoCancelOrderTest {

        @Test
        @DisplayName("订单不存在 → 直接返回，不抛异常")
        void autoCancelOrder_notFound_returnSilently() {
            // 场景：MQ消费时查不到订单（可能已被处理），应该直接返回
            when(orderInfoMapper.selectOne(any())).thenReturn(null);

            // 执行自动取消（不应该抛异常）
            orderService.autoCancelOrder(ORDER_NO);

            // 验证：没有调用 update
            verify(orderInfoMapper, never()).update(any(), any());
            // 验证：没有记录日志
            verify(orderLogMapper, never()).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("订单已支付 → 不取消，直接返回")
        void autoCancelOrder_alreadyPaid_notCancel() {
            // 场景：订单已经支付了，不需要自动取消
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.PAID.getCode());
            when(orderInfoMapper.selectOne(any())).thenReturn(order);

            // 执行自动取消（不应该抛异常，也不应该取消）
            orderService.autoCancelOrder(ORDER_NO);

            // 验证：没有调用 update（因为状态不是待付款）
            verify(orderInfoMapper, never()).update(any(), any());
            // 验证：没有回滚库存
            verify(productFeignClient, never()).addStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("正常超时取消 → 回滚库存、更新状态、记录日志")
        void autoCancelOrder_normalSuccess() {
            // 场景：待付款订单超时未支付，系统自动取消
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectOne(any())).thenReturn(order);

            // mock 乐观锁更新：返回1表示更新成功
            when(orderInfoMapper.update(any(), any())).thenReturn(1);
            // mock 回滚库存
            when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(buildOrderItem()));
            when(productFeignClient.addStock(anyLong(), anyInt())).thenReturn(Result.success());
            // mock 日志插入
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);

            // 执行自动取消
            orderService.autoCancelOrder(ORDER_NO);

            // 验证：更新了订单状态（待付款 → 已取消）
            verify(orderInfoMapper).update(any(), any());
            // 验证：回滚了库存
            verify(productFeignClient).addStock(eq(SKU_ID), eq(2));
            // 验证：记录了状态变更日志
            verify(orderLogMapper).insert(any(OrderLog.class));
        }
    }

    // ==================== 7. paySuccess 支付成功回调 ====================

    @Nested
    @DisplayName("paySuccess 支付成功回调")
    class PaySuccessTest {

        @Test
        @DisplayName("订单不存在 → 抛出ORDER_NOT_FOUND异常")
        void paySuccess_notFound_throwsException() {
            // 场景：支付回调时查不到订单
            when(orderInfoMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> orderService.paySuccess(ORDER_NO))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_NOT_FOUND.getCode());

            verify(orderInfoMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("正常支付成功 → 更新状态为待发货、记录日志")
        void paySuccess_normalSuccess() {
            // 场景：待付款订单支付成功，状态变为待发货
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.UNPAID.getCode());
            when(orderInfoMapper.selectOne(any())).thenReturn(order);

            // mock 乐观锁更新：返回1表示更新成功
            when(orderInfoMapper.update(any(), any())).thenReturn(1);
            // mock 日志插入
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);

            // 执行支付成功回调
            orderService.paySuccess(ORDER_NO);

            // 验证：更新了订单状态（待付款 → 待发货）
            verify(orderInfoMapper).update(any(), any());
            // 验证：记录了状态变更日志
            verify(orderLogMapper).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("订单已支付（重复回调） → 幂等返回，不重复更新")
        void paySuccess_alreadyPaid_idempotentReturn() {
            // 场景：支付平台重复回调，订单已经是待发货状态
            // 小白理解：支付回调可能重复发送，需要幂等处理防止重复更新
            OrderInfo order = buildOrder(ORDER_ID, USER_ID, OrderStatusEnum.PAID.getCode());
            when(orderInfoMapper.selectOne(any())).thenReturn(order);

            // 执行支付成功回调（不应该抛异常，直接返回）
            orderService.paySuccess(ORDER_NO);

            // 验证：没有调用 update（已经是待发货状态，不需要更新）
            verify(orderInfoMapper, never()).update(any(), any());
            // 验证：没有记录日志（没有状态变化）
            verify(orderLogMapper, never()).insert(any(OrderLog.class));
        }
    }
}
