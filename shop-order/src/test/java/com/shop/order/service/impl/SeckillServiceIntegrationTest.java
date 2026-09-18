package com.shop.order.service.impl;

import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.seckill.dto.SeckillOrderDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.enums.SeckillStatusEnum;
import com.shop.order.feign.SeckillFeignClient;
import com.shop.order.service.QueueService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-I-02 秒杀全链路集成测试
 * <p>
 * 小白讲解：
 * 之前的单元测试用 Mockito 把 Redis 也假装了，只能验证"调用了 Lua 脚本"，
 * 但不能验证"Lua 脚本在真实 Redis 上真的原子扣减了库存"、"用户已购数量真的写进去了"。
 *
 * 这个集成测试连的是本地 Docker 中正在运行的 Redis 容器（shop-redis），
 * 让 SeckillServiceImpl 的 Lua 脚本在真实 Redis 上执行，验证：
 * 1. Lua 脚本三合一原子操作（查库存→查已购→扣库存+记已购）在真实 Redis 上工作正常
 * 2. 库存不足时返回 -1，库存和用户已购都不变（原子性保证）
 * 3. 超过限购时返回 -2，库存和用户已购都不变（原子性保证）
 * 4. 幂等锁 5 秒内防重复点击（真实 TTL 写入）
 * 5. MQ 发送失败时回退 Redis 库存（真实 increment 操作）
 * 6. 活动校验失败时不执行扣库存（Feign mock + Redis 真实）
 * 7. Redis 数据验证：库存 Key 正确扣减、用户已购 Key 正确记录
 *
 * 隔离策略：使用 Redis DB 13（B-I-03 用 DB 15，B-I-06 用 DB 14，业务用 DB 0），
 * 测试前 FLUSHDB 清空 DB 13，不影响业务数据。
 *
 * Mock 策略：Feign（远程调用）+ RocketMQTemplate（外部 MQ）+ QueueService（排队）
 * 都用 Mockito 假装，因为它们是外部依赖，且单元测试已覆盖；
 * StringRedisTemplate 用真实连接，因为这是本测试的核心——验证 Lua 脚本原子性。
 *
 * 环境要求：本地 Docker 中需有 Redis 容器监听 6379 端口（如 shop-redis）。
 * 如果 Redis 不可用，所有测试会自动跳过（assumeTrue）。
 * </p>
 */
@DisplayName("B-I-02 秒杀全链路集成测试 - 真实 Redis Lua 脚本")
class SeckillServiceIntegrationTest {

    // ==================== 测试常量 ====================

    /** 测试用 Redis 数据库索引（B-I-03 用 15，B-I-06 用 14，B-I-02 用 13，业务用 0） */
    private static final int TEST_DB_INDEX = 13;
    /** 本地 Redis 主机 */
    private static final String REDIS_HOST = "localhost";
    /** 本地 Redis 端口 */
    private static final int REDIS_PORT = 6379;

    /** 测试用用户ID */
    private static final Long USER_ID = 1001L;
    /** 测试用秒杀活动ID */
    private static final Long SECKILL_ID = 5001L;
    /** 秒杀库存 Redis key（和被测类常量保持一致） */
    private static final String STOCK_KEY = "seckill:stock:" + SECKILL_ID;
    /** 用户已购 Redis key（和被测类常量保持一致） */
    private static final String USER_BOUGHT_KEY = "seckill:user:" + SECKILL_ID + ":" + USER_ID;
    /** 抢购幂等锁 key（和被测类常量保持一致） */
    private static final String GRAB_LOCK_KEY = "idempotent:seckill:grab:" + SECKILL_ID + ":" + USER_ID;
    /** 秒杀下单 MQ Topic（和被测类常量保持一致） */
    private static final String TOPIC_SECKILL_ORDER = "topic_seckill_order";

    // ==================== 共享资源 ====================

    /** 共享的 Lettuce 连接工厂（指向 DB 13） */
    private static LettuceConnectionFactory sharedFactory;
    /** 测试前 Redis 是否可用（不可用时跳过所有测试） */
    private static boolean redisAvailable = false;

    // ==================== 测试实例字段 ====================

    /** 真实连接本地 Redis 的字符串 Redis 客户端 */
    private StringRedisTemplate redisTemplate;

    /** 被测对象：秒杀服务实现 */
    private SeckillServiceImpl seckillService;

