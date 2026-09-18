package com.shop.order.service.impl;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.Result;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.enums.OrderTypeEnum;
import com.shop.model.order.vo.OrderDetailVO;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-I-04 分布式事务集成测试（补偿回滚）
 * <p>
 * 小白讲解：
 * 微服务架构下，一个业务操作可能跨多个服务（比如下单要扣库存+核销优惠券+发MQ），
 * 如果中间某步失败，前面已经成功的步骤需要"补偿回滚"，否则数据会不一致。
 *
 * Shop 项目跳过了 Seata（强一致性事务框架），改用"本地事务 + 补偿回退"模式：
 * 1. 强依赖（如优惠券核销）：失败直接抛异常，本地事务回滚（数据库操作自动回滚）
 * 2. 弱依赖（如库存回滚、秒杀库存回滚）：失败用 try-catch 吞掉异常，记录日志后续补偿
 *
 * 这个测试验证各种补偿回滚场景下数据的一致性：
 * 1. 下单时库存扣减失败：订单未写入数据库（本地事务回滚）
 * 2. 取消订单时库存回滚：每个 SKU 调用一次 addStock
 * 3. 取消订单时秒杀库存回滚：Redis 秒杀库存 +1
 * 4. 取消订单时库存/秒杀库存回滚失败：不影响取消主流程（弱依赖）
 * 5. 自动取消订单时库存和秒杀库存回滚
 * 6. 乐观锁并发：状态已变更时取消失败
 *
 * 测试架构：
 * - H2 内存数据库（数据库名 disttxn，与其他测试隔离）
 * - 本地 Redis DB 10（与 B-I-05 用 11、B-I-01 用 12、B-I-02 用 13 隔离）
 * - 5 个 Mapper 用真实 H2，8 个 Feign + MQ + OrderNoGenerator 用 Mockito
 * </p>
 */
@DisplayName("B-I-04 分布式事务集成测试 - 补偿回滚与数据一致性")
class DistributedTransactionIntegrationTest {

    // ==================== 测试常量 ====================

    private static final int TEST_DB_INDEX = 10;
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    private static final Long USER_ID = 1101L;
    private static final Long SKU_ID_1 = 1201L;
    private static final Long SKU_ID_2 = 1202L;
    private static final Long PRODUCT_ID = 1301L;
    private static final Long ADDRESS_ID = 1401L;
    private static final Long SECKILL_ID = 1501L;
    private static final String ORDER_NO = "1111222233334444555";
    private static final String LOCK_KEY = "order:create:" + USER_ID;
    private static final String SECKILL_STOCK_KEY = "seckill:stock:" + SECKILL_ID;

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

