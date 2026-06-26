package com.shop.payment.mq;

import com.shop.model.payment.enums.PayStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付消息生产者
 * <p>
 * 支付成功后，通过RocketMQ发送消息通知订单服务更新订单状态。
 * 使用消息队列的好处：
 * 1. 异步解耦：支付服务不用等订单服务处理完才返回，响应更快
 * 2. 可靠投递：即使订单服务暂时挂了，消息也不会丢，等服务恢复后继续消费
 * 3. 削峰填谷：大促时大量支付回调不会把订单服务压垮
 * </p>
 * <p>
 * 核心优化点：
 * 1. 消息内容包含完整信息：orderNo、paymentNo、payStatus、payTime
 * 2. 发送失败不影响支付流程，但记录详细日志便于排查
 * 3. 后续可升级为事务消息确保投递可靠性
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMQProducer {

    /** RocketMQ模板，Spring Boot自动注入，用来发消息 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 支付回调Topic：支付成功后往这个Topic发消息，订单服务消费它 */
    private static final String TOPIC_PAYMENT_CALLBACK = "topic_payment_callback";

    /** 退款Topic：退款成功后往这个Topic发消息，订单服务消费后把订单状态改回已取消 */
    private static final String TOPIC_PAYMENT_REFUND = "topic_payment_refund";

    /**
     * 发送支付成功消息
     * <p>
     * 支付成功后调用此方法，通知订单服务更新订单状态为"已支付"。
     * 消息内容包含完整信息，方便订单服务和后续对账使用。
     * </p>
     *
     * @param orderNo   订单号（订单服务根据这个更新订单状态）
     * @param paymentNo 支付单号（方便对账和排查问题）
     */
    public void sendPaySuccessMessage(String orderNo, String paymentNo) {
        try {
            // 构造消息内容，包含完整信息
            // 订单服务消费时可以根据这些信息做校验和记录
            Map<String, String> message = new HashMap<>();
            message.put("orderNo", orderNo);
            message.put("paymentNo", paymentNo);
            message.put("payStatus", String.valueOf(PayStatusEnum.PAID.getCode())); // 用枚举code避免硬编码，PAID=2
            message.put("payTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // 发送消息，syncSend是同步发送，确保消息发成功才继续
            rocketMQTemplate.syncSend(TOPIC_PAYMENT_CALLBACK, message);
            log.info("支付成功消息发送成功: orderNo={}, paymentNo={}", orderNo, paymentNo);
        } catch (Exception e) {
            // 发消息失败不影响支付流程，只记录日志
            // 订单服务可以通过定时任务补偿（查询支付状态来更新订单）
            log.error("支付成功消息发送失败: orderNo={}, paymentNo={}，订单服务需要通过定时任务补偿",
                    orderNo, paymentNo, e);
        }
    }

    /**
     * 发送退款成功消息
     * <p>
     * 退款成功后调用此方法，通知订单服务把订单状态改为"已取消"（退款导致的取消）。
     * 为什么退款要通知订单服务？因为订单服务可能还把订单当成"已支付"，
     * 退款后需要同步状态，避免用户看到"已支付但已退款"的矛盾状态。
     * </p>
     *
     * @param orderNo   订单号（订单服务根据这个把订单状态改成已取消）
     * @param paymentNo 支付单号（方便对账和排查）
     * @param amount   退款金额（方便订单服务记录退款金额）
     */
    public void sendRefundSuccessMessage(String orderNo, String paymentNo, java.math.BigDecimal amount) {
        try {
            // 构造退款消息，包含退款金额便于对账
            Map<String, String> message = new HashMap<>();
            message.put("orderNo", orderNo);
            message.put("paymentNo", paymentNo);
            message.put("refundAmount", amount.toPlainString());
            message.put("refundTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            rocketMQTemplate.syncSend(TOPIC_PAYMENT_REFUND, message);
            log.info("退款成功消息发送成功: orderNo={}, paymentNo={}, refundAmount={}", orderNo, paymentNo, amount);
        } catch (Exception e) {
            // 发消息失败不影响退款流程，只记录日志
            // 订单服务可以通过定时任务对账补偿（查询支付状态发现已退款，同步订单状态）
            log.error("退款成功消息发送失败: orderNo={}, paymentNo={}，订单服务需要通过对账补偿",
                    orderNo, paymentNo, e);
        }
    }
}