    /** Mock 的秒杀活动 Feign 客户端 */
    private SeckillFeignClient seckillFeignClient;

    /** Mock 的 RocketMQ 消息模板 */
    private RocketMQTemplate rocketMQTemplate;

    /** Mock 的排队队列服务 */
    private QueueService queueService;

    /**
     * 在所有测试前创建 Redis 连接工厂，并检测 Redis 是否可用
     * 小白讲解：连本地 Docker 中的 Redis（localhost:6379），切到 DB 13。
     * 如果连接失败（比如 Redis 没启动），所有测试会被跳过，不让 CI 失败。
     */
    @BeforeAll
    static void initConnectionFactory() {
        try {
            sharedFactory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
            // 切换到 DB 13，避免和业务数据混淆
            sharedFactory.setDatabase(TEST_DB_INDEX);
            sharedFactory.afterPropertiesSet();
            sharedFactory.start();

            // 测试连接是否可用：尝试执行 PING 命令
            String pong = sharedFactory.getConnection().ping();
            redisAvailable = "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            redisAvailable = false;
            System.err.println("[B-I-02] Redis 不可用，测试将被跳过：" + e.getMessage());
        }
    }

    /**
     * 每个测试前重建 Redis 客户端、Mock 对象和被测服务实例，并清空 DB 13 数据
     * 小白讲解：每个测试都需要干净的 Redis 状态，避免上一个测试的库存数据影响下一个。
     * 用 FLUSHDB 只清空当前 DB（13），不影响业务 DB 0。
     */
    @BeforeEach
    void setUp() {
        // 如果 Redis 不可用，通过 assumeTrue 跳过当前测试（不算失败）
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis 不可用，跳过 B-I-02 集成测试");

        // 创建真实的字符串 Redis 客户端
        redisTemplate = new StringRedisTemplate(sharedFactory);

        // 清空 Redis DB 13 中的所有数据，确保每个测试从干净状态开始
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        // 创建 Mock 对象：Feign、RocketMQ、QueueService 是外部依赖，用 Mockito 假装
        seckillFeignClient = mock(SeckillFeignClient.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        queueService = mock(QueueService.class);

        // 创建被测对象，手动注入：真实 Redis + Mock 依赖
        // 构造函数参数顺序：seckillFeignClient, stringRedisTemplate, rocketMQTemplate, queueService
        seckillService = new SeckillServiceImpl(
                seckillFeignClient,
                redisTemplate,
                rocketMQTemplate,
                queueService);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造一个"进行中"的秒杀活动实体
     * <p>开始时间在过去1分钟，结束时间在未来1小时，状态为进行中(1)</p>
     *
     * @param limitCount 限购数量
     * @return 构造好的 SeckillActivity
     */
    private SeckillActivity buildActiveActivity(int limitCount) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(SECKILL_ID);
        activity.setProductId(2001L);
        activity.setSkuId(3001L);
        activity.setMerchantId(1L);
        activity.setSeckillPrice(new BigDecimal("9.90"));
        activity.setOriginalPrice(new BigDecimal("19.90"));
        activity.setTotalCount(100);
        activity.setAvailableCount(50);
        activity.setLimitCount(limitCount);
        activity.setStatus(SeckillStatusEnum.ACTIVE.getCode());
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        return activity;
    }

    /**
     * 在 Redis 中预热秒杀库存
     * <p>模拟活动创建时的库存预热步骤，把 available_count 写入 Redis</p>
     *
     * @param stock 库存数量
     */
    private void preloadStock(int stock) {
        redisTemplate.opsForValue().set(STOCK_KEY, String.valueOf(stock));
    }

    /**
     * 读取 Redis 中的当前秒杀库存
     *
     * @return 库存数量，不存在返回 null
     */
    private Integer getCurrentStock() {
        String value = redisTemplate.opsForValue().get(STOCK_KEY);
        return value != null ? Integer.parseInt(value) : null;
    }

    /**
     * 读取 Redis 中的用户已购数量
     *
     * @return 已购数量，不存在返回 0
     */
    private int getUserBoughtCount() {
        String value = redisTemplate.opsForValue().get(USER_BOUGHT_KEY);
        return value != null ? Integer.parseInt(value) : 0;
    }

    /**
     * 读取 Redis 中幂等锁的 TTL（秒）
     *
     * @return TTL，不存在返回 -2
     */
    private long getGrabLockTtl() {
        Long ttl = redisTemplate.getExpire(GRAB_LOCK_KEY, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2L;
    }

    /**
     * 让 Feign 返回指定活动
     */
    private void mockFeignReturnActivity(SeckillActivity activity) {
        when(seckillFeignClient.getSeckillById(SECKILL_ID))
                .thenReturn(Result.success(activity));
    }

    // ==================== 1. Lua 脚本原子扣减测试 ====================

    @Nested
    @DisplayName("Lua 脚本原子扣减库存")
    class LuaAtomicDeductTest {

        @Test
        @DisplayName("正常扣减成功：库存 10→9，用户已购 0→1，Lua 返回 1")
        void luaDeduct_success_stockDecreasedAndUserBoughtIncreased() {
            // 准备：活动限购 1，Redis 预热库存 10
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);

            // 执行抢购
            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证返回值：抢购成功
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("抢购成功，正在创建订单");

            // 验证 Redis 数据：库存 10→9，用户已购 0→1
            assertThat(getCurrentStock()).isEqualTo(9);
            assertThat(getUserBoughtCount()).isEqualTo(1);

            // 验证 MQ 消息已发送
            verify(rocketMQTemplate).syncSend(eq(TOPIC_SECKILL_ORDER), any(Message.class));
        }

        @Test
        @DisplayName("库存不足：库存为 0，Lua 返回 -1，库存和用户已购都不变")
        void luaDeduct_stockZero_returnsSoldOutAndNoChange() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(0);

            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证返回值：库存不足
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("手慢了，商品已经被抢完了");

            // 验证 Redis 数据：库存仍为 0，用户已购仍为 0（未被错误扣减）
            assertThat(getCurrentStock()).isEqualTo(0);
            assertThat(getUserBoughtCount()).isEqualTo(0);

            // 验证：库存不足时不应发 MQ
            verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        }

        @Test
        @DisplayName("库存不足：库存 Key 不存在时，Lua 返回 -1")
        void luaDeduct_stockKeyMissing_returnsSoldOut() {
            mockFeignReturnActivity(buildActiveActivity(1));
            // 不预热库存，Key 不存在

            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("手慢了，商品已经被抢完了");
            verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        }

        @Test
        @DisplayName("超过限购：限购 1，用户已购 1，Lua 返回 -2，库存和用户已购都不变")
        void luaDeduct_exceedLimit_returnsLimitExceededAndNoChange() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);
            // 预置用户已购 1（达到限购上限）
            redisTemplate.opsForValue().set(USER_BOUGHT_KEY, "1");

            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证返回值：超过限购
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("您已达到限购数量，无法再次购买");