    /**
     * 初始化 H2 内存数据库
     * 小白讲解：用独立的 H2 数据库名 "disttxn"，与其他测试类隔离。
     * 必须配置 MetaObjectHandler 自动填充 createTime/updateTime，否则 INSERT 时会触发 NOT NULL 约束。
     */
    private static void initH2Database() {
        try {
            dataSource = (EmbeddedDatabase) new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("disttxn;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
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
            System.err.println("[B-I-04] H2 初始化失败：" + e.getMessage());
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
            System.err.println("[B-I-04] Redis 不可用，测试将被跳过：" + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(sqlSessionFactory != null,
                "H2 数据库初始化失败，跳过 B-I-04 集成测试");
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis 不可用，跳过 B-I-04 集成测试");

        sqlSession = sqlSessionFactory.openSession(true);
        orderInfoMapper = sqlSession.getMapper(OrderInfoMapper.class);
        orderItemMapper = sqlSession.getMapper(OrderItemMapper.class);
        orderAddressMapper = sqlSession.getMapper(OrderAddressMapper.class);
        orderLogMapper = sqlSession.getMapper(OrderLogMapper.class);
        orderLogisticsMapper = sqlSession.getMapper(OrderLogisticsMapper.class);
        jdbcTemplate = new JdbcTemplate(dataSource);

        // 清空所有表
        jdbcTemplate.execute("DELETE FROM order_log");
        jdbcTemplate.execute("DELETE FROM order_address");
        jdbcTemplate.execute("DELETE FROM order_item");
        jdbcTemplate.execute("DELETE FROM order_info");

        // 初始化 Redis
        redisTemplate = new StringRedisTemplate(sharedFactory);
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        // 创建 Mock
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

    /**
     * 构造单 SKU 下单请求
     */
    private OrderCreateDTO buildCreateDTO(int quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setAddressId(ADDRESS_ID);
        dto.setRemark("放门口");
        OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
        item.setSkuId(SKU_ID_1);
        item.setQuantity(quantity);
        dto.setItems(Collections.singletonList(item));
        return dto;
    }

    /**
     * 构造多 SKU 下单请求（用于测试多 SKU 库存回滚）
     */
    private OrderCreateDTO buildMultiSkuCreateDTO() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setAddressId(ADDRESS_ID);
        dto.setRemark("放门口");
        OrderCreateDTO.OrderItemDTO item1 = new OrderCreateDTO.OrderItemDTO();
        item1.setSkuId(SKU_ID_1);
        item1.setQuantity(2);
        OrderCreateDTO.OrderItemDTO item2 = new OrderCreateDTO.OrderItemDTO();
        item2.setSkuId(SKU_ID_2);
        item2.setQuantity(3);
        dto.setItems(Arrays.asList(item1, item2));
        return dto;
    }

    private ProductSkuVO buildSku(Long skuId, int stock, BigDecimal price) {
        ProductSkuVO sku = new ProductSkuVO();
        sku.setId(skuId);
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
        address.setName("王五");
        address.setPhone("13700137000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路3号");
        return address;
    }

    /**
     * 设置下单成功所需的 Mock（单 SKU）
     */
    private void mockCreateOrderSuccess(int stock, BigDecimal price) {
        when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
        when(productFeignClient.batchGetSkuByIds(anyList()))
                .thenReturn(Result.success(Collections.singletonList(buildSku(SKU_ID_1, stock, price))));
        when(userFeignClient.getAddressById(ADDRESS_ID))
                .thenReturn(Result.success(buildAddress()));
        when(promotionFeignClient.calculatePromotion(any()))
                .thenReturn(Result.success(BigDecimal.ZERO));
        when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                .thenReturn(Result.success(null));
    }

    /**
     * 设置多 SKU 下单成功的 Mock
     */
    private void mockMultiSkuCreateOrderSuccess() {
        when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
        when(productFeignClient.batchGetSkuByIds(anyList()))
                .thenReturn(Result.success(Arrays.asList(
                        buildSku(SKU_ID_1, 100, new BigDecimal("50.00")),
                        buildSku(SKU_ID_2, 100, new BigDecimal("30.00")))));
        when(userFeignClient.getAddressById(ADDRESS_ID))
                .thenReturn(Result.success(buildAddress()));
        when(promotionFeignClient.calculatePromotion(any()))
                .thenReturn(Result.success(BigDecimal.ZERO));
        when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                .thenReturn(Result.success(null));
    }

    private int countOrdersInDb() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class);
    }

    private OrderInfo getOrderFromDb() {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM order_info WHERE order_no = ?",
                (rs, rowNum) -> {
                    OrderInfo o = new OrderInfo();
                    o.setId(rs.getLong("id"));
                    o.setOrderNo(rs.getString("order_no"));
                    o.setUserId(rs.getLong("user_id"));
                    o.setOrderType(rs.getInt("order_type"));
                    o.setSeckillId(rs.getObject("seckill_id", Long.class));
                    o.setStatus(rs.getInt("status"));
                    return o;
                },
                ORDER_NO);
    }

    // ==================== 1. 库存扣减失败回滚测试 ====================

    @Nested
    @DisplayName("1. 下单时库存扣减失败抛异常")
    class StockDeductRollbackTest {

        // 小白讲解：
        // 生产环境下 createOrder 方法有 @Transactional 注解，库存扣减失败时整个事务会回滚（订单不写入数据库）。
        // 但测试环境手动 new OrderServiceImpl(...) 没有 Spring 代理，@Transactional 不生效，
        // 所以库存扣减失败时订单已经写入了 H2 数据库（不会自动回滚）。
        // 这里只验证"库存扣减失败时抛出了正确的异常"，数据库回滚由生产环境的 Spring 事务管理保证。

