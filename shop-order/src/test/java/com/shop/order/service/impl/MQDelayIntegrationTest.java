package com.shop.order.service.impl;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.shop.common.result.Result;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.user.vo.AddressVO;
import com.shop.order.consumer.OrderTimeoutConsumer;
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
import com.shop.order.service.OrderService;
import com.shop.order.util.OrderNoGenerator;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-I-07 MQ 延时消息集成测试
 * <p>
 * 小白讲解：
 * RocketMQ 延时消息是 Shop 项目的核心异步机制：
 * 1. 用户下单后，发送一条 30 分钟延时消息到 topic_order_timeout
 * 2. 30 分钟后，OrderTimeoutConsumer 收到消息，检查订单是否已支付
 * 3. 未支付则自动取消订单，回滚库存
 *
 * 这个测试验证：
 * 1. 下单成功时延时消息被正确发送（topic/timeout/delayLevel 参数正确）
 * 2. MQ 发送失败不影响下单主流程（弱依赖）
 * 3. 消费者幂等：同一条消息重复消费时只执行一次
 * 4. 消费失败时删除幂等标记，让 MQ 可以重试
 * 5. 消费者与 OrderService 集成：真实调用 autoCancelOrder
 *
 * 测试架构：
 * - H2 内存数据库（数据库名 mqdelay，与其他测试隔离）
 * - 本地 Redis DB 9（与 B-I-04 用 10、B-I-05 用 11、B-I-01 用 12 隔离）
 * - 真实 Redis 验证消费者幂等逻辑（SETNX + TTL）
 * - Mock RocketMQTemplate（不依赖真实 MQ 服务器）
 * </p>
 */
@DisplayName("B-I-07 MQ 延时消息集成测试 - 延时发送与消费者幂等")
class MQDelayIntegrationTest {

    // ==================== 测试常量 ====================

    private static final int TEST_DB_INDEX = 9;
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    private static final Long USER_ID = 2001L;
    private static final Long SKU_ID = 2101L;
    private static final Long PRODUCT_ID = 2201L;
    private static final Long ADDRESS_ID = 2301L;
    private static final String ORDER_NO = "9999888877776666555";
    private static final String LOCK_KEY = "order:create:" + USER_ID;
    private static final String TOPIC_ORDER_TIMEOUT = "topic_order_timeout";
    private static final String CONSUMED_KEY = "order:timeout:consumed:" + ORDER_NO;

    // ==================== 共享资源 ====================

    private static EmbeddedDatabase dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private static LettuceConnectionFactory sharedFactory;
    private static boolean redisAvailable = false;

    // ==================== 测试实例字段 ====================

    private StringRedisTemplate redisTemplate;
    private SqlSession sqlSession;
    private JdbcTemplate jdbcTemplate;

    private OrderInfoMapper orderInfoMapper;
    private OrderItemMapper orderItemMapper;
    private OrderAddressMapper orderAddressMapper;
    private OrderLogMapper orderLogMapper;
    private OrderLogisticsMapper orderLogisticsMapper;

    // 下单相关 Mock
    private ProductFeignClient productFeignClient;
    private UserFeignClient userFeignClient;
    private CartFeignClient cartFeignClient;
    private MerchantFeignClient merchantFeignClient;
    private NotificationFeignClient notificationFeignClient;
    private CouponFeignClient couponFeignClient;
    private PromotionFeignClient promotionFeignClient;
    private OrderNoGenerator orderNoGenerator;
    private RocketMQTemplate rocketMQTemplate;

    private OrderServiceImpl orderService;

    @BeforeAll
    static void initAll() {
        initH2Database();
        initRedis();
    }

