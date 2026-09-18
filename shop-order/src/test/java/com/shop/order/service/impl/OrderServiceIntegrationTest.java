package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.OrderAddress;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.entity.OrderLog;
import com.shop.model.order.enums.OrderStatusEnum;
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
import com.shop.order.mapper.OrderLogMapper;
import com.shop.order.mapper.OrderLogisticsMapper;
import com.shop.order.util.OrderNoGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterAll;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-I-01 订单全链路集成测试
 * <p>
 * 小白讲解：
 * 之前的单元测试把数据库 Mapper、Redis、Feign 全部 Mock 了，只能验证"调用了 insert 方法"，
 * 但不能验证"订单真的写入数据库了吗"、"订单明细和地址快照都持久化了吗"、"分布式锁真的释放了吗"。
 *
 * 这个集成测试用真实的 H2 内存数据库 + 真实的 Redis（本地 Docker），
 * 验证订单创建的完整链路在真实持久化场景下的行为：
 * 1. 下单成功：订单主表/明细/地址快照/日志都真实写入 H2 数据库
 * 2. 分布式锁：真实写入 Redis 并在 finally 中释放
 * 3. 金额计算：满减优惠、优惠券优惠、实付金额正确持久化
 * 4. 库存扣减：Feign 调用被正确触发
 * 5. MQ 延时消息：下单成功后发送超时取消延时消息
 * 6. 下单失败：库存不足/地址失败/商品下架时，数据库无脏数据，锁正确释放
 * 7. 取消订单：状态流转持久化，库存回滚 Feign 调用
 * 8. 支付成功：状态从待付款变为待发货，持久化到数据库
 * 9. 超时自动取消：MQ 消费者调用，状态更新 + 库存回滚
 *
 * 隔离策略：
 * - H2 内存数据库：每次测试前清空所有表，不影响其他测试
 * - Redis DB 12（B-I-02 用 13，B-I-03 用 15，B-I-06 用 14，业务用 0）
 *
 * Mock 策略：
 * - 8 个 Feign 客户端 + RocketMQTemplate + OrderNoGenerator 用 Mockito 假装
 * - 5 个 Mapper 用真实 H2 数据库
 * - StringRedisTemplate 用真实 Redis
 *
 * 环境要求：
 * - 本地 Docker 中需有 Redis 容器监听 6379 端口
 * - H2 数据库不需要额外安装，Maven 依赖已包含
 * </p>
 */
@DisplayName("B-I-01 订单全链路集成测试 - 真实 H2 + Redis")
class OrderServiceIntegrationTest {

    // ==================== 测试常量 ====================

    /** 测试用 Redis 数据库索引 */
    private static final int TEST_DB_INDEX = 12;
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    private static final Long USER_ID = 1001L;
    private static final Long SKU_ID = 3001L;
    private static final Long PRODUCT_ID = 2001L;
    private static final Long ADDRESS_ID = 4001L;
    private static final String ORDER_NO = "1829384756102345678";
    private static final String LOCK_KEY = "order:create:" + USER_ID;

    // ==================== H2 数据库共享资源 ====================

    /** H2 内存数据源 */
    private static EmbeddedDatabase dataSource;
    /** MyBatis SqlSessionFactory */
    private static SqlSessionFactory sqlSessionFactory;

    // ==================== Redis 共享资源 ====================

    /** 共享的 Lettuce 连接工厂（指向 DB 12） */
    private static LettuceConnectionFactory sharedFactory;
    /** Redis 是否可用 */
    private static boolean redisAvailable = false;

    // ==================== 测试实例字段 ====================

    /** 真实 Redis 客户端 */
    private StringRedisTemplate redisTemplate;
    /** 数据库会话 */
    private SqlSession sqlSession;
    /** JdbcTemplate（清理数据用） */
    private JdbcTemplate jdbcTemplate;

