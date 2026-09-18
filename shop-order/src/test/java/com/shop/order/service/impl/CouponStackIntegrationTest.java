package com.shop.order.service.impl;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.OrderInfo;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-I-05 优惠券叠加集成测试
 * <p>
 * 小白讲解：
 * 这个测试专门验证"满减优惠 + 优惠券"叠加使用的完整链路。
 * 之前的单元测试只验证"调用了 couponFeignClient.useCoupon 方法"，
 * 但不能验证：
 * 1. 叠加后实付金额（payAmount）是否正确持久化到数据库
 * 2. discountAmount（总优惠）和 promotionDiscount（满减优惠）字段是否分别正确记录
 * 3. 取消订单时是否真的调用了 rollbackCoupon 回退优惠券
 * 4. 优惠券门槛判断是基于"满减后金额"而不是订单原金额
 * 5. 优惠券核销失败时事务是否真的回滚（订单未写入数据库）
 *
 * 业务规则（来自项目约束）：
 * - 满减和优惠券可以叠加使用
 * - 满减先计算，优惠券基于"满减后金额"判断门槛
 * - 满减是弱依赖（失败降级为0，不影响下单）
 * - 优惠券是强依赖（核销失败直接抛异常，整个事务回滚）
 * - 取消订单时优惠券需要回退（弱依赖，回退失败不影响取消）
 *
 * 测试架构：
 * - H2 内存数据库（数据库名 couponstack，与 B-I-01 的 orderintegration 隔离）
 * - 本地 Redis DB 11（与 B-I-01 用 12、B-I-02 用 13、B-I-06 用 14、B-I-03 用 15 隔离）
 * - 5 个 Mapper 用真实 H2，8 个 Feign + MQ + OrderNoGenerator 用 Mockito
 *
 * 环境要求：
 * - 本地 Redis 监听 6379 端口（Docker 或本地安装）
 * - H2 内存数据库不需要额外安装
 * </p>
 */
@DisplayName("B-I-05 优惠券叠加集成测试 - 满减+优惠券叠加计算与回退")
class CouponStackIntegrationTest {

    // ==================== 测试常量 ====================

    /** 测试用 Redis 数据库索引（11，避免与其他 B-I 测试冲突） */
    private static final int TEST_DB_INDEX = 11;
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    private static final Long USER_ID = 5001L;
    private static final Long SKU_ID = 6001L;
    private static final Long PRODUCT_ID = 7001L;
    private static final Long ADDRESS_ID = 8001L;
    private static final Long USER_COUPON_ID = 9001L;
    private static final String ORDER_NO = "5555666677778888999";
    private static final String LOCK_KEY = "order:create:" + USER_ID;

    // ==================== H2 数据库共享资源 ====================

    private static EmbeddedDatabase dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    // ==================== Redis 共享资源 ====================

    private static LettuceConnectionFactory sharedFactory;
    private static boolean redisAvailable = false;

    // ==================== 测试实例字段 ====================

