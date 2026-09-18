package com.shop.order.consumer;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.result.Result;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.entity.OrderLog;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.enums.OrderTypeEnum;
import com.shop.model.seckill.dto.SeckillOrderDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.order.feign.ProductFeignClient;
import com.shop.order.feign.SeckillFeignClient;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderLogMapper;
import com.shop.order.util.OrderNoGenerator;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.Message;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀订单异步消费者 SeckillOrderConsumer 的单元测试
 * <p>
 * 这个测试类验证秒杀订单 MQ 消费者的核心逻辑：
 * - 幂等校验：MQ 重试时如果订单已存在，直接返回不重复创建
 * - 正常创建：查活动 → 写订单主表/明细/日志 → 扣商品库存 → 发延时消息
 * - 失败回退：创建失败时回退 Redis 秒杀库存，抛异常让 MQ 重试
 * - 弱依赖：延时消息发送失败不影响订单创建
 * </p>
 * <p>
 * 小白理解要点：
 * - SeckillOrderConsumer 是 RocketMQ 消费者，监听 topic_seckill_order
 * - 用户抢购成功（Redis库存扣减成功）后，会发一条 MQ 消息，消费者收到后异步创建订单
 * - 我们把 Mapper、Feign、MQ、Redis 都 Mock 掉，专注验证业务逻辑分支
 * </p>
 */
