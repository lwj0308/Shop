package com.shop.order.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 秒杀订单异步创建消费者
 * <p>
 * 监听RocketMQ的 topic_seckill_order 消息。
 * 用户秒杀抢购成功（Redis库存扣减成功）后，SeckillService会发一条MQ消息过来，
 * 这个消费者收到消息后异步创建秒杀订单。
 * </p>
 * <p>
 * 小白讲解：为什么要异步创建订单？
 * 因为秒杀是高并发场景，如果在抢购时就同步创建订单（写数据库），数据库扛不住。
 * 所以先在Redis里扣库存（快），然后发MQ消息，消费者慢慢创建订单（不影响用户体验）。
 * </p>
 * <p>
 * 事务与幂等策略：
 * 1. onMessage 加 @Transactional：保证订单主表/明细/日志的本地写入原子性（任一步失败全部回滚）
 * 2. 幂等校验：创建前查询是否已存在该 seckillId+userId 的秒杀订单，存在则直接返回（防止MQ重试重复创建）
 * 3. 失败补偿：创建失败时回退Redis秒杀库存（Redis操作不受@Transactional影响），再抛异常让MQ重试
 * 4. 秒杀订单不走Seata分布式事务，因为秒杀库存已在Redis扣减，消费者只需创建订单+扣商品库存
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "topic_seckill_order",
        consumerGroup = "seckill-order-consumer-group",
        // 最大重试次数：消费失败后RocketMQ会自动重试
        // 每次重试的间隔会越来越长：10s, 30s, 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m, 1h, 2h
        maxReconsumeTimes = 3
)
public class SeckillOrderConsumer implements RocketMQListener<SeckillOrderDTO> {

    /** 订单主表Mapper */
    private final OrderInfoMapper orderInfoMapper;

    /** 订单明细Mapper */
    private final OrderItemMapper orderItemMapper;

    /** 订单状态日志Mapper */
    private final OrderLogMapper orderLogMapper;

    /** 订单号生成器 */
    private final OrderNoGenerator orderNoGenerator;

    /** 商品服务Feign客户端，用于扣减商品库存 */
    private final ProductFeignClient productFeignClient;

    /** 秒杀活动Feign客户端，用于查询秒杀活动信息 */
    private final SeckillFeignClient seckillFeignClient;

    /** RocketMQ消息模板，用于发送超时取消延时消息 */
    private final RocketMQTemplate rocketMQTemplate;

    /** Redis模板，用于创建订单失败时回退秒杀库存 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 超时自动取消延时消息Topic（复用普通订单的超时取消机制） */
    private static final String TOPIC_ORDER_TIMEOUT = "topic_order_timeout";

    /** Redis秒杀库存key前缀 */
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    /** 操作人类型：3系统（秒杀订单由系统自动创建） */
    private static final int OPERATOR_TYPE_SYSTEM = 3;