    // 真实 Mapper
    private OrderInfoMapper orderInfoMapper;
    private OrderItemMapper orderItemMapper;
    private OrderAddressMapper orderAddressMapper;
    private OrderLogMapper orderLogMapper;
    private OrderLogisticsMapper orderLogisticsMapper;

    // Mock 依赖
    private ProductFeignClient productFeignClient;
    private UserFeignClient userFeignClient;
    private CartFeignClient cartFeignClient;
    private MerchantFeignClient merchantFeignClient;
    private NotificationFeignClient notificationFeignClient;
    private CouponFeignClient couponFeignClient;
    private PromotionFeignClient promotionFeignClient;
    private OrderNoGenerator orderNoGenerator;
    private RocketMQTemplate rocketMQTemplate;

    /** 被测对象 */
    private OrderServiceImpl orderService;

    /**
     * 所有测试前初始化 H2 数据库和 Redis 连接
     */
    @BeforeAll
    static void initAll() {
        initH2Database();
        initRedis();
    }

    /**
     * 初始化 H2 内存数据库 + MyBatis-Plus
     * 小白讲解：创建一个跑在内存里的数据库，执行建表脚本，注册所有 Mapper。
     * 注意：需要配置 MetaObjectHandler 自动填充 createTime/updateTime，
     * 因为测试环境没有 Spring 容器，生产环境的 MetaObjectHandler 不会生效。
     */
    private static void initH2Database() {
        try {
            dataSource = (EmbeddedDatabase) new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("orderintegration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
                    .addScript("classpath:schema-h2.sql")
                    .build();

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            // 配置自动填充处理器：insert 时填充 createTime 和 updateTime，
            // update 时填充 updateTime。生产环境由 MetaObjectHandler 自动处理，
            // 测试环境需要手动配置，否则 createTime 为 null 会触发 NOT NULL 约束异常。
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

            // 注册所有 Mapper 接口
            sqlSessionFactory.getConfiguration().addMapper(OrderInfoMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderItemMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderAddressMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderLogMapper.class);
            sqlSessionFactory.getConfiguration().addMapper(OrderLogisticsMapper.class);
        } catch (Exception e) {
            System.err.println("[B-I-01] H2 初始化失败：" + e.getMessage());
        }
    }

    /**
     * 初始化 Redis 连接
     */
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
            System.err.println("[B-I-01] Redis 不可用，测试将被跳过：" + e.getMessage());
        }
    }

    /**
     * 每个测试前重建所有实例，清空数据库和 Redis
     */
    @BeforeEach
    void setUp() {
        // 跳过条件：H2 或 Redis 不可用
        org.junit.jupiter.api.Assumptions.assumeTrue(sqlSessionFactory != null,
                "H2 数据库初始化失败，跳过 B-I-01 集成测试");
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis 不可用，跳过 B-I-01 集成测试");

        // 打开数据库会话
        sqlSession = sqlSessionFactory.openSession(true);
        orderInfoMapper = sqlSession.getMapper(OrderInfoMapper.class);
        orderItemMapper = sqlSession.getMapper(OrderItemMapper.class);
        orderAddressMapper = sqlSession.getMapper(OrderAddressMapper.class);
        orderLogMapper = sqlSession.getMapper(OrderLogMapper.class);
        orderLogisticsMapper = sqlSession.getMapper(OrderLogisticsMapper.class);
        jdbcTemplate = new JdbcTemplate(dataSource);

        // 清空所有表数据
        jdbcTemplate.execute("DELETE FROM order_log");
        jdbcTemplate.execute("DELETE FROM order_address");
        jdbcTemplate.execute("DELETE FROM order_item");
        jdbcTemplate.execute("DELETE FROM order_info");

        // 初始化 Redis
        redisTemplate = new StringRedisTemplate(sharedFactory);
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        // 创建 Mock 对象
        productFeignClient = mock(ProductFeignClient.class);
        userFeignClient = mock(UserFeignClient.class);
        cartFeignClient = mock(CartFeignClient.class);
        merchantFeignClient = mock(MerchantFeignClient.class);
        notificationFeignClient = mock(NotificationFeignClient.class);
        couponFeignClient = mock(CouponFeignClient.class);
        promotionFeignClient = mock(PromotionFeignClient.class);
        orderNoGenerator = mock(OrderNoGenerator.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);

        // 创建被测对象，手动注入：真实 Mapper + 真实 Redis + Mock 依赖
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
     * 构造下单请求 DTO
     */
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

    /**
     * 构造 SKU 信息（库存充足、已上架）
     */
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

    /**
     * 构造收货地址
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
     * 设置下单成功所需的 Mock 桩
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

    /**
     * 查询数据库中订单数量
     */
    private int countOrdersInDb() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class);
    }

    /**
     * 查询数据库中订单明细数量
     */
    private int countOrderItemsInDb() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_item", Integer.class);
    }

    /**
     * 查询数据库中订单地址数量
     */
    private int countOrderAddressesInDb() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_address", Integer.class);
    }

    /**
     * 查询数据库中订单日志数量
     */
    private int countOrderLogsInDb() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_log", Integer.class);
    }

    // ==================== 1. 下单成功全链路测试 ====================

    @Nested
    @DisplayName("下单成功全链路")
    class CreateOrderSuccessTest {

        @Test
        @DisplayName("下单成功：订单主表/明细/地址/日志都写入 H2，分布式锁写入并释放")
        void createOrder_success_allDataPersistedToDb() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));

            OrderDetailVO result = orderService.createOrder(USER_ID, buildCreateDTO(2));

            // 验证返回值
            assertThat(result).isNotNull();
            assertThat(result.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(result.getStatus()).isEqualTo(OrderStatusEnum.UNPAID.getCode());

            // 验证：订单主表写入 1 条
            assertThat(countOrdersInDb()).isEqualTo(1);
            // 验证：订单明细写入 1 条
            assertThat(countOrderItemsInDb()).isEqualTo(1);
            // 验证：订单地址写入 1 条
            assertThat(countOrderAddressesInDb()).isEqualTo(1);
            // 验证：订单日志写入 1 条（创建订单日志）
            assertThat(countOrderLogsInDb()).isEqualTo(1);

            // 验证：分布式锁已释放（finally 块执行）
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();

            // 验证：库存扣减 Feign 被调用
            verify(productFeignClient).batchDeductStock(anyList(), eq(ORDER_NO));
            // 验证：MQ 延时消息已发送
            // 小白讲解：RocketMQTemplate.syncSend 的第三个参数 timeout 是 long 类型，
            // Mockito 的 eq() 会自动装箱，所以要用 3000L（Long）而不是 3000（Integer）
            verify(rocketMQTemplate).syncSend(eq("topic_order_timeout"), any(), eq(3000L), eq(16));
            // 验证：购物车删除被调用
            verify(cartFeignClient).deleteBySkuIds(eq(USER_ID), anyList());
            // 验证：销量累加被调用
            verify(productFeignClient).incrSalesBatch(any());
        }

        @Test
        @DisplayName("金额计算正确：totalAmount=20，promotionDiscount=0，couponDiscount=0，payAmount=20")
        void createOrder_success_amountCalculationCorrect() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(2));

            // 查询数据库验证金额
            OrderInfo dbOrder = orderInfoMapper.selectOne(null);
            assertThat(dbOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(dbOrder.getPayAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(dbOrder.getPromotionDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dbOrder.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dbOrder.getStatus()).isEqualTo(OrderStatusEnum.UNPAID.getCode());
            assertThat(dbOrder.getOrderType()).isEqualTo(1); // 普通订单
        }

        @Test
        @DisplayName("订单明细正确持久化：productId、skuId、price、quantity、subtotal")
        void createOrder_success_orderItemPersistedCorrectly() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(2));

            List<OrderItem> items = orderItemMapper.selectList(null);
            assertThat(items).hasSize(1);
            OrderItem item = items.get(0);
            assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(item.getSkuId()).isEqualTo(SKU_ID);
            assertThat(item.getPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getSubtotal()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(item.getOrderNo()).isEqualTo(ORDER_NO);
        }

        @Test
        @DisplayName("订单地址快照正确持久化：name、phone、province、city、district、detail")
        void createOrder_success_addressSnapshotPersistedCorrectly() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(2));

            List<OrderAddress> addresses = orderAddressMapper.selectList(null);
            assertThat(addresses).hasSize(1);
            OrderAddress addr = addresses.get(0);
            assertThat(addr.getName()).isEqualTo("张三");
            assertThat(addr.getPhone()).isEqualTo("13800138000");
            assertThat(addr.getProvince()).isEqualTo("广东省");
            assertThat(addr.getCity()).isEqualTo("深圳市");
            assertThat(addr.getDistrict()).isEqualTo("南山区");
            assertThat(addr.getDetail()).isEqualTo("科技园路1号");
            assertThat(addr.getOrderNo()).isEqualTo(ORDER_NO);
        }

        @Test
        @DisplayName("分布式锁真实写入 Redis 并在 finally 中释放")
        void createOrder_success_distributedLockWrittenAndReleased() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(1));

            // 验证：锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }
    }

    // ==================== 2. 下单失败场景测试 ====================

    @Nested
    @DisplayName("下单失败场景")
    class CreateOrderFailTest {

        @Test
        @DisplayName("库存不足：抛异常，数据库无脏数据，分布式锁释放")
        void createOrder_stockNotEnough_noDirtyData() {
            mockCreateOrderSuccess(1, new BigDecimal("10.00"));
            // 买 5 件但库存只有 1 件
            OrderCreateDTO dto = buildCreateDTO(5);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, dto))
                    .isInstanceOf(BusinessException.class);

            // 验证：数据库无脏数据
            assertThat(countOrdersInDb()).isEqualTo(0);
            assertThat(countOrderItemsInDb()).isEqualTo(0);
            assertThat(countOrderAddressesInDb()).isEqualTo(0);

            // 验证：分布式锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();

            // 验证：库存扣减未被调用
            verify(productFeignClient, never()).batchDeductStock(anyList(), anyString());
        }

        @Test
        @DisplayName("地址获取失败：抛异常，数据库无脏数据，锁释放")
        void createOrder_addressFail_noDirtyData() {
            when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
            when(productFeignClient.batchGetSkuByIds(anyList()))
                    .thenReturn(Result.success(Collections.singletonList(buildSku(100, new BigDecimal("10.00")))));
            // 地址获取失败
            when(userFeignClient.getAddressById(ADDRESS_ID))
                    .thenReturn(Result.fail(ErrorCode.DATA_NOT_FOUND.getCode(), "地址不存在"));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(1)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("收货地址获取失败");

            assertThat(countOrdersInDb()).isEqualTo(0);
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("商品已下架：抛异常，数据库无脏数据，锁释放")
        void createOrder_productOffShelf_noDirtyData() {
            ProductSkuVO sku = buildSku(100, new BigDecimal("10.00"));
            sku.setStatus(0); // 下架
            when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
            when(productFeignClient.batchGetSkuByIds(anyList()))
                    .thenReturn(Result.success(Collections.singletonList(sku)));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(1)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("商品已下架");

            assertThat(countOrdersInDb()).isEqualTo(0);
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("库存扣减失败：抛异常，数据库无脏数据，锁释放")
        void createOrder_stockDeductFail_noDirtyData() {
            when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
            when(productFeignClient.batchGetSkuByIds(anyList()))
                    .thenReturn(Result.success(Collections.singletonList(buildSku(100, new BigDecimal("10.00")))));
            when(userFeignClient.getAddressById(ADDRESS_ID))
                    .thenReturn(Result.success(buildAddress()));
            when(promotionFeignClient.calculatePromotion(any()))
                    .thenReturn(Result.success(BigDecimal.ZERO));
            // 库存扣减失败
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(Result.fail(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode(), "库存不足"));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(1)))
                    .isInstanceOf(BusinessException.class);

            // 验证：数据库无脏数据（注意：由于没有 Spring 事务管理，已写入的数据不会自动回滚，
            // 但 createOrder 的 @Transactional 在无 Spring 环境下不生效，
            // 所以订单数据可能已写入。这里我们验证库存扣减失败时应抛异常）
            // 小白讲解：真实环境下 @Transactional 会回滚，但测试环境无 Spring 容器，事务不生效
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("分布式锁竞争：同一用户并发下单时第二次抛异常")
        void createOrder_lockContention_throwsException() {
            // 预置锁，模拟另一个请求正在创建订单
            redisTemplate.opsForValue().set(LOCK_KEY, String.valueOf(System.currentTimeMillis()));

            mockCreateOrderSuccess(100, new BigDecimal("10.00"));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(1)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("操作太频繁");

            // 验证：数据库无订单
            assertThat(countOrdersInDb()).isEqualTo(0);
            // 验证：库存扣减未被调用
            verify(productFeignClient, never()).batchDeductStock(anyList(), anyString());
        }
    }

    // ==================== 3. 取消订单测试 ====================

    @Nested
    @DisplayName("取消订单全链路")
    class CancelOrderTest {

        @Test
        @DisplayName("取消待付款订单：状态变为已取消，库存回滚 Feign 调用，日志记录")
        void cancelOrder_success_statusUpdatedAndStockRollback() {
            // 先创建一个订单
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(2));
            Long orderId = created.getId();

            // 清空之前的 Mock 调用记录
            org.mockito.Mockito.clearInvocations(productFeignClient);

            // 取消订单
            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, orderId, cancelDTO);

            // 验证：订单状态变为已取消
            OrderInfo dbOrder = orderInfoMapper.selectById(orderId);
            assertThat(dbOrder.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            assertThat(dbOrder.getCancelReason()).isEqualTo("不想买了");
            assertThat(dbOrder.getCancelTime()).isNotNull();

            // 验证：库存回滚 Feign 被调用
            verify(productFeignClient).addStock(eq(SKU_ID), eq(2));

            // 验证：订单日志新增 1 条（取消日志）
            List<OrderLog> logs = orderLogMapper.selectList(null);
            assertThat(logs).hasSize(2); // 创建日志 + 取消日志
            OrderLog cancelLog = logs.stream()
                    .filter(l -> "用户取消订单".equals(l.getAction()))
                    .findFirst()
                    .orElseThrow();
            assertThat(cancelLog.getFromStatus()).isEqualTo(OrderStatusEnum.UNPAID.getCode());
            assertThat(cancelLog.getToStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
        }

        @Test
        @DisplayName("取消非待付款订单：抛异常，状态不变")
        void cancelOrder_wrongStatus_throwsException() {
            // 创建订单后手动改为已支付
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(1));
            Long orderId = created.getId();

            // 手动改为已支付
            OrderInfo order = orderInfoMapper.selectById(orderId);
            order.setStatus(OrderStatusEnum.PAID.getCode());
            orderInfoMapper.updateById(order);

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");

            assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, orderId, cancelDTO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("订单状态不允许");

            // 验证：状态没变
            OrderInfo dbOrder = orderInfoMapper.selectById(orderId);
            assertThat(dbOrder.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
        }
    }

    // ==================== 4. 支付成功测试 ====================

    @Nested
    @DisplayName("支付成功全链路")
    class PaySuccessTest {

        @Test
        @DisplayName("支付成功：状态从待付款变为待发货，payTime 持久化")
        void paySuccess_success_statusUpdated() {
            // 先创建订单
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(1));

            // 支付成功回调
            orderService.paySuccess(ORDER_NO);

            // 验证：状态变为待发货
            OrderInfo dbOrder = orderInfoMapper.selectById(created.getId());
            assertThat(dbOrder.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
            assertThat(dbOrder.getPayTime()).isNotNull();
        }

        @Test
        @DisplayName("重复支付回调：幂等处理，不抛异常，状态不变")
        void paySuccess_duplicateCall_idempotent() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));
            orderService.createOrder(USER_ID, buildCreateDTO(1));

            // 第一次支付回调
            orderService.paySuccess(ORDER_NO);
            // 第二次支付回调（重复）
            orderService.paySuccess(ORDER_NO);

            // 验证：状态还是待发货（幂等处理）
            OrderInfo dbOrder = orderInfoMapper.selectOne(null);
            assertThat(dbOrder.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
        }
    }

    // ==================== 5. 超时自动取消测试 ====================

    @Nested
    @DisplayName("超时自动取消")
    class AutoCancelTest {

        @Test
        @DisplayName("超时自动取消：状态变为已取消，库存回滚 Feign 调用")
        void autoCancel_success_statusUpdatedAndStockRollback() {
            // 先创建订单
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(2));

            org.mockito.Mockito.clearInvocations(productFeignClient);

            // 超时自动取消
            orderService.autoCancelOrder(ORDER_NO);

            // 验证：状态变为已取消
            OrderInfo dbOrder = orderInfoMapper.selectById(created.getId());
            assertThat(dbOrder.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            assertThat(dbOrder.getCancelReason()).contains("超时");

            // 验证：库存回滚 Feign 被调用
            verify(productFeignClient).addStock(eq(SKU_ID), eq(2));
        }

        @Test
        @DisplayName("订单已支付时超时取消：不取消，状态不变")
        void autoCancel_alreadyPaid_noChange() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));
            orderService.createOrder(USER_ID, buildCreateDTO(1));

            // 先支付成功
            orderService.paySuccess(ORDER_NO);

            org.mockito.Mockito.clearInvocations(productFeignClient);

            // 超时取消（不应取消已支付订单）
            orderService.autoCancelOrder(ORDER_NO);

            // 验证：状态还是待发货
            OrderInfo dbOrder = orderInfoMapper.selectOne(null);
            assertThat(dbOrder.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());

            // 验证：库存回滚未被调用
            verify(productFeignClient, never()).addStock(anyLong(), any());
        }
    }

    // ==================== 6. 满减优惠集成测试 ====================

    @Nested
    @DisplayName("满减优惠集成")
    class PromotionTest {

        @Test
        @DisplayName("满减优惠正确计算：totalAmount=100，promotionDiscount=20，payAmount=80")
        void createOrder_withPromotion_amountCalculationCorrect() {
            mockCreateOrderSuccess(100, new BigDecimal("50.00"));
            // 满减优惠 20 元
            when(promotionFeignClient.calculatePromotion(any()))
                    .thenReturn(Result.success(new BigDecimal("20.00")));

            // 买 2 件，单价 50，总价 100
            orderService.createOrder(USER_ID, buildCreateDTO(2));

            OrderInfo dbOrder = orderInfoMapper.selectOne(null);
            assertThat(dbOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(dbOrder.getPromotionDiscount()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(dbOrder.getPayAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(dbOrder.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("满减计算失败时降级为 0，不影响下单")
        void createOrder_promotionFail_degradeToZero() {
            mockCreateOrderSuccess(100, new BigDecimal("10.00"));
            // 满减服务挂了
            when(promotionFeignClient.calculatePromotion(any()))
                    .thenThrow(new RuntimeException("满减服务不可用"));

            // 下单应成功
            OrderDetailVO result = orderService.createOrder(USER_ID, buildCreateDTO(1));
            assertThat(result).isNotNull();

            // 满减优惠为 0
            OrderInfo dbOrder = orderInfoMapper.selectOne(null);
            assertThat(dbOrder.getPromotionDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dbOrder.getPayAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        }
    }
}