@DisplayName("秒杀订单异步消费者 SeckillOrderConsumer 单元测试")
@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerTest {

    // ==================== 测试常量 ====================

    private static final Long USER_ID = 1001L;          // 抢购用户ID
    private static final Long SECKILL_ID = 5001L;       // 秒杀活动ID
    private static final Long SKU_ID = 3001L;           // 秒杀商品SKU ID
    private static final Long PRODUCT_ID = 2001L;       // 秒杀商品ID
    private static final Long ORDER_ID = 8001L;         // 订单ID
    private static final String ORDER_NO = "1829384756102345678"; // 订单号
    private static final String STOCK_KEY = "seckill:stock:" + SECKILL_ID; // Redis 秒杀库存 key
    private static final String TOPIC_ORDER_TIMEOUT = "topic_order_timeout"; // 超时取消 Topic

    // ==================== 依赖的 Mock 对象 ====================

    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderLogMapper orderLogMapper;
    @Mock
    private OrderNoGenerator orderNoGenerator;
    @Mock
    private ProductFeignClient productFeignClient;
    @Mock
    private SeckillFeignClient seckillFeignClient;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeckillOrderConsumer seckillOrderConsumer;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：onMessage 里用了 LambdaQueryWrapper.eq(OrderInfo::getSeckillId, ...)，
     * 这些 Lambda 表达式需要 MyBatis-Plus 解析"OrderInfo 的 seckillId 字段对应数据库哪一列"。
     * 单元测试没有 Spring 环境，需要手动初始化，否则会报 "can not find lambda cache" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        TableInfoHelper.initTableInfo(assistant, OrderLog.class);
    }

    /**
     * 每个测试前设置 Redis opsForValue 桩（lenient 避免严格模式报错）
     */
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造秒杀订单 MQ 消息体
     */
    private SeckillOrderDTO buildDTO() {
        SeckillOrderDTO dto = new SeckillOrderDTO();
        dto.setSeckillId(SECKILL_ID);
        dto.setUserId(USER_ID);
        return dto;
    }

    /**
     * 构造一个秒杀活动实体（含秒杀价、SKU等信息）
     */
    private SeckillActivity buildActivity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(SECKILL_ID);
        activity.setProductId(PRODUCT_ID);
        activity.setSkuId(SKU_ID);
        activity.setMerchantId(1L);
        activity.setSeckillPrice(new BigDecimal("9.90"));
        return activity;
    }

    // ==================== 1. onMessage 消费消息测试 ====================

    @Nested
    @DisplayName("onMessage 消费秒杀下单消息")
    class OnMessageTest {

        /**
         * 幂等校验：MQ 重试时订单已存在，直接返回不重复创建
         */
        @Test
        @DisplayName("幂等校验：订单已存在时直接返回，不创建新订单")
        void onMessage_orderExists_returnDirectly() {
            // 场景：MQ 重试投递，order_info 表里已经有这个用户对这个活动的秒杀订单了
            // mock selectCount 返回 1（已存在1条订单）
            when(orderInfoMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

            // 执行
            seckillOrderConsumer.onMessage(buildDTO());

            // 验证：直接返回，不查询活动、不生成订单号、不 insert 任何东西
            verify(seckillFeignClient, never()).getSeckillById(anyLong());
            verify(orderNoGenerator, never()).generate();
            verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
            verify(orderItemMapper, never()).insert(any(OrderItem.class));
            verify(orderLogMapper, never()).insert(any(OrderLog.class));
            verify(productFeignClient, never()).deductStock(anyLong(), anyInt());
            verify(rocketMQTemplate, never()).syncSend(
                    anyString(), any(Message.class), anyLong(), anyInt());
        }

        /**
         * 正常创建秒杀订单：所有步骤都成功
         */
        @Test
        @DisplayName("正常创建：查活动→写订单/明细/日志→扣库存→发延时消息")
        void onMessage_normalCreate_success() {
            // 场景：幂等校验通过（selectCount=0），活动查询成功，所有 insert 和扣库存都成功
            when(orderInfoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.success(buildActivity()));
            when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
            // mock insert 订单主表时回填订单ID（模拟 MyBatis-Plus 主键回填）
            when(orderInfoMapper.insert(any(OrderInfo.class))).thenAnswer(invocation -> {
                OrderInfo o = invocation.getArgument(0);
                o.setId(ORDER_ID);
                return 1;
            });
            when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);
            when(productFeignClient.deductStock(SKU_ID, 1)).thenReturn(Result.success());

            // 执行
            seckillOrderConsumer.onMessage(buildDTO());

            // 验证：插入了订单主表，且关键字段正确（秒杀订单 type=2，价格=秒杀价，seckillId 已记录）
            ArgumentCaptor<OrderInfo> orderCaptor = ArgumentCaptor.forClass(OrderInfo.class);
            verify(orderInfoMapper).insert(orderCaptor.capture());
            OrderInfo savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(savedOrder.getUserId()).isEqualTo(USER_ID);
            assertThat(savedOrder.getOrderType()).isEqualTo(OrderTypeEnum.SECKILL.getCode());
            assertThat(savedOrder.getSeckillId()).isEqualTo(SECKILL_ID);
            assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("9.90"));
            assertThat(savedOrder.getPayAmount()).isEqualTo(new BigDecimal("9.90"));
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatusEnum.UNPAID.getCode());

            // 验证：插入了订单明细（数量1，价格=秒杀价）
            ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
            verify(orderItemMapper).insert(itemCaptor.capture());
            OrderItem savedItem = itemCaptor.getValue();
            assertThat(savedItem.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(savedItem.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(savedItem.getSkuId()).isEqualTo(SKU_ID);
            assertThat(savedItem.getQuantity()).isEqualTo(1);
            assertThat(savedItem.getPrice()).isEqualTo(new BigDecimal("9.90"));

            // 验证：记录了订单状态日志
            verify(orderLogMapper).insert(any(OrderLog.class));

            // 验证：扣减了商品实物库存
            verify(productFeignClient).deductStock(SKU_ID, 1);

            // 验证：发送了30分钟超时取消延时消息（延时等级16）
            verify(rocketMQTemplate).syncSend(
                    eq(TOPIC_ORDER_TIMEOUT), any(Message.class), eq(3000L), eq(16));

            // 验证：成功时不应该回退 Redis 库存
            verify(valueOperations, never()).increment(anyString());
        }

        /**
         * 失败场景：秒杀活动信息获取失败 → 抛异常 + 回退 Redis 库存
         */
        @Test
        @DisplayName("活动信息获取失败：抛异常 + 回退Redis秒杀库存")
        void onMessage_activityFetchFail_rollbackRedisStock() {
            // 场景：幂等校验通过，但 Feign 调用秒杀服务失败
            when(orderInfoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.fail("秒杀活动不存在"));

            // 执行并验证：应抛 RuntimeException（让 MQ 重试）
            assertThatThrownBy(() -> seckillOrderConsumer.onMessage(buildDTO()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("秒杀订单创建失败");

            // 验证：回退了 Redis 秒杀库存（increment 操作）
            verify(valueOperations).increment(STOCK_KEY);

            // 验证：没有生成订单和明细
            verify(orderNoGenerator, never()).generate();
            verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
            verify(orderItemMapper, never()).insert(any(OrderItem.class));
        }

        /**
         * 失败场景：扣减商品库存失败 → 抛异常 + 回退 Redis 库存
         */
        @Test
        @DisplayName("扣减商品库存失败：抛异常 + 回退Redis秒杀库存")
        void onMessage_deductStockFail_rollbackRedisStock() {
            // 场景：订单主表/明细/日志都写好了，但扣商品库存失败
            when(orderInfoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.success(buildActivity()));
            when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
            when(orderInfoMapper.insert(any(OrderInfo.class))).thenAnswer(invocation -> {
                OrderInfo o = invocation.getArgument(0);
                o.setId(ORDER_ID);
                return 1;
            });
            when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);
            // mock 扣库存返回失败
            when(productFeignClient.deductStock(SKU_ID, 1))
                    .thenReturn(Result.fail("商品库存不足"));

            // 执行并验证：应抛 RuntimeException
            assertThatThrownBy(() -> seckillOrderConsumer.onMessage(buildDTO()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("扣减商品库存失败");

            // 验证：回退了 Redis 秒杀库存
            verify(valueOperations).increment(STOCK_KEY);

            // 验证：没有发送延时消息（因为提前失败了）
            verify(rocketMQTemplate, never()).syncSend(
                    anyString(), any(Message.class), anyLong(), anyInt());
        }

        /**
         * 弱依赖场景：发送延时消息失败不影响订单创建
         */
        @Test
        @DisplayName("发送延时消息失败：不影响订单创建（try-catch吞掉异常）")
        void onMessage_sendTimeoutMessageFail_stillSucceeds() {
            // 场景：订单创建成功，扣库存成功，但发送延时消息抛异常
            when(orderInfoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.success(buildActivity()));
            when(orderNoGenerator.generate()).thenReturn(ORDER_NO);
            when(orderInfoMapper.insert(any(OrderInfo.class))).thenAnswer(invocation -> {
                OrderInfo o = invocation.getArgument(0);
                o.setId(ORDER_ID);
                return 1;
            });
            when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);
            when(orderLogMapper.insert(any(OrderLog.class))).thenReturn(1);
            when(productFeignClient.deductStock(SKU_ID, 1)).thenReturn(Result.success());
            // mock 发送延时消息抛异常
            org.mockito.Mockito.doThrow(new RuntimeException("MQ连接断开"))
                    .when(rocketMQTemplate).syncSend(
                            anyString(), any(Message.class), anyLong(), anyInt());

            // 执行：不应抛异常（延时消息失败被 try-catch 吞掉，只记日志）
            seckillOrderConsumer.onMessage(buildDTO());

            // 验证：订单和明细都已插入（主流程没被影响）
            verify(orderInfoMapper).insert(any(OrderInfo.class));
            verify(orderItemMapper).insert(any(OrderItem.class));
            // 验证：扣减了商品库存
            verify(productFeignClient).deductStock(SKU_ID, 1);
            // 验证：尝试过发送延时消息（虽然失败了）
            verify(rocketMQTemplate).syncSend(
                    eq(TOPIC_ORDER_TIMEOUT), any(Message.class), eq(3000L), eq(16));
            // 验证：没有回退 Redis 库存（订单创建是成功的）
            verify(valueOperations, never()).increment(anyString());
        }

        /**
         * 边界场景：Feign 返回 null（活动信息获取失败的一种特殊情况）
         */
        @Test
        @DisplayName("Feign返回null时：抛异常 + 回退Redis秒杀库存")
        void onMessage_feignReturnNull_rollbackRedisStock() {
            when(orderInfoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
            // mock Feign 返回 null
            when(seckillFeignClient.getSeckillById(SECKILL_ID)).thenReturn(null);

            assertThatThrownBy(() -> seckillOrderConsumer.onMessage(buildDTO()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("秒杀活动信息获取失败");

            // 验证：回退了 Redis 库存
            verify(valueOperations).increment(STOCK_KEY);
        }
    }
}