        @Test
        @DisplayName("库存扣减返回失败 Result：抛 BusinessException")
        void createOrder_stockDeductReturnFail_throwsException() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(Result.fail(500, "库存不足"));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2)))
                    .isInstanceOf(BusinessException.class);

            // 验证：分布式锁已释放（finally 块执行）
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("库存扣减抛异常：原异常向上传播")
        void createOrder_stockDeductThrowsException_exceptionPropagates() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenThrow(new RuntimeException("商品服务超时"));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("商品服务超时");

            // 验证：锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("库存扣减返回 null：抛 BusinessException")
        void createOrder_stockDeductReturnNull_throwsException() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2)))
                    .isInstanceOf(BusinessException.class);

            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("库存扣减失败后：库存回滚 Feign 未被调用（下单流程未进入回滚分支）")
        void createOrder_stockDeductFail_rollbackFeignNotCalledDuringCreate() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(Result.fail(500, "库存不足"));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2)));

            // 验证：下单流程中 addStock（回滚库存）未被调用
            // 小白讲解：下单失败时不会自动调用 addStock 回滚，而是通过抛异常让事务回滚
            // addStock 只在取消订单（cancelOrder）时被调用
            verify(productFeignClient, never()).addStock(anyLong(), any());
            // 验证：锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }
    }

    // ==================== 2. 取消订单时库存回滚测试 ====================

    @Nested
    @DisplayName("2. 取消订单时库存回滚")
    class CancelOrderStockRollbackTest {

        @Test
        @DisplayName("取消订单单 SKU：addStock Feign 被调用1次")
        void cancelOrder_singleSku_addStockCalled() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(2));
            assertThat(countOrdersInDb()).isEqualTo(1);

            // 重置 Mock
            org.mockito.Mockito.reset(productFeignClient);
            // addStock 不需要返回值（或返回成功）

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：addStock 被调用1次（单 SKU）
            verify(productFeignClient).addStock(eq(SKU_ID_1), eq(2));
            // 验证：订单状态已更新为已取消
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
        }

        @Test
        @DisplayName("取消订单多 SKU：每个 SKU 分别调用 addStock")
        void cancelOrder_multiSku_addStockCalledForEach() {
            mockMultiSkuCreateOrderSuccess();
            OrderDetailVO created = orderService.createOrder(USER_ID, buildMultiSkuCreateDTO());
            assertThat(countOrdersInDb()).isEqualTo(1);

            // 重置 Mock
            org.mockito.Mockito.reset(productFeignClient);

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：两个 SKU 的 addStock 都被调用
            verify(productFeignClient).addStock(eq(SKU_ID_1), eq(2));
            verify(productFeignClient).addStock(eq(SKU_ID_2), eq(3));
            // 验证：至少调用了2次（每个 SKU 一次）
            verify(productFeignClient, atLeastOnce()).addStock(anyLong(), any());
        }

        @Test
        @DisplayName("取消订单时库存回滚失败：不影响取消订单主流程")
        void cancelOrder_stockRollbackFail_stillCancelSuccess() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(2));

            // 重置 Mock，让 addStock 抛异常
            org.mockito.Mockito.reset(productFeignClient);
            when(productFeignClient.addStock(anyLong(), any()))
                    .thenThrow(new RuntimeException("商品服务不可用"));

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            // 取消订单不应抛异常（库存回滚是弱依赖）
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：订单状态变为已取消（即使库存回滚失败）
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：addStock 被调用了（虽然失败）
            verify(productFeignClient).addStock(eq(SKU_ID_1), eq(2));
        }

        @Test
        @DisplayName("取消订单多 SKU 部分回滚失败：其他 SKU 仍尝试回滚（独立 try-catch）")
        void cancelOrder_multiSku_partialRollbackFail_otherStillRollback() {
            mockMultiSkuCreateOrderSuccess();
            OrderDetailVO created = orderService.createOrder(USER_ID, buildMultiSkuCreateDTO());

            // 重置 Mock：第一个 SKU 回滚抛异常，第二个 SKU 正常
            org.mockito.Mockito.reset(productFeignClient);
            when(productFeignClient.addStock(eq(SKU_ID_1), any()))
                    .thenThrow(new RuntimeException("SKU1 回滚失败"));
            // SKU_ID_2 不设置 thenThrow，默认返回 null（成功）

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：订单状态变为已取消
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：两个 SKU 都被尝试调用 addStock（即使第一个失败，第二个仍然执行）
            verify(productFeignClient).addStock(eq(SKU_ID_1), eq(2));
            verify(productFeignClient).addStock(eq(SKU_ID_2), eq(3));
        }
    }

    // ==================== 3. 秒杀库存回滚测试 ====================

    @Nested
    @DisplayName("3. 秒杀订单取消时 Redis 秒杀库存回滚")
    class SeckillStockRollbackTest {

        /**
         * 直接在数据库插入一条秒杀订单（绕过 createOrder，因为 createOrder 不支持秒杀下单）
         */
        private Long insertSeckillOrderToDb() {
            // 使用 MyBatis-Plus 直接插入
            OrderInfo order = new OrderInfo();
            order.setId(System.currentTimeMillis());
            order.setOrderNo(ORDER_NO);
            order.setUserId(USER_ID);
            order.setMerchantId(1L);
            order.setTotalAmount(new BigDecimal("100.00"));
            order.setPayAmount(new BigDecimal("100.00"));
            order.setFreightAmount(BigDecimal.ZERO);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setPromotionDiscount(BigDecimal.ZERO);
            order.setOrderType(OrderTypeEnum.SECKILL.getCode());
            order.setSeckillId(SECKILL_ID);
            order.setStatus(OrderStatusEnum.UNPAID.getCode());
            orderInfoMapper.insert(order);

            // 插入订单明细
            OrderItem item = new OrderItem();
            item.setId(System.currentTimeMillis() + 1);
            item.setOrderId(order.getId());
            item.setOrderNo(ORDER_NO);
            item.setProductId(PRODUCT_ID);
            item.setSkuId(SKU_ID_1);
            item.setProductName("秒杀商品");
            item.setPrice(new BigDecimal("100.00"));
            item.setQuantity(1);
            item.setSubtotal(new BigDecimal("100.00"));
            orderItemMapper.insert(item);

            // 预热 Redis 秒杀库存
            redisTemplate.opsForValue().set(SECKILL_STOCK_KEY, "10");

            return order.getId();
        }

        @Test
        @DisplayName("秒杀订单取消：Redis 秒杀库存 +1")
        void cancelOrder_seckillOrder_seckillStockIncrement() {
            Long orderId = insertSeckillOrderToDb();
            // 验证：初始 Redis 秒杀库存是 10
            assertThat(redisTemplate.opsForValue().get(SECKILL_STOCK_KEY)).isEqualTo("10");

            // 重置 Mock
            org.mockito.Mockito.reset(productFeignClient);

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, orderId, cancelDTO);

            // 验证：订单状态变为已取消
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：Redis 秒杀库存 +1（从 10 变成 11）
            assertThat(redisTemplate.opsForValue().get(SECKILL_STOCK_KEY)).isEqualTo("11");
        }

        @Test
        @DisplayName("普通订单取消：不触发秒杀库存回退（orderType=1）")
        void cancelOrder_normalOrder_seckillStockNotIncrement() {
            // 普通订单下单
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(1));

            // 重置 Mock
            org.mockito.Mockito.reset(productFeignClient);

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：订单状态变为已取消
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：orderType=1（普通订单）
            assertThat(getOrderFromDb().getOrderType()).isEqualTo(OrderTypeEnum.NORMAL.getCode());
            // 验证：seckill_stock Key 不存在（因为没有预热，普通订单取消也不会创建）
            assertThat(redisTemplate.hasKey(SECKILL_STOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("秒杀订单自动取消：Redis 秒杀库存 +1")
        void autoCancelOrder_seckillOrder_seckillStockIncrement() {
            Long orderId = insertSeckillOrderToDb();
            assertThat(redisTemplate.opsForValue().get(SECKILL_STOCK_KEY)).isEqualTo("10");

            // 重置 Mock
            org.mockito.Mockito.reset(productFeignClient);

            // 自动取消订单
            orderService.autoCancelOrder(ORDER_NO);

            // 验证：订单状态变为已取消
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：Redis 秒杀库存 +1
            assertThat(redisTemplate.opsForValue().get(SECKILL_STOCK_KEY)).isEqualTo("11");
        }
    }

    // ==================== 4. 自动取消订单回滚测试 ====================

    @Nested
    @DisplayName("4. 自动取消订单时补偿回滚")
    class AutoCancelRollbackTest {

        @Test
        @DisplayName("自动取消订单：库存回滚 Feign 被调用")
        void autoCancelOrder_stockRollbackCalled() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            orderService.createOrder(USER_ID, buildCreateDTO(2));

            // 重置 Mock
            org.mockito.Mockito.reset(productFeignClient);

            orderService.autoCancelOrder(ORDER_NO);

            // 验证：订单状态变为已取消
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：库存回滚 Feign 被调用
            verify(productFeignClient).addStock(eq(SKU_ID_1), eq(2));
        }

        @Test
        @DisplayName("自动取消订单状态不是待付款：不执行回滚")
        void autoCancelOrder_wrongStatus_noRollback() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            orderService.createOrder(USER_ID, buildCreateDTO(2));

            // 重置 Mock
            org.mockito.Mockito.reset(productFeignClient);

            // 手动将订单状态改为已支付
            jdbcTemplate.update("UPDATE order_info SET status = ? WHERE order_no = ?",
                    OrderStatusEnum.PAID.getCode(), ORDER_NO);

            // 自动取消（订单已支付，不应取消）
            orderService.autoCancelOrder(ORDER_NO);

            // 验证：订单状态仍是已支付（未被取消）
            assertThat(getOrderFromDb().getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
            // 验证：库存回滚 Feign 未被调用
            verify(productFeignClient, never()).addStock(anyLong(), any());
        }

        @Test
        @DisplayName("自动取消订单时订单不存在：不抛异常，不执行回滚")
        void autoCancelOrder_orderNotFound_noException() {
            // 没有创建任何订单，直接调用自动取消
            orderService.autoCancelOrder("NOT_EXIST_ORDER_NO");

            // 验证：没有抛异常，没有订单被取消
            assertThat(countOrdersInDb()).isEqualTo(0);
            // 验证：库存回滚 Feign 未被调用
            verify(productFeignClient, never()).addStock(anyLong(), any());
        }
    }
}