    private StringRedisTemplate redisTemplate;
    private SqlSession sqlSession;
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
     * 初始化 H2 内存数据库
     * 小白讲解：用独立的 H2 数据库名 "couponstack"，与 B-I-01 的 orderintegration 隔离，
     * 避免两个测试类同时操作同一份数据导致脏数据。
     * 必须配置 MetaObjectHandler 自动填充 createTime/updateTime，否则 INSERT 时会触发 NOT NULL 约束。
     */
    private static void initH2Database() {
        try {
            dataSource = (EmbeddedDatabase) new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("couponstack;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
                    .addScript("classpath:schema-h2.sql")
                    .build();

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            // 配置自动填充：测试环境没有 Spring 容器，需要手动配置 MetaObjectHandler
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
            System.err.println("[B-I-05] H2 初始化失败：" + e.getMessage());
        }
    }

    /**
     * 初始化 Redis 连接（连接到 DB 11）
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
            System.err.println("[B-I-05] Redis 不可用，测试将被跳过：" + e.getMessage());
        }
    }

    /**
     * 每个测试前重建实例、清空数据
     */
    @BeforeEach
    void setUp() {
        // 跳过条件：H2 或 Redis 不可用时跳过测试
        org.junit.jupiter.api.Assumptions.assumeTrue(sqlSessionFactory != null,
                "H2 数据库初始化失败，跳过 B-I-05 集成测试");
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis 不可用，跳过 B-I-05 集成测试");

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
     * 构造下单请求 DTO（不使用优惠券）
     */
    private OrderCreateDTO buildCreateDTO(int quantity) {
        return buildCreateDTO(quantity, null);
    }

    /**
     * 构造下单请求 DTO（指定优惠券ID）
     */
    private OrderCreateDTO buildCreateDTO(int quantity, Long userCouponId) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setAddressId(ADDRESS_ID);
        dto.setUserCouponId(userCouponId);
        dto.setRemark("放门口");
        OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
        item.setSkuId(SKU_ID);
        item.setQuantity(quantity);
        dto.setItems(Collections.singletonList(item));
        return dto;
    }

    /**
     * 构造 SKU 信息
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
        address.setName("李四");
        address.setPhone("13900139000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路2号");
        return address;
    }

    /**
     * 设置下单基础 Mock（不含满减和优惠券）
     *
     * @param stock  SKU 库存
     * @param price  SKU 价格
     * @param totalAmount 订单总金额（= price * quantity，用于 Mock 返回值断言参考）
     */
    private void mockCreateOrderBase(int stock, BigDecimal price) {
        when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
        when(productFeignClient.batchGetSkuByIds(anyList()))
                .thenReturn(Result.success(Collections.singletonList(buildSku(stock, price))));
        when(userFeignClient.getAddressById(ADDRESS_ID))
                .thenReturn(Result.success(buildAddress()));
        when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                .thenReturn(Result.success(null));
    }

    /**
     * 设置满减 Mock（返回指定满减优惠金额）
     */
    private void mockPromotion(BigDecimal promotionDiscount) {
        when(promotionFeignClient.calculatePromotion(any()))
                .thenReturn(Result.success(promotionDiscount));
    }

    /**
     * 设置优惠券核销 Mock（返回指定优惠券优惠金额）
     */
    private void mockCouponUseSuccess(BigDecimal couponDiscount) {
        when(couponFeignClient.useCoupon(any(CouponUseDTO.class)))
                .thenReturn(Result.success(couponDiscount));
    }

    /**
     * 设置优惠券核销 Mock（返回失败）
     */
    private void mockCouponUseFail(String message) {
        when(couponFeignClient.useCoupon(any(CouponUseDTO.class)))
                .thenReturn(Result.fail(400, message));
    }

    /**
     * 查询数据库中订单数量
     */
    private int countOrdersInDb() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class);
    }

    /**
     * 从数据库查询订单主表
     */
    private OrderInfo getOrderFromDb() {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM order_info WHERE order_no = ?",
                (rs, rowNum) -> {
                    OrderInfo o = new OrderInfo();
                    o.setId(rs.getLong("id"));
                    o.setOrderNo(rs.getString("order_no"));
                    o.setUserId(rs.getLong("user_id"));
                    o.setMerchantId(rs.getLong("merchant_id"));
                    o.setTotalAmount(rs.getBigDecimal("total_amount"));
                    o.setPayAmount(rs.getBigDecimal("pay_amount"));
                    o.setFreightAmount(rs.getBigDecimal("freight_amount"));
                    o.setDiscountAmount(rs.getBigDecimal("discount_amount"));
                    o.setPromotionDiscount(rs.getBigDecimal("promotion_discount"));
                    o.setStatus(rs.getInt("status"));
                    return o;
                },
                ORDER_NO);
    }

    // ==================== 1. 优惠券叠加金额计算测试 ====================

    @Nested
    @DisplayName("1. 优惠券叠加金额计算")
    class CouponStackCalcTest {

        @Test
        @DisplayName("满减+优惠券叠加：300元 → 满200减30 → 270元 → 满100减20券 → 实付250元")
        void createOrder_promotionAndCoupon_stackCalcCorrect() {
            // 订单总金额：100元 * 3件 = 300元
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            // 满减优惠：满200减30 = 30元
            mockPromotion(new BigDecimal("30.00"));
            // 优惠券优惠：满100减20 = 20元（基于满减后金额270判断门槛）
            mockCouponUseSuccess(new BigDecimal("20.00"));

            OrderDetailVO result = orderService.createOrder(USER_ID, buildCreateDTO(3, USER_COUPON_ID));

            assertThat(result).isNotNull();
            // 验证数据库持久化
            assertThat(countOrdersInDb()).isEqualTo(1);
            OrderInfo order = getOrderFromDb();
            // 总金额 = 300
            assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
            // 实付 = 300 - 30 - 20 = 250
            assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
            // 总优惠 = 30 + 20 = 50
            assertThat(order.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            // 满减优惠 = 30
            assertThat(order.getPromotionDiscount()).isEqualByComparingTo(new BigDecimal("30.00"));
            // 验证：锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("仅优惠券无满减：300元 → 用满100减50券 → 实付250元")
        void createOrder_onlyCoupon_noPromotion() {
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            // 无满减（满减为0）
            mockPromotion(BigDecimal.ZERO);
            // 优惠券优惠 50
            mockCouponUseSuccess(new BigDecimal("50.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(3, USER_COUPON_ID));

            OrderInfo order = getOrderFromDb();
            // 实付 = 300 - 0 - 50 = 250
            assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
            // 总优惠 = 0 + 50 = 50
            assertThat(order.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            // 满减优惠 = 0
            assertThat(order.getPromotionDiscount()).isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("仅满减无优惠券：300元 → 满200减30 → 实付270元，couponFeignClient 未被调用")
        void createOrder_onlyPromotion_noCoupon() {
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(new BigDecimal("30.00"));
            // 不使用优惠券（userCouponId = null）

            orderService.createOrder(USER_ID, buildCreateDTO(3));

            OrderInfo order = getOrderFromDb();
            // 实付 = 300 - 30 = 270
            assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("270.00"));
            // 总优惠 = 30 + 0 = 30
            assertThat(order.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            // 满减优惠 = 30
            assertThat(order.getPromotionDiscount()).isEqualByComparingTo(new BigDecimal("30.00"));
            // 验证：优惠券核销接口从未被调用
            verify(couponFeignClient, never()).useCoupon(any(CouponUseDTO.class));
        }

        @Test
        @DisplayName("无满减无优惠券：300元 → 实付300元，所有优惠字段为0")
        void createOrder_noPromotionNoCoupon_allZeroDiscount() {
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(BigDecimal.ZERO);

            orderService.createOrder(USER_ID, buildCreateDTO(3));

            OrderInfo order = getOrderFromDb();
            // 实付 = 300 - 0 - 0 = 300
            assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
            // 总优惠 = 0
            assertThat(order.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
            // 满减优惠 = 0
            assertThat(order.getPromotionDiscount()).isEqualByComparingTo(new BigDecimal("0.00"));
            // 验证：优惠券核销接口从未被调用
            verify(couponFeignClient, never()).useCoupon(any(CouponUseDTO.class));
        }
    }

    // ==================== 2. 优惠券门槛判断测试 ====================

    @Nested
    @DisplayName("2. 优惠券门槛基于满减后金额判断")
    class CouponThresholdTest {

        @Test
        @DisplayName("CouponUseDTO 传给 Feign 的 orderAmount 是满减后金额（不是订单原金额）")
        void createOrder_couponUseDTO_orderAmountIsAfterPromotion() {
            // 订单总金额：100元 * 3件 = 300元
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            // 满减优惠：30元
            mockPromotion(new BigDecimal("30.00"));
            // 优惠券优惠：20元
            mockCouponUseSuccess(new BigDecimal("20.00"));

            orderService.createOrder(USER_ID, buildCreateDTO(3, USER_COUPON_ID));

            // 验证：传给 useCoupon 的 CouponUseDTO 中 orderAmount 应该是 270（300-30），不是 300
            org.mockito.ArgumentCaptor<CouponUseDTO> captor =
                    org.mockito.ArgumentCaptor.forClass(CouponUseDTO.class);
            verify(couponFeignClient).useCoupon(captor.capture());
            CouponUseDTO capturedDTO = captor.getValue();
            assertThat(capturedDTO.getUserId()).isEqualTo(USER_ID);
            assertThat(capturedDTO.getUserCouponId()).isEqualTo(USER_COUPON_ID);
            assertThat(capturedDTO.getOrderNo()).isEqualTo(ORDER_NO);
            // 关键断言：orderAmount 是满减后金额
            assertThat(capturedDTO.getOrderAmount()).isEqualByComparingTo(new BigDecimal("270.00"));
        }

        @Test
        @DisplayName("满减后金额不满足优惠券门槛：shop-user 返回失败 → 订单回滚")
        void createOrder_afterPromotionBelowCouponThreshold_rollback() {
            // 订单总金额：100元 * 2件 = 200元
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            // 满减优惠：100元（满200减100）
            mockPromotion(new BigDecimal("100.00"));
            // 满减后金额 = 200 - 100 = 100，但用户用了满300减50的券，shop-user 拒绝
            mockCouponUseFail("订单金额不满足优惠券使用门槛");

            // 下单应抛出 BusinessException
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2, USER_COUPON_ID)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("优惠券核销失败")
                    .hasMessageContaining("订单金额不满足优惠券使用门槛");

            // 验证：订单未写入数据库（事务回滚）
            assertThat(countOrdersInDb()).isEqualTo(0);
            // 验证：分布式锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }
    }

    // ==================== 3. 优惠券核销失败回滚测试 ====================

    @Nested
    @DisplayName("3. 优惠券核销失败事务回滚")
    class CouponRollbackTest {

        @Test
        @DisplayName("优惠券核销抛异常：订单未写入，库存扣减已调用（事务边界在 Service 层）")
        void createOrder_couponUseThrowsException_orderNotPersisted() {
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(BigDecimal.ZERO);
            // 优惠券核销抛异常（模拟 Feign 调用超时）
            when(couponFeignClient.useCoupon(any(CouponUseDTO.class)))
                    .thenThrow(new RuntimeException("Feign 调用超时"));

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2, USER_COUPON_ID)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Feign 调用超时");

            // 验证：订单未写入数据库
            assertThat(countOrdersInDb()).isEqualTo(0);
            // 验证：锁已释放
            assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
        }

        @Test
        @DisplayName("优惠券核销返回 null：抛 BusinessException 订单未写入")
        void createOrder_couponUseReturnNull_rollback() {
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(BigDecimal.ZERO);
            // 优惠券核销返回 null（服务不可用）
            when(couponFeignClient.useCoupon(any(CouponUseDTO.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2, USER_COUPON_ID)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("优惠券核销失败")
                    .hasMessageContaining("优惠券服务不可用");

            // 验证：订单未写入
            assertThat(countOrdersInDb()).isEqualTo(0);
        }

        @Test
        @DisplayName("优惠券核销返回失败 Result：抛 BusinessException 包含错误消息")
        void createOrder_couponUseReturnFailResult_rollback() {
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(BigDecimal.ZERO);
            // 优惠券已被使用
            mockCouponUseFail("优惠券已被使用");

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, buildCreateDTO(2, USER_COUPON_ID)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("优惠券核销失败")
                    .hasMessageContaining("优惠券已被使用");

            // 验证：订单未写入
            assertThat(countOrdersInDb()).isEqualTo(0);
        }
    }

    // ==================== 4. 取消订单时优惠券回退测试 ====================

    @Nested
    @DisplayName("4. 取消订单时优惠券回退")
    class CancelOrderCouponRollbackTest {

        @Test
        @DisplayName("取消订单使用了优惠券：rollbackCoupon Feign 被调用")
        void cancelOrder_withCoupon_rollbackCouponCalled() {
            // 先下单：300元 - 满200减30 - 满100减20 = 实付250
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(new BigDecimal("30.00"));
            mockCouponUseSuccess(new BigDecimal("20.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(3, USER_COUPON_ID));
            assertThat(countOrdersInDb()).isEqualTo(1);

            // 重置 Mock，准备取消订单
            org.mockito.Mockito.reset(couponFeignClient, productFeignClient);
            // 取消订单时回退库存
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(Result.success(null));
            // 回退优惠券成功
            when(couponFeignClient.rollbackCoupon(eq(ORDER_NO)))
                    .thenReturn(Result.success(null));

            // 取消订单
            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：订单状态变为已取消
            OrderInfo order = getOrderFromDb();
            assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：优惠券回退 Feign 被调用
            verify(couponFeignClient).rollbackCoupon(eq(ORDER_NO));
        }

        @Test
        @DisplayName("取消订单未使用优惠券：rollbackCoupon 从未被调用")
        void cancelOrder_withoutCoupon_rollbackCouponNeverCalled() {
            // 先下单：不使用优惠券
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(BigDecimal.ZERO);
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(3));

            // 重置 Mock
            org.mockito.Mockito.reset(couponFeignClient, productFeignClient);
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(Result.success(null));

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：订单状态变为已取消
            OrderInfo order = getOrderFromDb();
            assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：优惠券回退接口从未被调用（因为没有使用优惠券）
            verify(couponFeignClient, never()).rollbackCoupon(anyString());
        }

        @Test
        @DisplayName("取消订单时优惠券回退失败：不影响取消订单主流程")
        void cancelOrder_couponRollbackFail_stillCancelSuccess() {
            // 先下单：使用优惠券
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(BigDecimal.ZERO);
            mockCouponUseSuccess(new BigDecimal("50.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(3, USER_COUPON_ID));

            // 重置 Mock
            org.mockito.Mockito.reset(couponFeignClient, productFeignClient);
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(Result.success(null));
            // 优惠券回退失败
            when(couponFeignClient.rollbackCoupon(eq(ORDER_NO)))
                    .thenReturn(Result.fail(500, "优惠券服务异常"));

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            // 取消订单不应抛异常（优惠券回退是弱依赖）
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：订单状态变为已取消（即使优惠券回退失败）
            OrderInfo order = getOrderFromDb();
            assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            // 验证：优惠券回退 Feign 被调用了（虽然失败）
            verify(couponFeignClient).rollbackCoupon(eq(ORDER_NO));
        }

        @Test
        @DisplayName("取消订单时优惠券回退抛异常：不影响取消订单主流程")
        void cancelOrder_couponRollbackThrowsException_stillCancelSuccess() {
            // 先下单：使用优惠券
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            mockPromotion(BigDecimal.ZERO);
            mockCouponUseSuccess(new BigDecimal("50.00"));
            OrderDetailVO created = orderService.createOrder(USER_ID, buildCreateDTO(3, USER_COUPON_ID));

            // 重置 Mock
            org.mockito.Mockito.reset(couponFeignClient, productFeignClient);
            when(productFeignClient.batchDeductStock(anyList(), eq(ORDER_NO)))
                    .thenReturn(Result.success(null));
            // 优惠券回退抛异常（网络超时）
            when(couponFeignClient.rollbackCoupon(eq(ORDER_NO)))
                    .thenThrow(new RuntimeException("网络超时"));

            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setReason("不想买了");
            // 取消订单不应抛异常
            orderService.cancelOrder(USER_ID, created.getId(), cancelDTO);

            // 验证：订单状态变为已取消
            OrderInfo order = getOrderFromDb();
            assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
            verify(couponFeignClient).rollbackCoupon(eq(ORDER_NO));
        }
    }

    // ==================== 5. 优惠金额边界测试 ====================

    @Nested
    @DisplayName("5. 优惠金额边界场景")
    class CouponEdgeCaseTest {

        @Test
        @DisplayName("满减优惠等于订单总额：满减后金额为0，优惠券门槛校验传0元")
        void createOrder_promotionEqualsTotalAmount_couponUseDTOHasZeroOrderAmount() {
            // 订单总金额：100元 * 2件 = 200元
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            // 满减优惠：200元（等于订单总额，极端场景）
            mockPromotion(new BigDecimal("200.00"));
            // 优惠券优惠：0元（因为满减后金额为0，不满足任何优惠券门槛）
            mockCouponUseSuccess(BigDecimal.ZERO);

            orderService.createOrder(USER_ID, buildCreateDTO(2, USER_COUPON_ID));

            OrderInfo order = getOrderFromDb();
            // 实付 = 200 - 200 - 0 = 0
            assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
            // 总优惠 = 200 + 0 = 200
            assertThat(order.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

            // 验证：传给 useCoupon 的 orderAmount 应该是 0
            org.mockito.ArgumentCaptor<CouponUseDTO> captor =
                    org.mockito.ArgumentCaptor.forClass(CouponUseDTO.class);
            verify(couponFeignClient).useCoupon(captor.capture());
            assertThat(captor.getValue().getOrderAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("满减优惠大于订单总额（异常场景）：满减后金额为负数，传给优惠券的 orderAmount 为负")
        void createOrder_promotionGreaterThanTotalAmount_negativeOrderAmount() {
            // 订单总金额：100元 * 2件 = 200元
            mockCreateOrderBase(100, new BigDecimal("100.00"));
            // 满减优惠：250元（大于订单总额，异常场景，由 shop-merchant 保证不出现）
            mockPromotion(new BigDecimal("250.00"));
            // 优惠券优惠：0元
            mockCouponUseSuccess(BigDecimal.ZERO);

            orderService.createOrder(USER_ID, buildCreateDTO(2, USER_COUPON_ID));

            OrderInfo order = getOrderFromDb();
            // 实付 = 200 - 250 - 0 = -50（负数，生产环境应由 shop-merchant 保证不出现，这里验证计算逻辑）
            assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("-50.00"));

            // 验证：传给 useCoupon 的 orderAmount 应该是 -50
            org.mockito.ArgumentCaptor<CouponUseDTO> captor =
                    org.mockito.ArgumentCaptor.forClass(CouponUseDTO.class);
            verify(couponFeignClient).useCoupon(captor.capture());
            assertThat(captor.getValue().getOrderAmount()).isEqualByComparingTo(new BigDecimal("-50.00"));
        }
    }
}
