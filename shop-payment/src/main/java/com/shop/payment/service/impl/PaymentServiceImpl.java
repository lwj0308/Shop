package com.shop.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.payment.dto.PayCallbackDTO;
import com.shop.model.payment.dto.PayCreateDTO;
import com.shop.model.payment.entity.PaymentCallback;
import com.shop.model.payment.entity.PaymentInfo;
import com.shop.model.payment.enums.PayStatusEnum;
import com.shop.model.payment.vo.PaymentVO;
import com.shop.model.payment.vo.PayResultVO;
import com.shop.payment.mapper.PaymentCallbackMapper;
import com.shop.payment.mapper.PaymentInfoMapper;
import com.shop.payment.mq.PaymentMQProducer;
import com.shop.payment.feign.OrderFeignClient;
import com.shop.payment.service.PaymentService;
import com.shop.payment.util.PaymentNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 支付服务实现类
 * <p>
 * 实现支付的核心业务逻辑，包括创建支付、模拟支付、处理回调、查询支付、退款等。
 * 核心优化点：
 * 1. 支付回调幂等：out_trade_no唯一索引 + Redis分布式锁 + 状态校验三重保障
 * 2. 支付状态机：使用枚举定义合法状态转换，防止非法操作
 * 3. 支付金额校验：回调金额必须和订单金额一致
 * 4. 支付超时处理：30分钟未支付自动关闭
 * 5. 退款安全：退款金额不超过实付金额，退款幂等
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    /** 支付记录Mapper，操作payment_info表 */
    private final PaymentInfoMapper paymentInfoMapper;

    /** 支付回调日志Mapper，操作payment_callback表 */
    private final PaymentCallbackMapper paymentCallbackMapper;

    /** MQ生产者，支付成功后发消息通知订单服务 */
    private final PaymentMQProducer paymentMQProducer;

    /** 订单服务Feign客户端，用于反查订单金额（支付安全核心） */
    private final OrderFeignClient orderFeignClient;

    /** Redis模板，用于分布式锁和幂等校验 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 支付回调分布式锁key前缀 */
    private static final String LOCK_CALLBACK_PREFIX = "payment:callback:lock:";

    /** 支付回调锁超时时间（秒） */
    private static final long LOCK_CALLBACK_TIMEOUT = 10;

    /**
     * 创建支付记录
     * <p>
     * 逻辑说明：
     * 1. 检查该订单是否已有支付记录（防止重复创建）
     * 2. 反查订单服务获取真实实付金额（不信任前端传入金额，防止金额篡改）
     * 3. 生成唯一的支付单号
     * 4. 创建支付记录，状态为"待支付"
     * </p>
     * <p>
     * 支付安全核心：金额必须从订单服务反查，不能用前端传入的金额。
     * 比如用户下单100元，但前端把金额改成0.01元传给支付服务，
     * 如果不反查，用户就能0.01元买100元商品。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO createPayment(Long userId, PayCreateDTO dto) {
        // 检查该订单是否已有待支付的记录（避免重复支付）
        PaymentInfo existPayment = paymentInfoMapper.selectOne(
                new LambdaQueryWrapper<PaymentInfo>()
                        .eq(PaymentInfo::getOrderNo, dto.getOrderNo())
                        .eq(PaymentInfo::getPayStatus, PayStatusEnum.WAIT.getCode())
        );
        if (existPayment != null) {
            log.info("订单已有待支付记录: orderNo={}, paymentNo={}", dto.getOrderNo(), existPayment.getPaymentNo());
            return convertToVO(existPayment);
        }

        // 反查订单服务获取真实实付金额（支付安全核心，不信任前端金额）
        // 订单服务内部会校验订单归属（orderNo + userId 匹配），防止给他人订单创建支付
        Result<BigDecimal> amountResult = orderFeignClient.getPayAmount(dto.getOrderNo(), userId);
        if (!amountResult.isSuccess() || amountResult.getData() == null) {
            log.error("反查订单金额失败，拒绝创建支付: orderNo={}, userId={}", dto.getOrderNo(), userId);
            throw new BusinessException(ErrorCode.PAYMENT_FAIL.getCode(), "订单不存在或不可支付");
        }
        BigDecimal realAmount = amountResult.getData();
        log.info("反查订单金额成功: orderNo={}, realAmount={}, frontAmount={}",
                dto.getOrderNo(), realAmount, dto.getAmount());

        // 生成支付单号（雪花算法保证唯一）
        String paymentNo = PaymentNoGenerator.generate();

        // 创建支付记录（使用反查的真实金额，不使用前端传入的金额）
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentNo(paymentNo);
        paymentInfo.setOrderNo(dto.getOrderNo());
        paymentInfo.setUserId(userId);
        paymentInfo.setAmount(realAmount);
        paymentInfo.setPayType(dto.getPayType());
        paymentInfo.setPayStatus(PayStatusEnum.WAIT.getCode());
        paymentInfoMapper.insert(paymentInfo);

        log.info("创建支付记录: userId={}, orderNo={}, paymentNo={}, amount={}",
                userId, dto.getOrderNo(), paymentNo, realAmount);

        return convertToVO(paymentInfo);
    }

    /**
     * 模拟支付（MVP专用）
     * <p>
     * MVP阶段不接入真实支付，直接把状态改为"已支付"。
     * 同时模拟一个回调流程：生成回调记录，并发送MQ消息通知订单服务。
     * 根据支付方式（payType）记录不同的渠道：1-mock 2-wechat 3-alipay，
     * 这样前端选择"微信支付"或"支付宝"时，回调日志会记录对应渠道，方便后续接入真实支付。
     * </p>
     * <p>
     * 安全说明：校验paymentInfo.userId必须等于当前登录用户，防止越权支付他人订单。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayResultVO mockPay(Long userId, Long paymentId) {
        // 查询支付记录
        PaymentInfo paymentInfo = paymentInfoMapper.selectById(paymentId);
        if (paymentInfo == null) {
            throw new BusinessException(ErrorCode.PAYMENT_FAIL.getCode(), "支付记录不存在");
        }

        // 归属校验：只能支付自己的订单，防止越权支付他人订单
        if (!paymentInfo.getUserId().equals(userId)) {
            log.warn("越权支付拦截: 当前用户={}, 支付单归属用户={}, paymentId={}", userId, paymentInfo.getUserId(), paymentId);
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该支付单");
        }

        // 使用状态机校验：只有待支付状态才能支付
        PayStatusEnum fromStatus = PayStatusEnum.getByCode(paymentInfo.getPayStatus());
        PayStatusEnum.checkTransit(fromStatus, PayStatusEnum.PAID, "模拟支付");

        // 乐观锁更新支付状态为"已支付"
        int updated = paymentInfoMapper.update(null,
                new LambdaUpdateWrapper<PaymentInfo>()
                        .eq(PaymentInfo::getId, paymentId)
                        .eq(PaymentInfo::getPayStatus, PayStatusEnum.WAIT.getCode())
                        .set(PaymentInfo::getPayStatus, PayStatusEnum.PAID.getCode())
                        .set(PaymentInfo::getPayTime, LocalDateTime.now())
                        .set(PaymentInfo::getCallbackTime, LocalDateTime.now())
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PAYMENT_FAIL.getCode(), "支付失败，支付状态已变更");
        }

        // 根据支付方式确定渠道名称和交易号前缀
        // payType: 1-模拟支付 2-微信 3-支付宝
        Integer payType = paymentInfo.getPayType();
        String channel;
        String tradeNoPrefix;
        if (payType != null && payType == 2) {
            channel = "wechat";
            tradeNoPrefix = "WX_";
        } else if (payType != null && payType == 3) {
            channel = "alipay";
            tradeNoPrefix = "ALI_";
        } else {
            channel = "mock";
            tradeNoPrefix = "MOCK_";
        }

        // 模拟回调：记录回调日志（渠道根据支付方式区分）
        PaymentCallback callback = new PaymentCallback();
        callback.setPaymentId(paymentId);
        callback.setChannel(channel);
        callback.setOutTradeNo(tradeNoPrefix + paymentInfo.getPaymentNo());
        callback.setCallbackData("{\"status\":\"success\",\"amount\":" + paymentInfo.getAmount() + ",\"channel\":\"" + channel + "\"}");
        paymentCallbackMapper.insert(callback);

        // 发送MQ消息通知订单服务支付成功
        paymentMQProducer.sendPaySuccessMessage(paymentInfo.getOrderNo(), paymentInfo.getPaymentNo());

        log.info("模拟支付成功: paymentId={}, paymentNo={}, orderNo={}, channel={}",
                paymentId, paymentInfo.getPaymentNo(), paymentInfo.getOrderNo(), channel);

        PayResultVO result = new PayResultVO();
        result.setPaymentNo(paymentInfo.getPaymentNo());
        result.setSuccess(true);
        result.setMessage("支付成功");
        return result;
    }

    /**
     * 处理支付回调
     * <p>
     * 核心逻辑：
     * 1. Redis分布式锁防并发回调
     * 2. 幂等校验：已支付成功的不再处理
     * 3. 支付金额校验：回调金额必须和订单金额一致
     * 4. 更新支付状态
     * 5. 发送MQ消息通知订单服务
     * 6. 记录回调日志（out_trade_no唯一索引防重）
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayResultVO handleCallback(PayCallbackDTO dto) {
        // ========== 1. Redis分布式锁防并发回调 ==========
        // 同一个支付单号同一时刻只能有一个回调在处理
        String lockKey = LOCK_CALLBACK_PREFIX + dto.getPaymentNo();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_CALLBACK_TIMEOUT, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            log.warn("支付回调并发锁获取失败，可能有并发回调: paymentNo={}", dto.getPaymentNo());
            // 并发回调时直接返回成功，让另一个请求处理
            PayResultVO result = new PayResultVO();
            result.setPaymentNo(dto.getPaymentNo());
            result.setSuccess(true);
            result.setMessage("处理中");
            return result;
        }

        try {
            // ========== 2. 根据支付单号查询支付记录 ==========
            PaymentInfo paymentInfo = paymentInfoMapper.selectOne(
                    new LambdaQueryWrapper<PaymentInfo>()
                            .eq(PaymentInfo::getPaymentNo, dto.getPaymentNo())
            );
            if (paymentInfo == null) {
                throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_ERROR.getCode(), "支付记录不存在");
            }

            // ========== 3. 幂等校验：如果已经支付过了，直接返回成功 ==========
            if (paymentInfo.getPayStatus() == PayStatusEnum.PAID.getCode()) {
                log.info("支付已处理，忽略重复回调: paymentNo={}, outTradeNo={}", dto.getPaymentNo(), dto.getOutTradeNo());
                PayResultVO result = new PayResultVO();
                result.setPaymentNo(dto.getPaymentNo());
                result.setSuccess(true);
                result.setMessage("已处理");
                return result;
            }

            // ========== 4. 支付金额校验：回调金额必须和订单金额一致 ==========
            // 从回调数据中解析金额（MVP阶段简化处理，实际需要根据不同渠道解析）
            // 这里先做基本的金额校验框架，后续接入真实支付时完善
            validateCallbackAmount(paymentInfo, dto);

            // ========== 5. 使用状态机校验状态转换 ==========
            PayStatusEnum fromStatus = PayStatusEnum.getByCode(paymentInfo.getPayStatus());
            PayStatusEnum.checkTransit(fromStatus, PayStatusEnum.PAID, "支付回调");

            // ========== 6. 乐观锁更新支付状态为"已支付" ==========
            LocalDateTime now = LocalDateTime.now();
            int updated = paymentInfoMapper.update(null,
                    new LambdaUpdateWrapper<PaymentInfo>()
                            .eq(PaymentInfo::getId, paymentInfo.getId())
                            .eq(PaymentInfo::getPayStatus, PayStatusEnum.WAIT.getCode())
                            .set(PaymentInfo::getPayStatus, PayStatusEnum.PAID.getCode())
                            .set(PaymentInfo::getPayTime, now)
                            .set(PaymentInfo::getCallbackTime, now)
            );
            if (updated == 0) {
                log.warn("支付回调更新失败，支付状态已变更: paymentNo={}", dto.getPaymentNo());
                PayResultVO result = new PayResultVO();
                result.setPaymentNo(dto.getPaymentNo());
                result.setSuccess(true);
                result.setMessage("已处理");
                return result;
            }

            // ========== 7. 记录回调日志（out_trade_no唯一索引保证幂等） ==========
            PaymentCallback callback = new PaymentCallback();
            callback.setPaymentId(paymentInfo.getId());
            callback.setChannel(dto.getChannel());
            callback.setOutTradeNo(dto.getOutTradeNo());
            callback.setCallbackData(dto.getCallbackData());
            try {
                paymentCallbackMapper.insert(callback);
            } catch (DuplicateKeyException e) {
                log.warn("重复回调，已忽略: outTradeNo={}", dto.getOutTradeNo());
            }

            // ========== 8. 发送MQ消息通知订单服务支付成功 ==========
            paymentMQProducer.sendPaySuccessMessage(paymentInfo.getOrderNo(), paymentInfo.getPaymentNo());

            log.info("支付回调处理成功: paymentNo={}, outTradeNo={}, channel={}",
                    dto.getPaymentNo(), dto.getOutTradeNo(), dto.getChannel());

            PayResultVO result = new PayResultVO();
            result.setPaymentNo(dto.getPaymentNo());
            result.setSuccess(true);
            result.setMessage("支付成功");
            return result;
        } finally {
            // 释放分布式锁
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 根据订单号查询支付信息
     * <p>
     * 安全说明：查询条件带userId，只能查到自己的支付记录，
     * 防止用户用订单号枚举查看他人支付信息。
     * </p>
     */
    @Override
    public PaymentVO getPaymentByOrderNo(Long userId, String orderNo) {
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(
                new LambdaQueryWrapper<PaymentInfo>()
                        .eq(PaymentInfo::getOrderNo, orderNo)
                        .eq(PaymentInfo::getUserId, userId)
                        .orderByDesc(PaymentInfo::getCreateTime)
                        .last("LIMIT 1")
        );
        if (paymentInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "支付记录不存在");
        }
        return convertToVO(paymentInfo);
    }

    /**
     * 退款处理
     * <p>
     * 核心优化点：
     * 1. 退款金额校验：退款金额不能超过实付金额
     * 2. 支付状态机校验：只有已支付状态才能退款
     * 3. 退款幂等：同一笔支付不能重复退款
     * 4. 乐观锁更新：防止并发退款
     * 5. 退款成功发MQ通知订单服务同步状态
     * </p>
     * <p>
     * 安全说明：校验paymentInfo.userId必须等于当前登录用户，防止越权退款他人订单。
     * </p>
     *
     * @param userId    当前登录用户ID（用于归属校验）
     * @param paymentId 支付记录ID
     * @param amount    退款金额
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long userId, Long paymentId, BigDecimal amount) {
        // 查询支付记录
        PaymentInfo paymentInfo = paymentInfoMapper.selectById(paymentId);
        if (paymentInfo == null) {
            throw new BusinessException(ErrorCode.PAYMENT_FAIL.getCode(), "支付记录不存在");
        }

        // 归属校验：只能退自己的支付单，防止越权退款他人订单
        if (!paymentInfo.getUserId().equals(userId)) {
            log.warn("越权退款拦截: 当前用户={}, 支付单归属用户={}, paymentId={}", userId, paymentInfo.getUserId(), paymentId);
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该支付单");
        }

        // 使用状态机校验：只有已支付状态才能退款
        PayStatusEnum fromStatus = PayStatusEnum.getByCode(paymentInfo.getPayStatus());
        PayStatusEnum.checkTransit(fromStatus, PayStatusEnum.REFUNDING, "退款");

        // 退款金额校验：退款金额不能超过支付金额
        if (amount.compareTo(paymentInfo.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_FAIL.getCode(), "退款金额不能超过支付金额");
        }
        // 退款金额必须大于0
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_FAIL.getCode(), "退款金额必须大于0");
        }

        // 乐观锁更新支付状态为"已退款"
        // TODO: 后续支持部分退款时，需要新增退款记录表，这里先简化处理
        int updated = paymentInfoMapper.update(null,
                new LambdaUpdateWrapper<PaymentInfo>()
                        .eq(PaymentInfo::getId, paymentId)
                        .eq(PaymentInfo::getPayStatus, PayStatusEnum.PAID.getCode())
                        .set(PaymentInfo::getPayStatus, PayStatusEnum.REFUNDED.getCode())
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PAYMENT_FAIL.getCode(), "退款失败，支付状态已变更");
        }

        // 退款成功后发MQ消息通知订单服务把订单状态改为已取消
        // 避免出现"订单显示已支付但实际已退款"的不一致状态
        paymentMQProducer.sendRefundSuccessMessage(paymentInfo.getOrderNo(), paymentInfo.getPaymentNo(), amount);

        log.info("退款成功: userId={}, paymentId={}, paymentNo={}, refundAmount={}", userId, paymentId, paymentInfo.getPaymentNo(), amount);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验回调金额是否和订单金额一致
     * <p>
     * 支付安全的核心：第三方支付平台回调时传的金额，
     * 必须和我们系统记录的金额一致，防止金额被篡改。
     * 比如用户付了100元，但黑客把回调金额改成了1元，
     * 如果不校验就会造成损失。
     * </p>
     * <p>
     * MVP阶段实现：从callbackData JSON中解析amount字段（如果有）和paymentInfo比对。
     * 接入真实支付时，需要根据不同渠道（支付宝/微信）的签名规则验证回调真实性和金额。
     * </p>
     *
     * @param paymentInfo 支付记录
     * @param dto         回调参数
     */
    private void validateCallbackAmount(PaymentInfo paymentInfo, PayCallbackDTO dto) {
        String callbackData = dto.getCallbackData();
        if (callbackData == null || callbackData.isEmpty()) {
            // MVP阶段回调数据可能为空，仅记录日志不阻断流程
            log.warn("回调数据为空，跳过金额校验: paymentNo={}", dto.getPaymentNo());
            return;
        }

        // 尝试从callbackData JSON中解析amount字段
        // 格式示例：{"status":"success","amount":100.00,"channel":"mock"}
        try {
            com.fasterxml.jackson.databind.JsonNode jsonNode =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(callbackData);
            if (jsonNode.has("amount")) {
                BigDecimal callbackAmount = new BigDecimal(jsonNode.get("amount").asText());
                // 金额必须完全一致，差1分钱都算异常（可能是篡改或渠道错误）
                if (callbackAmount.compareTo(paymentInfo.getAmount()) != 0) {
                    log.error("回调金额校验失败: 订单金额={}, 回调金额={}, paymentNo={}",
                            paymentInfo.getAmount(), callbackAmount, dto.getPaymentNo());
                    throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_ERROR.getCode(),
                            "回调金额与订单金额不一致");
                }
                log.debug("回调金额校验通过: amount={}", callbackAmount);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // JSON解析失败，MVP阶段不阻断，记录日志
            log.warn("回调数据JSON解析失败，跳过金额校验: paymentNo={}, callbackData={}",
                    dto.getPaymentNo(), callbackData, e);
        }
    }

    /**
     * PaymentInfo实体转PaymentVO
     *
     * @param paymentInfo 支付记录实体
     * @return 支付信息VO
     */
    private PaymentVO convertToVO(PaymentInfo paymentInfo) {
        PaymentVO vo = new PaymentVO();
        vo.setId(paymentInfo.getId());
        vo.setPaymentNo(paymentInfo.getPaymentNo());
        vo.setOrderNo(paymentInfo.getOrderNo());
        vo.setUserId(paymentInfo.getUserId());
        vo.setAmount(paymentInfo.getAmount());
        vo.setPayType(paymentInfo.getPayType());
        vo.setPayStatus(paymentInfo.getPayStatus());
        vo.setPayTime(paymentInfo.getPayTime());
        vo.setCreateTime(paymentInfo.getCreateTime());
        return vo;
    }
}
