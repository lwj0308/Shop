package com.shop.order.mapper;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.shop.model.order.entity.OrderInfo;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderInfoMapper DAO测试（B-S-08）
 * <p>
 * 小白理解：DAO测试是"真的执行SQL"，但不用真的连MySQL，而是用H2内存数据库。
 * H2是一个"跑在内存里的数据库"，启动快、用完就销毁，特别适合测试。
 * 我们把H2设置成"MySQL兼容模式"，这样MySQL的SQL语法（比如CURDATE、DATE）都能用。
 * </p>
 * <p>
 * 测试重点：
 * 1. BaseMapper的基本CRUD（insert、selectById、update、deleteById）
 * 2. 自定义SQL查询（聚合统计、分组查询）
 * 3. 逻辑删除（@TableLogic注解，deleted=1的记录不会被查到）
 * 4. MyBatis-Plus的自动填充（@TableField fill）
 * </p>
 * <p>
 * 注意：
 * 1. 不启动SpringBoot应用，手动创建H2数据源和MyBatis-Plus的SqlSessionFactory。
 *    这样测试跑得飞快，不会因为Nacos、Redis等中间件没启动而报错。
 * 2. H2使用MySQL兼容模式（MODE=MySQL），支持CURDATE()、DATE()等MySQL函数。
 * 3. 每个测试方法执行后会清空表数据，保证测试之间互不影响。
 * 4. @TableField(fill)的自动填充需要MetaObjectHandler，这里没配，所以手动设置时间。
 * </p>
 */
@DisplayName("OrderInfoMapper DAO测试（H2内存数据库）")
class OrderInfoMapperTest {

    /** 数据库连接（H2内存数据库） */
    private static EmbeddedDatabase dataSource;

    /** MyBatis的SqlSessionFactory，用来创建数据库会话 */
    private static SqlSessionFactory sqlSessionFactory;

    /** 数据库会话（每个测试方法用一个独立的会话） */
    private SqlSession sqlSession;

    /** 被测试的Mapper */
    private OrderInfoMapper orderInfoMapper;

    /** JdbcTemplate：用来执行原生SQL（清理测试数据用） */
    private JdbcTemplate jdbcTemplate;

    /** 测试用的商家ID */
    private static final Long MERCHANT_ID = 1001L;

    /** 测试用的用户ID */
    private static final Long USER_ID = 2001L;

    /**
     * 所有测试方法执行前的准备工作（只执行一次）
     * <p>
     * 创建H2内存数据库（MySQL兼容模式），执行建表脚本，
     * 创建MyBatis-Plus的SqlSessionFactory。
     * </p>
     */
    @BeforeAll
    static void initDatabase() throws Exception {
        // 创建H2内存数据库，启用MySQL兼容模式
        // 小白理解：MODE=MySQL让H2支持MySQL的函数和语法，比如CURDATE()、DATE()
        // DB_CLOSE_DELAY=-1表示JVM关闭前不销毁数据库，方便多个测试方法共享
        dataSource = (EmbeddedDatabase) new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
                .addScript("classpath:schema-h2.sql")
                .build();

        // 创建MyBatis-Plus的SqlSessionFactory
        // 小白理解：MybatisSqlSessionFactoryBean是MyBatis-Plus提供的工厂，
        // 比原生MyBatis多了逻辑删除、自动填充等功能
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        sqlSessionFactory = factoryBean.getObject();

        // 手动注册Mapper接口（不启动Spring时需要手动注册，否则Mapper不会被MyBatis-Plus识别）
        // 小白理解：正常Spring Boot项目用@MapperScan自动扫描注册，
        // 这里没有Spring，所以要手动告诉MyBatis-Plus"有这个Mapper接口"
        sqlSessionFactory.getConfiguration().addMapper(OrderInfoMapper.class);
    }