    /**
     * 消费秒杀下单消息
     * <p>
     * 收到消息后的处理流程：
     * 1. 幂等校验：查询是否已存在该 seckillId+userId 的秒杀订单，存在则直接返回
     * 2. 查询秒杀活动信息（拿到秒杀价、SKU ID等）
     * 3. 生成订单号
     * 4. 创建订单主表（orderType=2秒杀订单，价格为秒杀价）
     * 5. 创建订单明细（数量为1，价格为秒杀价）
     * 6. 扣减商品库存
     * 7. 发送30分钟超时取消延时消息
     * 8. 记录订单状态日志
     * </p>
     * <p>
     * 事务策略：onMessage 加 @Transactional 保证本地写入原子性。
     * 异常处理：任何一步失败，本地事务自动回滚订单数据，再回退Redis秒杀库存让MQ重试。
     * </p>
     *
     * @param dto 秒杀订单消息体（包含seckillId和userId）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(SeckillOrderDTO dto) {
        Long seckillId = dto.getSeckillId();
        Long userId = dto.getUserId();
        log.info("收到秒杀下单消息: seckillId={}, userId={}", seckillId, userId);

        // ========== 0. 幂等校验：防止MQ重试导致重复创建订单 ==========
        // 小白讲解：MQ消息可能因为网络问题重复投递，如果上一次已经创建了订单，
        // 这次再创建就会产生重复订单。所以先查一下这个用户对这个秒杀活动是否已经有订单了。
        Long existCount = orderInfoMapper.selectCount(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getSeckillId, seckillId)
                        .eq(OrderInfo::getUserId, userId)
                        .eq(OrderInfo::getOrderType, OrderTypeEnum.SECKILL.getCode())
        );
        if (existCount != null && existCount > 0) {
            log.info("秒杀订单已存在，幂等返回: seckillId={}, userId={}", seckillId, userId);
            return;
        }

        try {
            createSeckillOrder(dto);
            log.info("秒杀订单创建成功: seckillId={}, userId={}", seckillId, userId);
        } catch (Exception e) {
            // 创建订单失败：回退Redis秒杀库存，让用户的名额释放出来
            // 小白讲解：抢购时Redis库存已经扣了1个，但订单没创建成功，
            // 需要把这1个库存加回去，否则库存数量会对不上
            // 注意：Redis操作不受@Transactional影响，回滚本地数据库后Redis库存依然需要手动回退
            log.error("秒杀订单创建失败，回退Redis库存: seckillId={}, userId={}", seckillId, userId, e);
            rollbackRedisStock(seckillId);
            // 抛出异常让RocketMQ重试（重试时幂等校验会拦截重复创建）
            throw new RuntimeException("秒杀订单创建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建秒杀订单
     * <p>
     * 简化版订单创建逻辑（不走Seata，因为秒杀库存已在Redis扣减）：
     * 1. 查询秒杀活动信息
     * 2. 生成订单号
     * 3. 创建订单主表 OrderInfo（orderType=2秒杀，价格=秒杀价，seckillId记录活动）
     * 4. 创建订单明细 OrderItem（数量1，价格=秒杀价）
     * 5. 记录订单状态日志（本地写入，提前到扣库存之前）
     * 6. 扣减商品库存（Feign调用商品服务，放最后；失败时事务回滚本地数据+回退Redis秒杀库存）
     * 7. 发送30分钟超时取消延时消息（try-catch，失败不影响主流程）
     * </p>
     * <p>
     * 顺序说明：把扣减商品库存放在所有本地写入之后，确保扣库存成功后不会再因本地写入失败
     * 导致库存与订单数据不一致。事务由 onMessage 上的 @Transactional 管理。
     * </p>
     *
     * @param dto 秒杀订单消息体
     */
    private void createSeckillOrder(SeckillOrderDTO dto) {
        Long seckillId = dto.getSeckillId();
        Long userId = dto.getUserId();

        // ========== 1. 查询秒杀活动信息 ==========
        // 拿到秒杀价、SKU ID、商家ID等信息，用于创建订单
        Result<SeckillActivity> activityResult = seckillFeignClient.getSeckillById(seckillId);
        if (activityResult == null || !activityResult.isSuccess() || activityResult.getData() == null) {
            throw new RuntimeException("秒杀活动信息获取失败: seckillId=" + seckillId);
        }
        SeckillActivity activity = activityResult.getData();

        // ========== 2. 生成订单号 ==========
        String orderNo = orderNoGenerator.generate();

        // ========== 3. 创建订单主表 ==========
        // 秒杀订单和普通订单的区别：orderType=2，价格为秒杀价，记录seckillId
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(orderNo);
        orderInfo.setUserId(userId);
        orderInfo.setMerchantId(activity.getMerchantId());
        // 秒杀订单：总金额和实付金额都是秒杀价
        orderInfo.setTotalAmount(activity.getSeckillPrice());
        orderInfo.setPayAmount(activity.getSeckillPrice());
        orderInfo.setFreightAmount(BigDecimal.ZERO);
        orderInfo.setDiscountAmount(BigDecimal.ZERO);
        // 订单类型：2=秒杀订单
        orderInfo.setOrderType(OrderTypeEnum.SECKILL.getCode());
        // 记录秒杀活动ID，取消订单时用于回退Redis秒杀库存
        orderInfo.setSeckillId(seckillId);
        // 订单状态：0=待付款
        orderInfo.setStatus(OrderStatusEnum.UNPAID.getCode());
        orderInfoMapper.insert(orderInfo);

        Long orderId = orderInfo.getId();

        // ========== 4. 创建订单明细 ==========
        // 秒杀订单固定买1个商品，价格就是秒杀价
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setOrderNo(orderNo);
        orderItem.setProductId(activity.getProductId());
        orderItem.setSkuId(activity.getSkuId());
        orderItem.setProductName("");
        orderItem.setPrice(activity.getSeckillPrice());
        orderItem.setQuantity(1);
        // 小计金额 = 单价 × 数量 = 秒杀价 × 1
        orderItem.setSubtotal(activity.getSeckillPrice());
        orderItemMapper.insert(orderItem);

        // ========== 5. 记录订单状态日志（提前到扣库存之前） ==========
        // 小白讲解：日志先写好，这样扣库存失败时事务回滚，日志也会回滚，不会留下脏数据
        saveOrderLog(orderId, orderNo, null, OrderStatusEnum.UNPAID.getCode(),
                "秒杀下单", userId, OPERATOR_TYPE_SYSTEM, "秒杀活动ID: " + seckillId);

        // ========== 6. 扣减商品库存（放最后，失败时事务回滚本地数据） ==========
        // 小白讲解：这里扣的是商品的实物库存（DB库存），不是Redis秒杀库存
        // Redis秒杀库存已经在抢购时扣过了，这里扣的是商品仓库里的库存
        // 放在最后：如果扣减失败，@Transactional 会回滚前面的订单主表/明细/日志，再回退Redis秒杀库存
        Result<Void> deductResult = productFeignClient.deductStock(activity.getSkuId(), 1);
        if (deductResult == null || !deductResult.isSuccess()) {
            String msg = deductResult != null ? deductResult.getMessage() : "商品库存扣减失败";
            throw new RuntimeException("扣减商品库存失败: skuId=" + activity.getSkuId() + ", " + msg);
        }

        // ========== 7. 发送30分钟超时取消延时消息 ==========
        // 复用普通订单的超时取消机制，30分钟未支付自动取消
        sendTimeoutMessage(orderNo);
    }