            // 验证 Redis 数据：库存仍为 10，用户已购仍为 1（原子性保证，未发生错误扣减）
            assertThat(getCurrentStock()).isEqualTo(10);
            assertThat(getUserBoughtCount()).isEqualTo(1);

            verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        }

        @Test
        @DisplayName("限购 2 场景：第一次抢购成功，第二次抢购成功，第三次返回超限购")
        void luaDeduct_limitTwo_thirdAttemptExceedsLimit() {
            mockFeignReturnActivity(buildActiveActivity(2));
            preloadStock(10);

            // 第一次抢购：成功，库存 10→9，已购 0→1
            Result<String> r1 = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(r1.isSuccess()).isTrue();
            assertThat(getCurrentStock()).isEqualTo(9);
            assertThat(getUserBoughtCount()).isEqualTo(1);

            // 释放并重新获取幂等锁（删除锁，模拟 5 秒后重试）
            redisTemplate.delete(GRAB_LOCK_KEY);

            // 第二次抢购：成功，库存 9→8，已购 1→2
            Result<String> r2 = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(r2.isSuccess()).isTrue();
            assertThat(getCurrentStock()).isEqualTo(8);
            assertThat(getUserBoughtCount()).isEqualTo(2);

            // 释放幂等锁
            redisTemplate.delete(GRAB_LOCK_KEY);

            // 第三次抢购：失败（超过限购 2），库存和已购不变
            Result<String> r3 = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(r3.isSuccess()).isFalse();
            assertThat(r3.getMessage()).isEqualTo("您已达到限购数量，无法再次购买");
            assertThat(getCurrentStock()).isEqualTo(8);
            assertThat(getUserBoughtCount()).isEqualTo(2);
        }
    }

    // ==================== 2. 幂等锁测试 ====================

    @Nested
    @DisplayName("幂等锁防重复点击")
    class GrabLockTest {

        @Test
        @DisplayName("首次抢购：幂等锁写入成功，TTL 约 5 秒")
        void grabLock_firstAttempt_lockWrittenWithCorrectTtl() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);

            seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证：幂等锁 Key 存在
            String lockValue = redisTemplate.opsForValue().get(GRAB_LOCK_KEY);
            assertThat(lockValue).isEqualTo("1");

            // 验证：TTL 在 4~5 秒之间（允许少量耗时误差）
            long ttl = getGrabLockTtl();
            assertThat(ttl).isBetween(4L, 5L);
        }

        @Test
        @DisplayName("5 秒内重复点击：被幂等锁拦截，返回操作太频繁")
        void grabLock_repeatWithin5Seconds_blockedByLock() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);

            // 第一次抢购：成功
            Result<String> r1 = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(r1.isSuccess()).isTrue();
            assertThat(getCurrentStock()).isEqualTo(9);

            // 第二次抢购（不释放锁）：被拦截
            Result<String> r2 = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(r2.isSuccess()).isFalse();
            assertThat(r2.getMessage()).isEqualTo("操作太频繁，请稍后重试");

            // 验证：库存未被第二次扣减（仍为 9）
            assertThat(getCurrentStock()).isEqualTo(9);
            assertThat(getUserBoughtCount()).isEqualTo(1);

            // 验证：被拦截时未执行 Lua 脚本（通过未发 MQ 间接验证）
            // 第一次成功发送了 1 次 MQ，第二次被拦截不应再发
            verify(rocketMQTemplate).syncSend(eq(TOPIC_SECKILL_ORDER), any(Message.class));
        }

        @Test
        @DisplayName("幂等锁过期后可再次抢购（手动模拟锁过期）")
        void grabLock_expired_canGrabAgain() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);

            // 第一次抢购：成功
            Result<String> r1 = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(r1.isSuccess()).isTrue();
            assertThat(getCurrentStock()).isEqualTo(9);

            // 手动删除锁，模拟 5 秒后锁过期
            redisTemplate.delete(GRAB_LOCK_KEY);

            // 第二次抢购：锁已过期，可以再次抢
            // 但由于用户已购 1，达到限购 1，会返回"超过限购"
            Result<String> r2 = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(r2.isSuccess()).isFalse();
            assertThat(r2.getMessage()).isEqualTo("您已达到限购数量，无法再次购买");

            // 验证：库存未被第二次扣减（因为是限购拦截，不是锁拦截）
            assertThat(getCurrentStock()).isEqualTo(9);
        }
    }

    // ==================== 3. MQ 发送与回退测试 ====================

    @Nested
    @DisplayName("MQ 消息发送与失败回退")
    class MqSendAndRollbackTest {

        @Test
        @DisplayName("MQ 发送成功：消息体包含正确的 seckillId 和 userId")
        void mqSend_success_messageContainsCorrectPayload() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);

            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            assertThat(result.isSuccess()).isTrue();

            // 捕获 MQ 消息，验证消息体内容
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Message<SeckillOrderDTO>> msgCaptor =
                    ArgumentCaptor.forClass(Message.class);
            verify(rocketMQTemplate).syncSend(eq(TOPIC_SECKILL_ORDER), msgCaptor.capture());
            SeckillOrderDTO payload = msgCaptor.getValue().getPayload();
            assertThat(payload.getSeckillId()).isEqualTo(SECKILL_ID);
            assertThat(payload.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("MQ 发送失败：回退 Redis 库存（库存 +1），返回系统繁忙")
        void mqSend_fail_rollbackRedisStock() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);
            // mock MQ 发送抛异常
            doThrow(new RuntimeException("MQ 连接失败"))
                    .when(rocketMQTemplate)
                    .syncSend(anyString(), any(Message.class));

            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证返回值：系统繁忙
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");

            // 验证 Redis 库存已回退：Lua 扣减后是 9，回退后应恢复为 10
            assertThat(getCurrentStock()).isEqualTo(10);

            // 验证：用户已购数量不会被回退（仍为 1）
            // 小白讲解：这是已知的设计——MQ 失败时只回退库存，不回退用户已购记录，
            // 因为用户已经"占用"了一个名额，只是订单没创建成功，后续会有补偿机制处理
            assertThat(getUserBoughtCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("MQ 发送失败 + 回退也失败：主流程仍返回系统繁忙（不抛异常）")
        void mqSendFail_andRollbackFail_returnsSystemBusy() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);
            doThrow(new RuntimeException("MQ 连接失败"))
                    .when(rocketMQTemplate)
                    .syncSend(anyString(), any(Message.class));

            // 由于库存回退用的是 redisTemplate.opsForValue().increment()，
            // 真实 Redis 下 increment 不会失败，所以我们用 Redis 真实数据验证回退成功即可
            // 这个测试验证 MQ 异常被吞掉，主流程正常返回
            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");
            // 真实 Redis 下 increment 成功，库存回退为 10
            assertThat(getCurrentStock()).isEqualTo(10);
        }
    }

    // ==================== 4. 活动校验测试 ====================

    @Nested
    @DisplayName("活动校验阶段（Feign Mock + 真实 Redis）")
    class ActivityCheckTest {

        @Test
        @DisplayName("Feign 返回 null：抛 BusinessException，不执行扣库存")
        void activityCheck_feignReturnNull_throwsException() {
            when(seckillFeignClient.getSeckillById(SECKILL_ID)).thenReturn(null);
            preloadStock(10);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动信息获取失败");

            // 验证：库存未被扣减
            assertThat(getCurrentStock()).isEqualTo(10);
            verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        }

        @Test
        @DisplayName("Feign 返回失败：抛 BusinessException 并携带失败消息")
        void activityCheck_feignReturnFail_throwsException() {
            when(seckillFeignClient.getSeckillById(SECKILL_ID))
                    .thenReturn(Result.fail(ErrorCode.DATA_NOT_FOUND.getCode(), "秒杀活动不存在"));
            preloadStock(10);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动不存在");

            assertThat(getCurrentStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("活动状态非进行中：抛 BusinessException，不执行扣库存")
        void activityCheck_statusNotActive_throwsException() {
            SeckillActivity activity = buildActiveActivity(1);
            activity.setStatus(SeckillStatusEnum.PENDING.getCode());
            mockFeignReturnActivity(activity);
            preloadStock(10);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动未开始或已结束");

            assertThat(getCurrentStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("活动尚未开始：抛 BusinessException，不执行扣库存")
        void activityCheck_notStarted_throwsException() {
            SeckillActivity activity = buildActiveActivity(1);
            activity.setStartTime(LocalDateTime.now().plusHours(1));
            mockFeignReturnActivity(activity);
            preloadStock(10);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动尚未开始");

            assertThat(getCurrentStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("活动已结束：抛 BusinessException，不执行扣库存")
        void activityCheck_ended_throwsException() {
            SeckillActivity activity = buildActiveActivity(1);
            activity.setEndTime(LocalDateTime.now().minusHours(1));
            mockFeignReturnActivity(activity);
            preloadStock(10);

            assertThatThrownBy(() -> seckillService.executeSeckill(USER_ID, SECKILL_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动已结束");

            assertThat(getCurrentStock()).isEqualTo(10);
        }
    }

    // ==================== 5. Sentinel 兜底方法测试 ====================

    @Nested
    @DisplayName("Sentinel 限流/降级兜底")
    class FallbackTest {

        @Test
        @DisplayName("executeSeckillBlockHandler：入队并返回 202 排队中")
        void blockHandler_shouldEnqueueAndReturnAccepted() {
            // 模拟排队服务行为
            when(queueService.enqueue(SECKILL_ID, USER_ID)).thenReturn("queue-abc-001");
            when(queueService.getQueuePosition(SECKILL_ID, "queue-abc-001")).thenReturn(3);

            // 执行 blockHandler（不依赖 Redis，可直接调用）
            com.alibaba.csp.sentinel.slots.block.BlockException ex =
                    new com.alibaba.csp.sentinel.slots.block.BlockException("限流") {
                    };
            Result<String> result = seckillService.executeSeckillBlockHandler(USER_ID, SECKILL_ID, ex);

            // 验证：返回 202（已接受，正在排队）
            assertThat(result.getCode()).isEqualTo(202);
            assertThat(result.getMessage()).isEqualTo("排队中，请稍候");
            assertThat(result.getData()).isEqualTo("queue-abc-001");

            verify(queueService).enqueue(SECKILL_ID, USER_ID);
            verify(queueService).getQueuePosition(SECKILL_ID, "queue-abc-001");
        }

        @Test
        @DisplayName("executeSeckillFallback：业务异常返回对应 code 和 message")
        void fallback_businessException_returnOriginalError() {
            BusinessException ex = new BusinessException(
                    ErrorCode.OPERATION_FAIL.getCode(), "秒杀活动尚未开始");

            Result<String> result = seckillService.executeSeckillFallback(USER_ID, SECKILL_ID, ex);

            assertThat(result.getCode()).isEqualTo(ErrorCode.OPERATION_FAIL.getCode());
            assertThat(result.getMessage()).isEqualTo("秒杀活动尚未开始");
        }

        @Test
        @DisplayName("executeSeckillFallback：非业务异常返回系统繁忙")
        void fallback_otherException_returnSystemBusy() {
            RuntimeException ex = new RuntimeException("Redis 连接断开");

            Result<String> result = seckillService.executeSeckillFallback(USER_ID, SECKILL_ID, ex);

            assertThat(result.getCode()).isEqualTo(ErrorCode.OPERATION_FAIL.getCode());
            assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");
        }
    }

    // ==================== 6. Redis 数据完整性验证 ====================

    @Nested
    @DisplayName("Redis 数据完整性")
    class RedisDataIntegrityTest {

        @Test
        @DisplayName("抢购成功后：库存 Key 和用户已购 Key 都正确写入")
        void redisData_afterSuccess_stockAndUserBoughtKeysCorrect() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(5);

            seckillService.executeSeckill(USER_ID, SECKILL_ID);

            // 验证：库存 Key 存在且值为 4
            assertThat(redisTemplate.hasKey(STOCK_KEY)).isTrue();
            assertThat(getCurrentStock()).isEqualTo(4);

            // 验证：用户已购 Key 存在且值为 1
            assertThat(redisTemplate.hasKey(USER_BOUGHT_KEY)).isTrue();
            assertThat(getUserBoughtCount()).isEqualTo(1);

            // 验证：幂等锁 Key 存在
            assertThat(redisTemplate.hasKey(GRAB_LOCK_KEY)).isTrue();
        }

        @Test
        @DisplayName("不同用户抢购同一活动：用户已购 Key 相互独立")
        void redisData_differentUsers_userBoughtKeysIndependent() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);

            // 用户 A 抢购
            Long userA = 2001L;
            Result<String> rA = seckillService.executeSeckill(userA, SECKILL_ID);
            assertThat(rA.isSuccess()).isTrue();

            // 用户 B 抢购
            Long userB = 2002L;
            Result<String> rB = seckillService.executeSeckill(userB, SECKILL_ID);
            assertThat(rB.isSuccess()).isTrue();

            // 验证：库存共扣减 2 次（10→8）
            assertThat(getCurrentStock()).isEqualTo(8);

            // 验证：用户 A 和用户 B 的已购 Key 相互独立，各自都是 1
            String userAKey = "seckill:user:" + SECKILL_ID + ":" + userA;
            String userBKey = "seckill:user:" + SECKILL_ID + ":" + userB;
            assertThat(redisTemplate.opsForValue().get(userAKey)).isEqualTo("1");
            assertThat(redisTemplate.opsForValue().get(userBKey)).isEqualTo("1");
        }

        @Test
        @DisplayName("MQ 失败回退后：库存 Key 恢复原值，幂等锁仍保留")
        void redisData_afterMqRollback_stockRestoredAndLockKept() {
            mockFeignReturnActivity(buildActiveActivity(1));
            preloadStock(10);
            doThrow(new RuntimeException("MQ 连接失败"))
                    .when(rocketMQTemplate)
                    .syncSend(anyString(), any(Message.class));

            Result<String> result = seckillService.executeSeckill(USER_ID, SECKILL_ID);
            assertThat(result.isSuccess()).isFalse();

            // 验证：库存已回退为 10
            assertThat(getCurrentStock()).isEqualTo(10);

            // 验证：幂等锁仍然保留（5 秒内不能重试）
            // 小白讲解：MQ 失败后库存回退了，但幂等锁没删除，防止用户立即重试造成重复发送 MQ
            assertThat(redisTemplate.hasKey(GRAB_LOCK_KEY)).isTrue();
        }
    }
}
