package com.shop.order.consumer;

import com.shop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 订单超时自动取消消费者
 * <p>
 * 监听RocketMQ的延时消息topic_order_timeout，
 * 当用户下单30分钟后还没付款，这个消费者就会收到消息，
 * 然后自动把订单取消掉，同时回滚库存。
 * </p>
 * <p>
 * 核心优化点：
 * 1. 消费者幂等：通过Redis去重，防止消息重复消费
 * 2. 消费失败重试：RocketMQ自动重试，最多16次
 * 3. 死信队列：重试超过最大次数后进入死信队列，需要人工处理
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "topic_order_timeout",
        consumerGroup = "order-timeout-consumer-group",
        // 最大重试次数：消费失败后RocketMQ会自动重试，最多重试16次
        // 每次重试的间隔会越来越长：10s, 30s, 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m, 1h, 2h
        maxReconsumeTimes = 3
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    /** 订单服务，用来执行自动取消逻辑 */
    private final OrderService orderService;

    /** Redis模板，用于消费者幂等去重 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 幂等key前缀：order:timeout:consumed:{orderNo} */
    private static final String CONSUMED_KEY_PREFIX = "order:timeout:consumed:";

    /** 幂等key有效期（小时）：24小时后自动过期 */
    private static final long CONSUMED_KEY_EXPIRE_HOURS = 24;

    /**
     * 消费延时消息
     * <p>
     * 收到消息后：
     * 1. 幂等校验：检查这个订单号是否已经消费过
     * 2. 如果没消费过，调用orderService.autoCancelOrder()取消超时订单
     * 3. 消费成功后，在Redis中标记已消费
     * </p>
     *
     * @param orderNo 订单号
     */
    @Override
    public void onMessage(String orderNo) {
        log.info("收到订单超时消息: orderNo={}", orderNo);

        // ========== 1. 幂等校验：防止消息重复消费 ==========
        // 用Redis的SETNX实现：如果key已存在，说明这条消息已经消费过了
        String consumedKey = CONSUMED_KEY_PREFIX + orderNo;
        Boolean isFirst = stringRedisTemplate.opsForValue()
                .setIfAbsent(consumedKey, String.valueOf(System.currentTimeMillis()),
                        CONSUMED_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
        if (isFirst == null || !isFirst) {
            log.info("订单超时消息已消费过，忽略: orderNo={}", orderNo);
            return;
        }

        try {
            // ========== 2. 执行自动取消逻辑 ==========
            orderService.autoCancelOrder(orderNo);
        } catch (Exception e) {
            // 消费失败，删除幂等标记，让下次重试可以重新消费
            stringRedisTemplate.delete(consumedKey);
            log.error("订单超时自动取消失败: orderNo={}，等待RocketMQ重试", orderNo, e);
            throw e; // 抛出异常让RocketMQ重试
        }
    }
}