    /**
     * 发送超时自动取消的延时消息
     * <p>
     * 使用RocketMQ延时消息，30分钟后自动投递。
     * 复用普通订单的超时取消机制（topic_order_timeout）。
     * </p>
     *
     * @param orderNo 订单号
     */
    private void sendTimeoutMessage(String orderNo) {
        try {
            rocketMQTemplate.syncSend(
                    TOPIC_ORDER_TIMEOUT,
                    MessageBuilder.withPayload(orderNo).build(),
                    3000,
                    16    // 延时等级16 = 30分钟
            );
            log.info("秒杀订单发送延时消息成功: orderNo={}", orderNo);
        } catch (Exception e) {
            // 延时消息发送失败不影响订单创建，记录日志即可
            // 可以通过定时任务补偿（扫描超时未支付的秒杀订单）
            log.error("秒杀订单发送延时消息失败: orderNo={}，需要通过定时任务补偿", orderNo, e);
        }
    }

    /**
     * 回退Redis秒杀库存
     * <p>
     * 订单创建失败时，把抢购时扣减的Redis秒杀库存加回去。
     * 这样库存数量才能保持正确。
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

    /**
     * 保存订单状态日志
     * <p>
     * 记录订单状态变化，方便追溯订单生命周期。
     * </p>
     *
     * @param orderId      订单ID
     * @param orderNo      订单号
     * @param fromStatus   变化前的状态
     * @param toStatus     变化后的状态
     * @param action       操作类型
     * @param operatorId   操作人ID
     * @param operatorType 操作人类型
     * @param note         备注
     */
    private void saveOrderLog(Long orderId, String orderNo, Integer fromStatus, Integer toStatus,
                              String action, Long operatorId, Integer operatorType, String note) {
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderId);
        orderLog.setOrderNo(orderNo);
        orderLog.setFromStatus(fromStatus);
        orderLog.setToStatus(toStatus);
        orderLog.setAction(action);
        orderLog.setOperatorId(operatorId);
        orderLog.setOperatorType(operatorType);
        orderLog.setNote(note);
        orderLogMapper.insert(orderLog);
    }
}