    /**
     * 每个测试方法执行前的准备工作
     * <p>
     * 打开数据库会话，获取Mapper实例。
     * </p>
     */
    @BeforeEach
    void setUp() {
        // autoCommit=true：每条SQL执行后自动提交，方便测试
        sqlSession = sqlSessionFactory.openSession(true);
        orderInfoMapper = sqlSession.getMapper(OrderInfoMapper.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 每个测试方法执行后的清理工作
     * <p>
     * 清空order_info表中的所有数据，保证每个测试方法都在干净的数据上运行。
     * 使用原生SQL删除（绕过逻辑删除），确保彻底清空。
     * </p>
     */
    @AfterEach
    void tearDown() {
        // 用JdbcTemplate执行原生DELETE，绕过MyBatis-Plus的逻辑删除
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("DELETE FROM order_info");
        }
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    /**
     * 创建一个测试用的订单对象
     * <p>
     * 工具方法，减少重复代码。创建一个基本的OrderInfo对象，
     * 测试时可以根据需要修改字段。
     * </p>
     *
     * @param orderNo   订单号
     * @param status    订单状态
     * @param payAmount 实付金额
     * @return 订单对象
     */
    private OrderInfo createOrder(String orderNo, int status, BigDecimal payAmount) {
        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setUserId(USER_ID);
        order.setMerchantId(MERCHANT_ID);
        order.setTotalAmount(payAmount);
        order.setPayAmount(payAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPromotionDiscount(BigDecimal.ZERO);
        order.setOrderType(1);
        order.setIsReviewed(0);
        order.setStatus(status);
        // 手动设置时间（因为测试环境没配MetaObjectHandler，自动填充不生效）
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        return order;
    }

    // ==================== 1. BaseMapper CRUD 测试 ====================

    /**
     * BaseMapper基本增删改查测试组
     * <p>
     * 测试MyBatis-Plus自动提供的CRUD方法，不需要写SQL。
     * </p>
     */
    @Nested
    @DisplayName("BaseMapper CRUD - 基本增删改查")
    class CrudTest {

        /**
         * 测试插入订单
         * <p>
         * 场景：插入一条订单，应返回影响行数1，且ID被自动生成
         * </p>
         */
        @Test
        @DisplayName("插入订单 - 返回1且ID自动生成")
        void insert_Success() {
            OrderInfo order = createOrder("ORDER001", 0, new BigDecimal("99.99"));

            int result = orderInfoMapper.insert(order);

            assertThat(result).isEqualTo(1);
            // ID由雪花算法自动生成，不应为null
            assertThat(order.getId()).isNotNull();
        }

        /**
         * 测试根据ID查询订单
         * <p>
         * 场景：插入订单后，根据ID查询，应返回相同的订单
         * </p>
         */
        @Test
        @DisplayName("根据ID查询 - 返回插入的订单")
        void selectById_Success() {
            OrderInfo order = createOrder("ORDER002", 2, new BigDecimal("199.00"));
            orderInfoMapper.insert(order);

            OrderInfo found = orderInfoMapper.selectById(order.getId());

            assertThat(found).isNotNull();
            assertThat(found.getOrderNo()).isEqualTo("ORDER002");
            assertThat(found.getStatus()).isEqualTo(2);
            assertThat(found.getPayAmount()).isEqualByComparingTo("199.00");
        }

        /**
         * 测试根据ID查询不存在的订单
         * <p>
         * 场景：查询一个不存在的ID，应返回null
         * </p>
         */
        @Test
        @DisplayName("查询不存在的ID - 返回null")
        void selectById_NotFound() {
            OrderInfo found = orderInfoMapper.selectById(999999L);

            assertThat(found).isNull();
        }

        /**
         * 测试更新订单状态
         * <p>
         * 场景：插入订单后，更新状态为"已发货"(3)，验证更新成功
         * </p>
         */
        @Test
        @DisplayName("更新订单状态 - 更新成功")
        void update_Success() {
            OrderInfo order = createOrder("ORDER003", 0, new BigDecimal("50.00"));
            orderInfoMapper.insert(order);

            // 更新状态为已发货
            order.setStatus(3);
            int result = orderInfoMapper.updateById(order);

            assertThat(result).isEqualTo(1);

            // 重新查询验证
            OrderInfo updated = orderInfoMapper.selectById(order.getId());
            assertThat(updated.getStatus()).isEqualTo(3);
        }

        /**
         * 测试逻辑删除
         * <p>
         * 小白理解：MyBatis-Plus的逻辑删除不是真的从数据库删数据，
         * 而是把deleted字段从0改成1。之后查询时自动加条件 WHERE deleted = 0，
         * 所以被"删除"的数据就查不到了。
         * </p>
         * <p>
         * 场景：删除订单后，selectById查不到（但数据还在表里）
         * </p>
         */
        @Test
        @DisplayName("逻辑删除 - 删除后查不到")
        void deleteById_LogicalDelete() {
            OrderInfo order = createOrder("ORDER004", 0, new BigDecimal("30.00"));
            orderInfoMapper.insert(order);
            Long orderId = order.getId();

            // 执行删除（逻辑删除）
            int result = orderInfoMapper.deleteById(orderId);

            assertThat(result).isEqualTo(1);

            // 通过MyBatis-Plus查询，应该查不到（自动过滤deleted=1）
            OrderInfo found = orderInfoMapper.selectById(orderId);
            assertThat(found).isNull();
        }
    }

    // ==================== 2. 聚合统计查询测试 ====================

    /**
     * 聚合统计查询测试组
     * <p>
     * 测试OrderInfoMapper中自定义的统计SQL，这些SQL用于商家仪表盘和数据中心。
     * </p>
     */
    @Nested
    @DisplayName("聚合统计查询 - 商家仪表盘统计")
    class StatisticsTest {

        /**
         * 测试统计商家今日销售额
         * <p>
         * 场景：插入3条今日订单（2条已付款、1条待付款），应只统计已付款的金额
         * </p>
         */
        @Test
        @DisplayName("今日销售额 - 只统计已付款订单(status>=2)")
        void sumTodaySales_Success() {
            // 插入2条已付款订单
            orderInfoMapper.insert(createOrder("S001", 2, new BigDecimal("100.00")));
            orderInfoMapper.insert(createOrder("S002", 5, new BigDecimal("200.00")));
            // 插入1条待付款订单（不应计入销售额）
            orderInfoMapper.insert(createOrder("S003", 0, new BigDecimal("999.00")));

            BigDecimal todaySales = orderInfoMapper.sumTodaySales(MERCHANT_ID);

            // 100 + 200 = 300
            assertThat(todaySales).isEqualByComparingTo("300.00");
        }

        /**
         * 测试今日销售额 - 无数据
         * <p>
         * 场景：没有已付款订单，应返回0
         * </p>
         */
        @Test
        @DisplayName("今日销售额 - 无数据返回0")
        void sumTodaySales_NoData() {
            BigDecimal todaySales = orderInfoMapper.sumTodaySales(MERCHANT_ID);

            assertThat(todaySales).isEqualByComparingTo("0");
        }

        /**
         * 测试统计商家今日订单数
         * <p>
         * 场景：插入3条今日订单（各种状态），应返回3
         * </p>
         */
        @Test
        @DisplayName("今日订单数 - 统计所有状态")
        void countTodayOrders_Success() {
            orderInfoMapper.insert(createOrder("C001", 0, new BigDecimal("10.00")));
            orderInfoMapper.insert(createOrder("C002", 2, new BigDecimal("20.00")));
            orderInfoMapper.insert(createOrder("C003", 5, new BigDecimal("30.00")));

            Long count = orderInfoMapper.countTodayOrders(MERCHANT_ID);

            assertThat(count).isEqualTo(3L);
        }

        /**
         * 测试统计待发货订单数
         * <p>
         * 场景：插入3条订单（1条待发货status=2、2条其他状态），应返回1
         * </p>
         */
        @Test
        @DisplayName("待发货订单数 - 只统计status=2")
        void countPendingShip_Success() {
            orderInfoMapper.insert(createOrder("P001", 0, new BigDecimal("10.00")));  // 待付款
            orderInfoMapper.insert(createOrder("P002", 2, new BigDecimal("20.00")));  // 待发货
            orderInfoMapper.insert(createOrder("P003", 3, new BigDecimal("30.00")));  // 运输中

            Long count = orderInfoMapper.countPendingShip(MERCHANT_ID);

            assertThat(count).isEqualTo(1L);
        }

        /**
         * 测试统计全平台今日销售额
         * <p>
         * 场景：插入不同商家的订单，应统计所有商家的已付款金额
         * </p>
         */
        @Test
        @DisplayName("全平台今日销售额 - 统计所有商家")
        void sumTodaySalesAll_Success() {
            // 商家1的订单
            OrderInfo o1 = createOrder("A001", 2, new BigDecimal("100.00"));
            orderInfoMapper.insert(o1);
            // 商家2的订单
            OrderInfo o2 = createOrder("A002", 3, new BigDecimal("200.00"));
            o2.setMerchantId(2002L);
            orderInfoMapper.insert(o2);

            BigDecimal totalSales = orderInfoMapper.sumTodaySalesAll();

            // 100 + 200 = 300
            assertThat(totalSales).isEqualByComparingTo("300.00");
        }

        /**
         * 测试统计全平台今日订单数
         * <p>
         * 场景：插入不同商家的订单，应统计所有商家的订单数
         * </p>
         */
        @Test
        @DisplayName("全平台今日订单数 - 统计所有商家")
        void countTodayOrdersAll_Success() {
            orderInfoMapper.insert(createOrder("ALL001", 0, new BigDecimal("10.00")));

            OrderInfo otherMerchantOrder = createOrder("ALL002", 2, new BigDecimal("20.00"));
            otherMerchantOrder.setMerchantId(2002L);
            orderInfoMapper.insert(otherMerchantOrder);

            Long count = orderInfoMapper.countTodayOrdersAll();

            assertThat(count).isEqualTo(2L);
        }
    }

    // ==================== 3. 时间范围查询测试 ====================

    /**
     * 时间范围查询测试组
     * <p>
     * 测试指定时间范围内的统计查询，用于数据中心的数据分析。
     * </p>
     */
    @Nested
    @DisplayName("时间范围查询 - 数据中心统计")
    class RangeQueryTest {

        /**
         * 测试统计指定时间范围内的销售额
         * <p>
         * 场景：插入3条订单（2条在范围内、1条在范围外），应只统计范围内的已付款金额
         * </p>
         */
        @Test
        @DisplayName("时间范围销售额 - 只统计范围内的已付款订单")
        void sumSalesByRange_Success() {
            // 在范围内的订单（create_time是今天，范围是昨天到明天）
            orderInfoMapper.insert(createOrder("R001", 2, new BigDecimal("100.00")));
            orderInfoMapper.insert(createOrder("R002", 5, new BigDecimal("200.00")));

            // 查询范围：昨天到明天
            LocalDateTime startDate = LocalDateTime.now().minusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(1);

            BigDecimal sales = orderInfoMapper.sumSalesByRange(MERCHANT_ID, startDate, endDate);

            // 100 + 200 = 300
            assertThat(sales).isEqualByComparingTo("300.00");
        }

        /**
         * 测试统计指定时间范围内的订单数
         * <p>
         * 场景：插入2条在范围内的订单，应返回2
         * </p>
         */
        @Test
        @DisplayName("时间范围订单数 - 统计范围内所有状态")
        void countOrdersByRange_Success() {
            orderInfoMapper.insert(createOrder("R003", 0, new BigDecimal("10.00")));
            orderInfoMapper.insert(createOrder("R004", 2, new BigDecimal("20.00")));

            LocalDateTime startDate = LocalDateTime.now().minusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(1);

            Long count = orderInfoMapper.countOrdersByRange(MERCHANT_ID, startDate, endDate);

            assertThat(count).isEqualTo(2L);
        }

        /**
         * 测试统计指定时间范围内的退款订单数
         * <p>
         * 场景：插入4条订单（1条退款中status=6、1条已退款status=7、2条其他状态），
         * 应只返回退款订单数2
         * </p>
         */
        @Test
        @DisplayName("时间范围退款订单数 - 统计status in (6,7)")
        void countRefundOrdersByRange_Success() {
            orderInfoMapper.insert(createOrder("RF001", 2, new BigDecimal("10.00")));  // 待发货
            orderInfoMapper.insert(createOrder("RF002", 6, new BigDecimal("20.00")));  // 退款中
            orderInfoMapper.insert(createOrder("RF003", 7, new BigDecimal("30.00")));  // 已退款
            orderInfoMapper.insert(createOrder("RF004", 5, new BigDecimal("40.00")));  // 已完成

            LocalDateTime startDate = LocalDateTime.now().minusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(1);

            Long count = orderInfoMapper.countRefundOrdersByRange(MERCHANT_ID, startDate, endDate);

            assertThat(count).isEqualTo(2L);
        }

        /**
         * 测试查询销售趋势
         * <p>
         * 场景：插入多条订单，应按日期分组返回销售数据
         * </p>
         */
        @Test
        @DisplayName("销售趋势 - 按日期分组返回")
        void selectSalesTrend_Success() {
            // 插入今天的已付款订单
            orderInfoMapper.insert(createOrder("T001", 2, new BigDecimal("100.00")));
            orderInfoMapper.insert(createOrder("T002", 3, new BigDecimal("200.00")));

            LocalDateTime startDate = LocalDateTime.now().minusDays(1);

            List<Map<String, Object>> trend = orderInfoMapper.selectSalesTrend(MERCHANT_ID, startDate);

            // 应至少有1条数据（今天的）
            assertThat(trend).isNotEmpty();
            // 每条数据应包含date和amount字段
            Map<String, Object> firstRow = trend.get(0);
            assertThat(firstRow).containsKeys("date", "amount");
            // 今天的金额应该是 100 + 200 = 300
            BigDecimal todayAmount = new BigDecimal(firstRow.get("amount").toString());
            assertThat(todayAmount).isEqualByComparingTo("300.00");
        }
    }

    // ==================== 4. 逻辑删除隔离测试 ====================

    /**
     * 逻辑删除隔离测试组
     * <p>
     * 验证逻辑删除的数据不会出现在统计查询中。
     * </p>
     */
    @Nested
    @DisplayName("逻辑删除隔离 - 已删除数据不参与统计")
    class LogicalDeleteTest {

        /**
         * 测试逻辑删除的订单不计入今日销售额
         * <p>
         * 场景：插入2条已付款订单，删除其中1条，今日销售额应只算未删除的
         * </p>
         */
        @Test
        @DisplayName("已删除订单不计入销售额")
        void sumTodaySales_ExcludesDeleted() {
            OrderInfo order1 = createOrder("D001", 2, new BigDecimal("100.00"));
            orderInfoMapper.insert(order1);

            OrderInfo order2 = createOrder("D002", 2, new BigDecimal("200.00"));
            orderInfoMapper.insert(order2);

            // 逻辑删除order1
            orderInfoMapper.deleteById(order1.getId());

            BigDecimal todaySales = orderInfoMapper.sumTodaySales(MERCHANT_ID);

            // 只有order2的200，order1被逻辑删除了不算
            assertThat(todaySales).isEqualByComparingTo("200.00");
        }

        /**
         * 测试逻辑删除的订单不计入今日订单数
         * <p>
         * 场景：插入3条订单，删除1条，今日订单数应返回2
         * </p>
         */
        @Test
        @DisplayName("已删除订单不计入订单数")
        void countTodayOrders_ExcludesDeleted() {
            OrderInfo order1 = createOrder("D003", 0, new BigDecimal("10.00"));
            orderInfoMapper.insert(order1);
            orderInfoMapper.insert(createOrder("D004", 2, new BigDecimal("20.00")));
            orderInfoMapper.insert(createOrder("D005", 3, new BigDecimal("30.00")));

            // 逻辑删除order1
            orderInfoMapper.deleteById(order1.getId());

            Long count = orderInfoMapper.countTodayOrders(MERCHANT_ID);

            // 3条插入，1条被逻辑删除，所以查到2条
            assertThat(count).isEqualTo(2L);
        }
    }
}