    private static void initH2Database() {
        try {
            dataSource = (EmbeddedDatabase) new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("mqdelay;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
                    .addScript("classpath:schema-h2.sql")
                    .build();

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig()
                    .setMetaObjectHandler(new com.baomidou.mybatisplus.core.handlers.MetaObjectHandler() {
                        @Override
                        public void insertFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                            this.strictInsertFill(metaObject, "createTime",
                                    java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                            this.strictInsertFill(metaObject, "updateTime",
                                    java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                        }

                        @Override
                        public void updateFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                            this.strictUpdateFill(metaObject, "updateTime",
                                    java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                        }
                    }));
            sqlSessionFactory = factoryBean.getObject();

            sqlSessionFactory.getConfiguration().addMapper(OrderInfoMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderItemMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderAddressMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderLogMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderLogisticsMapper.class);
        } catch (Exception e) {
            System.err.println("[B-I-07] H2 初始化失败：" + e.getMessage());
        }
    }

    private static void initRedis() {
        try {
            sharedFactory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
            sharedFactory.setDatabase(TEST_DB_INDEX);
            sharedFactory.afterPropertiesSet();
            sharedFactory.start();
            String pong = sharedFactory.getConnection().ping();
            redisAvailable = "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            redisAvailable = false;
            System.err.println("[B-I-07] Redis 不可用，测试将被跳过：" + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(sqlSessionFactory != null,
                "H2 数据库初始化失败，跳过 B-I-07 集成测试");
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis 不可用，跳过 B-I-07 集成测试");

        sqlSession = sqlSessionFactory.openSession(true);
        orderInfoMapper = sqlSession.getMapper(OrderInfoMapper.class);
        orderItemMapper = sqlSession.getMapper(OrderItemMapper.class);
        orderAddressMapper = sqlSession.getMapper(OrderAddressMapper.class);
        orderLogMapper = sqlSession.getMapper(OrderLogMapper.class);
        orderLogisticsMapper = sqlSession.getMapper(OrderLogisticsMapper.class);
        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("DELETE FROM order_log");
        jdbcTemplate.execute("DELETE FROM order_address");
        jdbcTemplate.execute("DELETE FROM order_item");
        jdbcTemplate.execute("DELETE FROM order_info");

        redisTemplate = new StringRedisTemplate(sharedFactory);
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        productFeignClient = mock(ProductFeignClient.class);
        userFeignClient = mock(UserFeignClient.class);
        cartFeignClient = mock(CartFeignClient.class);
        merchantFeignClient = mock(MerchantFeignClient.class);
        notificationFeignClient = mock(NotificationFeignClient.class);
        couponFeignClient = mock(CouponFeignClient.class);
        promotionFeignClient = mock(PromotionFeignClient.class);
        orderNoGenerator = mock(OrderNoGenerator.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);

        orderService = new OrderServiceImpl(
                orderInfoMapper,
                orderItemMapper,
                orderAddressMapper,
                orderLogisticsMapper,
                orderLogMapper,
                productFeignClient,
                userFeignClient,
                cartFeignClient,
                merchantFeignClient,
                notificationFeignClient,
                couponFeignClient,
                promotionFeignClient,
                orderNoGenerator,
                rocketMQTemplate,
                redisTemplate);
    }

    // ==================== 辅助方法 ====================

    private OrderCreateDTO buildCreateDTO(int quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setAddressId(ADDRESS_ID);
        dto.setRemark("放门口");
        OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
        item.setSkuId(SKU_ID);
        item.setQuantity(quantity);
        dto.setItems(Collections.singletonList(item));
        return dto;
    }

    private ProductSkuVO buildSku(int stock, BigDecimal price) {
        ProductSkuVO sku = new ProductSkuVO();
        sku.setId(SKU_ID);
        sku.setProductId(PRODUCT_ID);
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setStatus(1);
        sku.setImage("img-url");
        sku.setMerchantId(1L);
        return sku;
    }

    private AddressVO buildAddress() {
        AddressVO address = new AddressVO();
        address.setId(ADDRESS_ID);
        address.setName("赵六");
        address.setPhone("13600136000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路4号");
        return address;
    }

    /**
     * 设置下单成功所需的 Mock
     */
    private void mockCreateOrderSuccess(int stock, BigDecimal price) {
        when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
        when(productFeignClient.batchGetSkuByIds(anyList()))
                .thenReturn(Result.success(Collections.singletonList(buildSku(stock, price))));
        when(userFeignClient.getAddressById(ADDRESS_ID))
                .thenReturn(Result.success(buildAddress()));
        when(promotionFeignClient.calculatePromotion(any()))
                .thenReturn(Result.success(BigDecimal.ZERO));
        when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                .thenReturn(Result.success(null));
    }

    // ==================== 1. 延时消息发送测试 ====================

    @Nested
    @DisplayName("1. 下单时延时消息发送")
    class SendTimeoutMessageTest {

        @Test
        @DisplayName("下单成功：syncSend 被调用，topic/timeout/delayLevel 参数正确")
        void createOrder_success_syncSendCalledWithCorrectParams() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(2));

            // 验证：syncSend 被调用，参数正确
            // 小白讲解：timeout 是 long 类型，eq(3000L) 不能写成 eq(3000)（Integer）
            // delayLevel=16 对应 RocketMQ 延时等级 16 = 30分钟
            verify(rocketMQTemplate).syncSend(
                    eq(TOPIC_ORDER_TIMEOUT),
                    any(Message.class),
                    eq(3000L),
                    eq(16));
        }

        @Test
        @DisplayName("MQ 发送抛异常：下单仍成功（弱依赖，try-catch 吞掉异常）")
        void createOrder_mqSendThrowsException_orderStillCreated() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            // MQ 发送抛异常
            when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L), eq(16)))
                    .thenThrow(new RuntimeException("MQ 服务不可用"));

            // 下单不应抛异常（MQ 是弱依赖）
            OrderDetailVO result = orderService.createOrder(USER_ID, buildCreateDTO(2));

            // 验证：订单创建成功
            assertThat(result).isNotNull();
            assertThat(result.getOrderNo()).isEqualTo(ORDER_NO);
            // 验证：订单写入数据库
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class)).isEqualTo(1);
            // 验证：锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("MQ 发送成功：订单创建成功，锁释放")
        void createOrder_mqSendSuccess_orderCreated() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            // MQ 发送成功（默认返回 SendStatus.SEND_OK，Mock 不需要额外设置）

            OrderDetailVO result = orderService.createOrder(USER_ID, buildCreateDTO(1));

            assertThat(result).isNotNull();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class)).isEqualTo(1);
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
            // 验证：syncSend 被调用1次
            verify(rocketMQTemplate, times(1)).syncSend(
                    eq(TOPIC_ORDER_TIMEOUT), any(Message.class), eq(3000L), eq(16));
        }

        @Test
        @DisplayName("MQ 消息 payload 是订单号")
        void createOrder_mqPayloadIsOrderNo() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(1));

            // 捕获 syncSend 的 Message 参数，验证 payload 是订单号
            org.mockito.ArgumentCaptor<Message> messageCaptor =
                    org.mockito.ArgumentCaptor.forClass(Message.class);
            verify(rocketMQTemplate).syncSend(
                    eq(TOPIC_ORDER_TIMEOUT), messageCaptor.capture(), eq(3000L), eq(16));

            Message<?> capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getPayload()).isEqualTo(ORDER_NO);
        }
    }

    // ==================== 2. 消费者幂等测试 ====================

    @Nested
    @DisplayName("2. OrderTimeoutConsumer 幂等消费")
    class ConsumerIdempotentTest {

        private OrderService mockOrderService;

        @BeforeEach
        void setUpConsumer() {
            // 消费者测试用 Mock OrderService，专注验证消费者幂等逻辑
            mockOrderService = mock(OrderService.class);
        }

        /**
         * 创建消费者实例（注入 Mock OrderService 和真实 Redis）
         */
        private OrderTimeoutConsumer createConsumer() {
            return new OrderTimeoutConsumer(mockOrderService, redisTemplate);
        }

        @Test
        @DisplayName("首次消费：autoCancelOrder 被调用，Redis 幂等标记写入")
        void onMessage_firstConsume_autoCancelCalled() {
            OrderTimeoutConsumer consumer = createConsumer();

            consumer.onMessage(ORDER_NO);

            // 验证：autoCancelOrder 被调用1次
            verify(mockOrderService).autoCancelOrder(ORDER_NO);
            // 验证：Redis 幂等标记已写入
            assertThat(redisTemplate.hasKey(CONSUMED_KEY)).isTrue();
        }

        @Test
        @DisplayName("重复消费：幂等校验拦截，autoCancelOrder 未被调用")
        void onMessage_duplicateConsume_autoCancelNotCalled() {
            OrderTimeoutConsumer consumer = createConsumer();

            // 第一次消费
            consumer.onMessage(ORDER_NO);
            verify(mockOrderService, times(1)).autoCancelOrder(ORDER_NO);

            // 第二次消费（重复消息）
            consumer.onMessage(ORDER_NO);

            // 验证：autoCancelOrder 仍然只被调用1次（幂等拦截）
            verify(mockOrderService, times(1)).autoCancelOrder(ORDER_NO);
        }

        @Test
        @DisplayName("消费失败：删除幂等标记，异常向上传播让 MQ 重试")
        void onMessage_consumeFail_deleteConsumedKeyAndThrow() {
            OrderTimeoutConsumer consumer = createConsumer();
            // autoCancelOrder 抛异常（模拟取消失败）
            // 小白讲解：autoCancelOrder 返回 void，不能用 when().thenThrow()，
            // 必须用 doThrow().when() 语法
            org.mockito.Mockito.doThrow(new RuntimeException("订单服务异常"))
                    .when(mockOrderService).autoCancelOrder(ORDER_NO);

            // 消费应抛异常（让 MQ 重试）
            assertThatThrownBy(() -> consumer.onMessage(ORDER_NO))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("订单服务异常");

            // 验证：autoCancelOrder 被调用了
            verify(mockOrderService).autoCancelOrder(ORDER_NO);
            // 验证：幂等标记已被删除（让下次重试可以重新消费）
            assertThat(redisTemplate.hasKey(CONSUMED_KEY)).isFalse();
        }

        @Test
        @DisplayName("消费失败后重试：可以重新消费（幂等标记已删除）")
        void onMessage_retryAfterFail_canConsumeAgain() {
            OrderTimeoutConsumer consumer = createConsumer();
            // 第一次消费失败，第二次成功
            // 小白讲解：autoCancelOrder 返回 void，用 doThrow().doNothing().when() 语法
            org.mockito.Mockito.doThrow(new RuntimeException("第一次失败"))
                    .doNothing()
                    .when(mockOrderService).autoCancelOrder(ORDER_NO);

            // 第一次消费：失败，抛异常
            assertThatThrownBy(() -> consumer.onMessage(ORDER_NO))
                    .hasMessageContaining("第一次失败");
            // 验证：幂等标记已删除
            assertThat(redisTemplate.hasKey(CONSUMED_KEY)).isFalse();

            // 第二次消费（MQ 重试）：成功
            consumer.onMessage(ORDER_NO);
            // 验证：autoCancelOrder 被调用了2次（第一次失败 + 第二次成功）
            verify(mockOrderService, times(2)).autoCancelOrder(ORDER_NO);
            // 验证：幂等标记已写入（第二次成功后）
            assertThat(redisTemplate.hasKey(CONSUMED_KEY)).isTrue();
        }

        @Test
        @DisplayName("幂等标记 TTL 24小时")
        void onMessage_consumedKeyTtl24Hours() {
            OrderTimeoutConsumer consumer = createConsumer();

            consumer.onMessage(ORDER_NO);

            // 验证：幂等标记存在，且 TTL 大约是 24 小时（86400 秒）
            Long ttl = redisTemplate.getExpire(CONSUMED_KEY);
            assertThat(ttl).isNotNull();
            // TTL 应该在 23 小时到 24 小时之间（考虑测试执行时间）
            assertThat(ttl).isGreaterThan(23 * 3600L);
            assertThat(ttl).isLessThanOrEqualTo(24 * 3600L);
        }
    }

    // ==================== 3. 消费者与 Service 集成测试 ====================

    @Nested
    @DisplayName("3. 消费者与 OrderService 真实集成")
    class ConsumerServiceIntegrationTest {

        /**
         * 创建消费者实例（注入真实 OrderServiceImpl 和真实 Redis）
         * 小白讲解：这里用真实的 OrderServiceImpl，不是 Mock，
         * 验证消费者调用 autoCancelOrder 后订单真的被取消了。
         */
        private OrderTimeoutConsumer createConsumerWithRealService() {
            return new OrderTimeoutConsumer(orderService, redisTemplate);
        }

        @Test
        @DisplayName("消费消息 → autoCancelOrder → 订单状态变为已取消（真实集成）")
        void onMessage_realService_orderCancelled() {
            // 先下单
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            orderService.createOrder(USER_ID, buildCreateDTO(1));
            // 验证订单状态是待付款
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM order_info WHERE order_no = ?",
                    Integer.class, ORDER_NO)).isEqualTo(OrderStatusEnum.UNPAID.getCode());

            // 创建消费者（注入真实 OrderServiceImpl）
            OrderTimeoutConsumer consumer = createConsumerWithRealService();

            // 消费延时消息
            consumer.onMessage(ORDER_NO);

            // 验证：订单状态变为已取消
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM order_info WHERE order_no = ?",
                    Integer.class, ORDER_NO)).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：幂等标记已写入
            assertThat(redisTemplate.hasKey(CONSUMED_KEY)).isTrue();
            // 验证：库存回滚 Feign 被调用
            verify(productFeignClient).addStock(eq(SKU_ID), eq(1));
        }

        @Test
        @DisplayName("订单已支付后消费延时消息：autoCancelOrder 不执行取消")
        void onMessage_orderAlreadyPaid_noCancel() {
            // 先下单
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            orderService.createOrder(USER_ID, buildCreateDTO(1));

            // 手动将订单状态改为已支付
            jdbcTemplate.update("UPDATE order_info SET status = ? WHERE order_no = ?",
                    OrderStatusEnum.PAID.getCode(), ORDER_NO);

            // 重置 Mock（因为下单时 addStock 可能被调用过）
            org.mockito.Mockito.reset(productFeignClient);

            OrderTimeoutConsumer consumer = createConsumerWithRealService();
            consumer.onMessage(ORDER_NO);

            // 验证：订单状态仍是已支付（未被取消）
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM order_info WHERE order_no = ?",
                    Integer.class, ORDER_NO)).isEqualTo(OrderStatusEnum.PAID.getCode());
            // 验证：库存回滚 Feign 未被调用（因为订单已支付，不需要回滚）
            verify(productFeignClient, never()).addStock(anyLong(), anyInt());
            // 验证：幂等标记已写入（消费者执行成功了，即使没有取消订单）
            assertThat(redisTemplate.hasKey(CONSUMED_KEY)).isTrue();
        }

        @Test
        @DisplayName("订单不存在时消费消息：不抛异常，幂等标记正常写入")
        void onMessage_orderNotFound_noException() {
            OrderTimeoutConsumer consumer = createConsumerWithRealService();

            // 消费一个不存在的订单号（不应抛异常）
            consumer.onMessage("NOT_EXIST_ORDER");

            // 验证：幂等标记已写入（消费者正常执行完成）
            assertThat(redisTemplate.hasKey("order:timeout:consumed:NOT_EXIST_ORDER")).isTrue();
        }
    }
}
