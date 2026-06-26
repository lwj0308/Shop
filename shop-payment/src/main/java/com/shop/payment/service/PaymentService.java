package com.shop.payment.service;

import com.shop.model.payment.dto.PayCallbackDTO;
import com.shop.model.payment.dto.PayCreateDTO;
import com.shop.model.payment.vo.PaymentVO;
import com.shop.model.payment.vo.PayResultVO;

import java.math.BigDecimal;

/**
 * 支付服务接口
 * <p>
 * 定义支付相关的业务方法，包括创建支付、模拟支付、处理回调、查询支付、退款等。
 * MVP阶段使用模拟支付，不接入真实支付平台。
 * </p>
 */
public interface PaymentService {

    /**
     * 创建支付记录
     * <p>
     * 用户下单后调用此方法，生成一条支付记录（状态为待支付）。
     * 相当于告诉系统"这个订单要付钱了，先记一笔"。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    创建支付参数（订单号、金额、支付方式）
     * @return 支付信息（含支付单号，前端用来跳转支付页面）
     */
    PaymentVO createPayment(Long userId, PayCreateDTO dto);

    /**
     * 模拟支付（MVP专用）
     * <p>
     * MVP阶段不接入真实支付，这个方法直接把支付状态改为"已支付"。
     * 后续接入真实支付后，这个方法可以保留用于测试环境。
     * </p>
     * <p>
     * 安全说明：传入userId做归属校验，防止用户A用用户B的paymentId支付。
     * </p>
     *
     * @param userId    当前登录用户ID（用于归属校验）
     * @param paymentId 支付记录ID
     * @return 支付结果（成功/失败）
     */
    PayResultVO mockPay(Long userId, Long paymentId);

    /**
     * 处理支付回调
     * <p>
     * 第三方支付平台（如支付宝、微信）通知我们支付结果时调用。
     * 核心逻辑：
     * 1. 幂等校验：通过out_trade_no唯一索引防止重复处理
     * 2. 更新支付状态为"已支付"
     * 3. 通过RocketMQ发送支付成功消息，通知订单服务
     * 4. 记录回调日志
     * </p>
     *
     * @param dto 回调参数（支付单号、第三方交易号、渠道、回调数据）
     * @return 支付结果（成功/失败）
     */
    PayResultVO handleCallback(PayCallbackDTO dto);

    /**
     * 根据订单号查询支付信息
     * <p>
     * 订单服务或前端需要查看某个订单的支付状态时调用。
     * </p>
     * <p>
     * 安全说明：传入userId做归属校验，只能查自己的支付记录，
     * 防止用户A用订单号枚举查看用户B的支付信息。
     * </p>
     *
     * @param userId  当前登录用户ID（用于归属校验）
     * @param orderNo 订单号
     * @return 支付信息（含支付状态、金额等）
     */
    PaymentVO getPaymentByOrderNo(Long userId, String orderNo);

    /**
     * 退款处理
     * <p>
     * 用户申请退款时调用，将支付状态改为"已退款"。
     * MVP阶段只改状态，不真实退款到用户账户。
     * </p>
     * <p>
     * 安全说明：传入userId做归属校验，只能退自己的支付单，
     * 防止用户A退款用户B的订单。
     * </p>
     *
     * @param userId    当前登录用户ID（用于归属校验）
     * @param paymentId 支付记录ID
     * @param amount    退款金额（支持部分退款）
     */
    void refund(Long userId, Long paymentId, BigDecimal amount);
}
