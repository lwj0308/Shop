package com.shop.order.service.impl;

import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.seckill.dto.SeckillOrderDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.enums.SeckillStatusEnum;
import com.shop.order.feign.SeckillFeignClient;
import com.shop.order.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 秒杀服务实现类
 * <p>
 * 实现秒杀抢购的核心逻辑：Redis Lua脚本原子扣减库存 + MQ异步下单。
 * </p>
 * <p>
 * 核心优化点：
 * 1. 使用Lua脚本保证"查库存+扣库存+记录用户购买数"是原子操作，防止超卖
 * 2. 库存在Redis里扣减，不直接操作数据库，扛住高并发
 * 3. 抢购成功后发MQ消息异步创建订单，用户不用等订单创建完才返回
 * </p>
 * <p>
 * 小白讲解：秒杀为什么要用Lua脚本？
 * 因为"查库存"和"扣库存"是两步，如果在高并发下分开做，两个用户同时查都看到有库存，
 * 然后都去扣，就超卖了。Lua脚本在Redis里一次性执行完，中间不会被别人打断，所以是安全的。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    /** 秒杀活动Feign客户端，用于查询秒杀活动信息 */
    private final SeckillFeignClient seckillFeignClient;

    /** Redis模板，用于执行Lua脚本扣减秒杀库存 */
    private final StringRedisTemplate stringRedisTemplate;

    /** RocketMQ消息模板，用于发送异步下单消息 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 秒杀订单MQ Topic：用户抢购成功后往这个Topic发消息，消费者收到后异步创建订单 */
    private static final String TOPIC_SECKILL_ORDER = "topic_seckill_order";

    /** Redis秒杀库存key前缀：seckill:stock:{seckillId} 存的是剩余秒杀库存数量 */
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    /** Redis用户已购数量key前缀：seckill:user:{seckillId}:{userId} 存的是这个用户在这个活动买了几个 */
    private static final String USER_BOUGHT_KEY_PREFIX = "seckill:user:";

    /**
     * Lua脚本：原子扣减秒杀库存
     * <p>
     * 这个脚本在Redis里一次性完成三件事，保证原子性（中间不会被其他请求打断）：
     * 1. 查剩余库存，不够就返回-1（库存不足）
     * 2. 查用户已购数量，加上本次购买超过限购就返回-2（超过限购）
     * 3. 扣减库存 + 增加用户已购数量，返回1（成功）
     * </p>
     * <p>
     * 参数说明：
     * - KEYS[1]：秒杀库存key（seckill:stock:{seckillId}）
     * - KEYS[2]：用户已购key（seckill:user:{seckillId}:{userId}）
     * - ARGV[1]：限购数量（每人最多买几个）
     * - ARGV[2]：本次购买数量（秒杀固定买1个）
     * </p>
     * <p>
     * 返回值：1=成功, -1=库存不足, -2=超过限购
     * </p>
     */
    private static final String SECKILL_LUA_SCRIPT =
            "local stock = tonumber(redis.call('get', KEYS[1]))\n" +
            "if not stock or stock < tonumber(ARGV[2]) then\n" +
            "    return -1\n" +
            "end\n" +
            "local bought = tonumber(redis.call('get', KEYS[2]) or '0')\n" +
            "if bought + tonumber(ARGV[2]) > tonumber(ARGV[1]) then\n" +
            "    return -2\n" +
            "end\n" +
            "redis.call('decrby', KEYS[1], ARGV[2])\n" +
            "redis.call('incrby', KEYS[2], ARGV[2])\n" +
            "return 1";

    /**
     * 执行秒杀抢购
     * <p>
     * 完整流程：
     * 1. 查询秒杀活动信息并校验（活动存在、状态进行中、在时间窗口内）
     * 2. 执行Lua脚本原子扣减Redis秒杀库存
     * 3. 库存扣减成功后发送MQ消息异步创建秒杀订单
     * 4. 返回"抢购成功，正在创建订单"
     * </p>
     *
     * @param userId    用户ID
     * @param seckillId 秒杀活动ID
     * @return 抢购结果提示
     */
    @Override
    public Result<String> executeSeckill(Long userId, Long seckillId) {
        log.info("开始执行秒杀: userId={}, seckillId={}", userId, seckillId);

        // ========== 1. 查询秒杀活动信息并校验 ==========
        SeckillActivity activity = getAndCheckActivity(seckillId);

        // ========== 2. 执行Lua脚本原子扣减Redis秒杀库存 ==========
        // 小白讲解：这一步是秒杀的核心，用Lua脚本一次性完成"查库存+扣库存+记已购"，防止超卖
        Long result = executeLuaScript(seckillId, userId, activity.getLimitCount());

        // 根据Lua脚本返回值判断结果
        if (result == null || result == -1) {
            // 库存不足：来晚了一步，秒杀商品已经被抢完了
            log.info("秒杀失败，库存不足: userId={}, seckillId={}", userId, seckillId);
            return Result.fail(ErrorCode.OPERATION_FAIL.getCode(), "手慢了，商品已经被抢完了");
        }
        if (result == -2) {
            // 超过限购：这个用户已经达到限购上限，不能再买了
            log.info("秒杀失败，超过限购: userId={}, seckillId={}, limitCount={}",
                    userId, seckillId, activity.getLimitCount());
            return Result.fail(ErrorCode.OPERATION_FAIL.getCode(), "您已达到限购数量，无法再次购买");
        }
        if (result != 1) {
            // 其他未知返回值，按失败处理
            log.warn("秒杀Lua脚本返回未知结果: userId={}, seckillId={}, result={}", userId, seckillId, result);
            return Result.fail(ErrorCode.OPERATION_FAIL.getCode(), "秒杀失败，请稍后重试");
        }

        // ========== 3. 库存扣减成功，发送MQ消息异步创建秒杀订单 ==========
        // 小白讲解：抢购成功后不直接创建订单，而是发个消息给MQ，由消费者慢慢创建订单
        // 这样用户不用等数据库写完才收到响应，体验更好
        SeckillOrderDTO dto = new SeckillOrderDTO();
        dto.setSeckillId(seckillId);
        dto.setUserId(userId);

        try {
            rocketMQTemplate.syncSend(TOPIC_SECKILL_ORDER,
                    MessageBuilder.withPayload(dto).build());
            log.info("秒杀抢购成功，已发送异步下单消息: userId={}, seckillId={}", userId, seckillId);
        } catch (Exception e) {
            // MQ发送失败，需要回退刚才扣减的Redis库存，否则库存对不上
            // 小白讲解：名额抢到了但消息发不出去，要把库存加回去，让别人还能抢
            log.error("发送秒杀下单MQ消息失败，回退Redis库存: userId={}, seckillId={}", userId, seckillId, e);
            rollbackRedisStock(seckillId);
            return Result.fail(ErrorCode.OPERATION_FAIL.getCode(), "系统繁忙，请稍后重试");
        }

        // ========== 4. 返回抢购成功提示 ==========
        return Result.success("抢购成功，正在创建订单", null);
    }

    /**
     * 查询秒杀活动信息并校验
     * <p>
     * 校验内容：
     * 1. 活动是否存在（Feign查询结果不为空）
     * 2. 活动状态是否为"进行中"（status=1）
     * 3. 当前时间是否在秒杀时间窗口内（startTime <= now <= endTime）
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @return 秒杀活动实体
     */
    private SeckillActivity getAndCheckActivity(Long seckillId) {
        // 通过Feign远程调用商家服务查询秒杀活动信息
        Result<SeckillActivity> activityResult = seckillFeignClient.getSeckillById(seckillId);
        if (activityResult == null || !activityResult.isSuccess() || activityResult.getData() == null) {
            String msg = activityResult != null ? activityResult.getMessage() : "秒杀活动信息获取失败";
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), msg);
        }

        SeckillActivity activity = activityResult.getData();

        // 校验活动状态是否为"进行中"（status=1）
        if (activity.getStatus() == null
                || activity.getStatus() != SeckillStatusEnum.ACTIVE.getCode()) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "秒杀活动未开始或已结束");
        }

        // 校验当前时间是否在秒杀时间窗口内
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "秒杀活动尚未开始");
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "秒杀活动已结束");
        }

        return activity;
    }

    /**
     * 执行Lua脚本原子扣减Redis秒杀库存
     * <p>
     * 使用DefaultRedisScript执行Lua脚本，保证"查库存+扣库存+记已购"是原子操作。
     * </p>
     *
     * @param seckillId   秒杀活动ID
     * @param userId      用户ID
     * @param limitCount  限购数量（每人最多买几个）
     * @return Lua脚本返回值：1=成功, -1=库存不足, -2=超过限购
     */
    private Long executeLuaScript(Long seckillId, Long userId, Integer limitCount) {
        // 构建库存key和用户已购key
        String stockKey = STOCK_KEY_PREFIX + seckillId;
        String userKey = USER_BOUGHT_KEY_PREFIX + seckillId + ":" + userId;

        // 默认限购数量为1（防止活动配置的limitCount为null）
        int limit = limitCount != null && limitCount > 0 ? limitCount : 1;

        // 创建Redis脚本对象
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(SECKILL_LUA_SCRIPT);
        script.setResultType(Long.class);

        // 执行Lua脚本
        // 参数：脚本、key列表、ARGV参数（限购数量、购买数量1）
        List<String> keys = Arrays.asList(stockKey, userKey);
        return stringRedisTemplate.execute(script, keys, String.valueOf(limit), "1");
    }

    /**
     * 回退Redis秒杀库存
     * <p>
     * 当MQ消息发送失败时，需要把刚才扣减的Redis秒杀库存加回去，
     * 否则库存数量会不对（扣了但没下单成功）。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     */
    private void rollbackRedisStock(Long seckillId) {
        try {
            String stockKey = STOCK_KEY_PREFIX + seckillId;
            stringRedisTemplate.opsForValue().increment(stockKey);
            log.info("秒杀Redis库存回退成功: seckillId={}", seckillId);
        } catch (Exception e) {
            // 回退失败只能记录日志，后续可通过定时任务补偿
            log.error("秒杀Redis库存回退失败，需要人工处理: seckillId={}", seckillId, e);
        }
    }
}
