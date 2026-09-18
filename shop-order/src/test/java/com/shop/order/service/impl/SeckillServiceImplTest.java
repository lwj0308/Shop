package com.shop.order.service.impl;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.seckill.dto.SeckillOrderDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.enums.SeckillStatusEnum;
import com.shop.order.feign.SeckillFeignClient;
import com.shop.order.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀抢购服务 SeckillServiceImpl 的单元测试
 * <p>
 * 这个测试类验证秒杀抢购的核心流程是否正确，包括：
 * - 活动校验（活动存在、状态进行中、时间窗口内）
 * - 用户抢购幂等锁（5秒内同一用户同一活动只能点一次）
 * - Lua 脚本扣减 Redis 秒杀库存（成功/库存不足/超限购）
 * - MQ 消息发送成功 / 失败回退库存
 * - Sentinel 限流 blockHandler / 降级 fallback 兜底
 * </p>
 * <p>
 * 小白理解要点：
 * - 我们把 Feign 客户端、Redis、RocketMQ、QueueService 都"假装"一下（Mock）
 * - 这样测试不需要真的连微服务、Redis、MQ，跑得又快又稳定
 * - 重点验证"各种场景下方法的返回值和调用次数对不对"
 * </p>
 */
@DisplayName("秒杀抢购服务 SeckillServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {

    // ==================== 测试常量 ====================

    /** 测试用用户ID */
    private static final Long USER_ID = 1001L;
    /** 测试用秒杀活动ID */
    private static final Long SECKILL_ID = 5001L;
    /** 秒杀库存 Redis key 前缀（和被测类常量保持一致） */
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    /** 抢购幂等锁 key 前缀（和被测类常量保持一致） */
    private static final String GRAB_LOCK_PREFIX = "idempotent:seckill:grab:";
    /** 秒杀下单 MQ Topic（和被测类常量保持一致） */
    private static final String TOPIC_SECKILL_ORDER = "topic_seckill_order";

    // ==================== 依赖的 Mock 对象 ====================

    /** 假装秒杀活动 Feign 客户端，模拟查询秒杀活动信息 */
    @Mock
    private SeckillFeignClient seckillFeignClient;

    /** 假装 Redis 模板，模拟 Lua 脚本扣库存 + 幂等锁 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 假装 Redis 字符串操作对象，opsForValue() 的返回值 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 假装 RocketMQ 消息模板，模拟发送异步下单消息 */
    @Mock
    private RocketMQTemplate rocketMQTemplate;

    /** 假装排队队列服务，模拟 Sentinel 限流时的排队兜底 */
    @Mock
    private QueueService queueService;

    /** 被测对象，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private SeckillServiceImpl seckillService;

    /**
     * 每个测试方法执行前的准备工作
     * <p>
     * 用 lenient() 设置 opsForValue() 的桩，是因为不是每个测试都会用到 Redis 字符串操作。
     * 严格模式下没用到会报"多余的桩"错误，lenient() 表示"宽松模式"，没用到也不报错。
     * </p>
     */
    @BeforeEach
    void setUp() {
        // 让 stringRedisTemplate.opsForValue() 返回我们的假 valueOperations
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个"进行中"的秒杀活动实体
     * <p>开始时间在过去1分钟，结束时间在未来1小时，状态为进行中(1)</p>
     *
     * @return 构造好的 SeckillActivity
     */
    private SeckillActivity buildActiveActivity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(SECKILL_ID);
        activity.setProductId(2001L);
        activity.setSkuId(3001L);
        activity.setMerchantId(1L);
        activity.setSeckillPrice(new BigDecimal("9.90"));
        activity.setOriginalPrice(new BigDecimal("19.90"));
        activity.setTotalCount(100);
        activity.setAvailableCount(50);
        activity.setLimitCount(1);
        activity.setStatus(SeckillStatusEnum.ACTIVE.getCode());
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        return activity;
    }

    // ==================== 1. executeSeckill 抢购主流程测试 ====================

    @Nested
    @DisplayName("executeSeckill 秒杀抢购主流程")
    class ExecuteSeckillTest {

        /**
         * 活动校验阶段失败的5个场景：Feign返回null / 失败 / 状态非进行中 / 未开始 / 已结束
         */
        @Nested
        @DisplayName("活动校验阶段")
        class ActivityCheckTest {

            @Test
            @DisplayName("Feign返回null时，应抛出业务异常")
            void executeSeckill_feignReturnNull_throwsException() {
                // 场景：Feign 调用返回 null（比如网络异常导致 Fallback 返回 null）
                when(seckillFeignClient.getSeckillById(SECKILL_ID)).thenReturn(null);

                // 执行并验证：应抛 BusinessException
                assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage("秒杀活动信息获取失败");

                // 验证：活动校验失败不应进入扣库存和发MQ流程
                verify(stringRedisTemplate, never()).execute(
                        any(), anyList(), any(Object[].class));
                verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
            }

            @Test
            @DisplayName("Feign返回失败时，应抛出业务异常并携带失败消息")
            void executeSeckill_feignReturnFail_throwsException() {
                // 场景：Feign 调用返回 code!=200 的失败结果
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.fail(ErrorCode.DATA_NOT_FOUND.getCode(), "秒杀活动不存在"));

                assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage("秒杀活动不存在");
            }

            @Test
            @DisplayName("活动状态非进行中时，应抛出业务异常")
            void executeSeckill_activityNotActive_throwsException() {
                // 场景：活动状态是"待生效(0)"，不是"进行中(1)"
                SeckillActivity activity = buildActiveActivity();
                activity.setStatus(SeckillStatusEnum.PENDING.getCode());
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.success(activity));

                assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage("秒杀活动未开始或已结束");
            }

            @Test
            @DisplayName("活动尚未开始时，应抛出业务异常")
            void executeSeckill_activityNotStarted_throwsException() {
                // 场景：活动开始时间在未来1小时，当前还没到点
                SeckillActivity activity = buildActiveActivity();
                activity.setStartTime(LocalDateTime.now().plusHours(1));
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.success(activity));

                assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage("秒杀活动尚未开始");
            }

            @Test
            @DisplayName("活动已结束时，应抛出业务异常")
            void executeSeckill_activityEnded_throwsException() {
                // 场景：活动结束时间在过去1小时
                SeckillActivity activity = buildActiveActivity();
                activity.setEndTime(LocalDateTime.now().minusHours(1));
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.success(activity));

                assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage("秒杀活动已结束");
            }
        }

        /**
         * 幂等锁阶段：5秒内同一用户同一活动重复点击
         */
        @Test
        @DisplayName("幂等锁拦截：setIfAbsent返回false时，应返回操作太频繁")
        void executeSeckill_grabLockFail_returnsTooFrequent() {
            // 场景：用户5秒内连续点击抢购，Redis setIfAbsent 返回 false 表示锁已存在
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.success(buildActiveActivity()));
            when(valueOperations.setIfAbsent(
                    eq(GRAB_LOCK_PREFIX + SECKILL_ID + ":" + USER_ID),
                    eq("1"), eq(5L), any()))
                    .thenReturn(false);

            // 执行
            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证：返回失败，消息是"操作太频繁，请稍后重试"
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("操作太频繁，请稍后重试");

            // 验证：幂等锁拦截后不应执行 Lua 脚本和发 MQ
            verify(stringRedisTemplate, never()).execute(
                    any(), anyList(), any(Object[].class));
            verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        }

        /**
         * Lua 脚本扣库存阶段：返回 -1（库存不足）/ -2（超限购）/ null / 未知值
         */
        @Nested
        @DisplayName("Lua扣库存阶段")
        class LuaDeductTest {

            @Test
            @DisplayName("Lua返回null时，按库存不足处理，返回商品已被抢完")
            void executeSeckill_luaReturnNull_returnsSoldOut() {
                // 场景：Lua 脚本返回 null（理论上不会发生，但代码做了防御）
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.success(buildActiveActivity()));
                when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                        .thenReturn(true);
                // mock Lua 脚本执行返回 null
                when(stringRedisTemplate.execute(
                        any(), anyList(), any(Object[].class))).thenReturn(null);

                Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).isEqualTo("手慢了，商品已经被抢完了");
                // 库存不足不应发 MQ
                verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
            }

            @Test
            @DisplayName("Lua返回-1时，库存不足，返回商品已被抢完")
            void executeSeckill_luaReturnNegative1_returnsSoldOut() {
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.success(buildActiveActivity()));
                when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                        .thenReturn(true);
                when(stringRedisTemplate.execute(
                        any(), anyList(), any(Object[].class))).thenReturn(-1L);

                Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).isEqualTo("手慢了，商品已经被抢完了");
                verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
            }

            @Test
            @DisplayName("Lua返回-2时，超过限购，返回已达到限购数量")
            void executeSeckill_luaReturnNegative2_returnsLimitExceeded() {
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.success(buildActiveActivity()));
                when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                        .thenReturn(true);
                when(stringRedisTemplate.execute(
                        any(), anyList(), any(Object[].class))).thenReturn(-2L);

                Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).isEqualTo("您已达到限购数量，无法再次购买");
                verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
            }

            @Test
            @DisplayName("Lua返回未知值时，按失败处理，返回秒杀失败请稍后重试")
            void executeSeckill_luaReturnUnknown_returnsFail() {
                when(seckillFeignClient.getSeckillById(SECKILL_ID))
                        .thenReturn(Result.success(buildActiveActivity()));
                when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                        .thenReturn(true);
                // 返回 99 这种未知值
                when(stringRedisTemplate.execute(
                        any(), anyList(), any(Object[].class))).thenReturn(99L);

                Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).isEqualTo("秒杀失败，请稍后重试");
                verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
            }
        }

        /**
         * 抢购成功场景：Lua 返回 1，MQ 发送成功
         */
        @Test
        @DisplayName("正常抢购成功：Lua返回1，MQ发送成功，返回抢购成功提示")
        void executeSeckill_success_returnsGrabSuccess() {
            // 场景：所有步骤都正常，抢购成功
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.success(buildActiveActivity()));
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(true);
            // mock Lua 扣库存成功，返回 1
            when(stringRedisTemplate.execute(
                    any(), anyList(), any(Object[].class))).thenReturn(1L);

            // 执行抢购
            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证返回值：code=200，message=抢购成功
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("抢购成功，正在创建订单");

            // 验证 MQ 消息已发送到秒杀下单 Topic
            verify(rocketMQTemplate).syncSend(eq(TOPIC_SECKILL_ORDER), any(Message.class));

            // 验证 MQ 消息体内容：seckillId 和 userId 正确
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Message<SeckillOrderDTO>> msgCaptor =
                    ArgumentCaptor.forClass(Message.class);
            verify(rocketMQTemplate).syncSend(eq(TOPIC_SECKILL_ORDER), msgCaptor.capture());
            SeckillOrderDTO payload = msgCaptor.getValue().getPayload();
            assertThat(payload.getSeckillId()).isEqualTo(SECKILL_ID);
            assertThat(payload.getUserId()).isEqualTo(USER_ID);
        }

        /**
         * MQ 发送失败场景：Lua 扣减成功但 MQ 发不出去，需要回退 Redis 库存
         */
        @Test
        @DisplayName("MQ发送失败时：应回退Redis库存，返回系统繁忙")
        void executeSeckill_mqSendFail_rollbackRedisStock() {
            // 场景：Lua 扣库存成功了，但 MQ 发送抛异常
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.success(buildActiveActivity()));
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(true);
            when(stringRedisTemplate.execute(
                    any(), anyList(), any(Object[].class))).thenReturn(1L);
            // mock MQ 发送抛异常
            doThrowOnSyncSend(new RuntimeException("MQ连接失败"));

            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证：返回系统繁忙
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");

            // 验证：调用了 Redis 库存回退（increment 操作）
            verify(valueOperations).increment(STOCK_KEY_PREFIX + SECKILL_ID);
        }

        /**
         * MQ 发送失败 + 库存回退也失败的场景：回退异常被吞掉，不影响主流程返回
         */
        @Test
        @DisplayName("MQ发送失败且库存回退也失败时：主流程仍返回系统繁忙（不抛异常）")
        void executeSeckill_mqFailAndRollbackFail_returnsSystemBusy() {
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.success(buildActiveActivity()));
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(true);
            when(stringRedisTemplate.execute(
                    any(), anyList(), any(Object[].class))).thenReturn(1L);
            doThrowOnSyncSend(new RuntimeException("MQ连接失败"));
            // mock Redis 回退也抛异常
            when(valueOperations.increment(STOCK_KEY_PREFIX + SECKILL_ID))
                    .thenThrow(new RuntimeException("Redis连接断开"));

            // 执行：不应抛异常（回退失败被 try-catch 吞掉，只记日志）
            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证：主流程仍正常返回系统繁忙
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");
        }

        /**
         * 辅助方法：让 rocketMQTemplate.syncSend 抛指定异常
         * <p>抽取出来避免重复代码</p>
         *
         * @param ex 要抛出的异常
         */
        private void doThrowOnSyncSend(RuntimeException ex) {
            org.mockito.Mockito.doThrow(ex).when(rocketMQTemplate)
                    .syncSend(anyString(), any(Message.class));
        }
    }

    // ==================== 2. Sentinel 兜底方法测试 ====================

    @Nested
    @DisplayName("Sentinel 限流/降级兜底方法")
    class FallbackTest {

        /**
         * 限流兜底：把用户放入排队队列，返回 202 + 排队号
         */
        @Test
        @DisplayName("executeSeckillBlockHandler：应入队并返回202排队中")
        void executeSeckillBlockHandler_shouldEnqueueAndReturnAccepted() {
            // 场景：Sentinel 限流触发 blockHandler
            BlockException ex = new BlockException("限流") {
            };
            // mock 排队服务：入队返回排队号，查询位置返回 3
            when(queueService.enqueue(SECKILL_ID, USER_ID)).thenReturn("queue-abc-001");
            when(queueService.getQueuePosition(SECKILL_ID, "queue-abc-001")).thenReturn(3);

            // 执行兜底方法
            Result<String> result = seckillService.executeSeckillBlockHandler(USER_ID, SECKILL_ID, ex);

            // 验证：返回 202（已接受，正在排队）
            assertThat(result.getCode()).isEqualTo(202);
            assertThat(result.getMessage()).isEqualTo("排队中，请稍候");
            assertThat(result.getData()).isEqualTo("queue-abc-001");

            // 验证：调用了入队和查询位置
            verify(queueService).enqueue(SECKILL_ID, USER_ID);
            verify(queueService).getQueuePosition(SECKILL_ID, "queue-abc-001");
        }

        /**
         * 降级兜底 - 业务异常：原样返回业务异常的 code 和 message
         */
        @Test
        @DisplayName("executeSeckillFallback：业务异常时返回对应的code和message")
        void executeSeckillFallback_businessException_returnOriginalError() {
            // 场景：方法执行过程中抛出 BusinessException（比如活动未开始）
            BusinessException ex = new BusinessException(
                    ErrorCode.OPERATION_FAIL.getCode(), "秒杀活动尚未开始");

            Result<String> result = seckillService.executeSeckillFallback(USER_ID, SECKILL_ID, ex);

            // 验证：返回原业务异常的 code 和 message
            assertThat(result.getCode()).isEqualTo(ErrorCode.OPERATION_FAIL.getCode());
            assertThat(result.getMessage()).isEqualTo("秒杀活动尚未开始");
        }

        /**
         * 降级兜底 - 非业务异常：返回统一的"系统繁忙"提示
         */
        @Test
        @DisplayName("executeSeckillFallback：非业务异常时返回系统繁忙")
        void executeSeckillFallback_otherException_returnSystemBusy() {
            // 场景：方法执行过程中抛出非 BusinessException（比如 Redis 连接异常）
            RuntimeException ex = new RuntimeException("Redis连接断开");

            Result<String> result = seckillService.executeSeckillFallback(USER_ID, SECKILL_ID, ex);

            // 验证：返回统一的系统繁忙提示
            assertThat(result.getCode()).isEqualTo(ErrorCode.OPERATION_FAIL.getCode());
            assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");
        }
    }
}
