package com.shop.payment.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.result.Result;
import com.shop.model.payment.dto.PayCallbackDTO;
import com.shop.model.payment.dto.PayCreateDTO;
import com.shop.model.payment.vo.PaymentVO;
import com.shop.model.payment.vo.PayResultVO;
import com.shop.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 支付控制器
 * <p>
 * 处理支付相关的所有接口，包括创建支付、模拟支付、支付回调、查询支付信息等。
 * MVP阶段使用模拟支付，不接入真实支付平台。
 * </p>
 * <p>
 * 接口权限说明：
 * - /payment/create：用户接口，需要用户登录
 * - /payment/mock-pay：用户接口，需要用户登录（MVP专用）
 * - /payment/callback：系统接口，第三方支付平台回调，不需要登录
 * - /payment/order/{orderNo}：用户接口，需要用户登录
 * - /payment/refund：商家接口，需要商家登录
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Tag(name = "支付", description = "支付创建、模拟支付、支付回调、退款等操作")
public class PaymentController {

    /** 支付服务 */
    private final PaymentService paymentService;

    /**
     * 创建支付
     * <p>
     * 用户下单后调用此接口，生成一条支付记录。
     * 返回支付单号，前端可以用它跳转到支付页面。
     * </p>
     *
     * @param dto 创建支付参数（订单号、金额、支付方式）
     * @return 支付信息（含支付单号）
     */
    @PostMapping("/create")
    @Operation(summary = "创建支付", description = "根据订单创建支付记录，返回支付单号")
    public Result<PaymentVO> createPayment(@Validated @RequestBody PayCreateDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        PaymentVO paymentVO = paymentService.createPayment(userId, dto);
        return Result.success(paymentVO);
    }

    /**
     * 模拟支付（MVP专用）
     * <p>
     * MVP阶段不接入真实支付，这个接口直接把支付状态改为"已支付"。
     * 后续接入真实支付后，这个接口可以保留用于测试环境。
     * </p>
     *
     * @param paymentId 支付记录ID
     * @return 支付结果（成功/失败）
     */
    @PostMapping("/mock-pay/{paymentId}")
    @Operation(summary = "模拟支付", description = "MVP阶段专用，直接将支付状态改为已支付")
    public Result<PayResultVO> mockPay(@PathVariable Long paymentId) {
        // 从登录态获取当前用户ID，传入Service做归属校验，防止越权支付他人订单
        Long userId = StpUtil.getLoginIdAsLong();
        PayResultVO result = paymentService.mockPay(userId, paymentId);
        return Result.success(result);
    }

    /**
     * 支付回调（统一入口）
     * <p>
     * 第三方支付平台（如支付宝、微信）支付完成后会通知我们，
     * 所有渠道的回调都走这个接口，通过channel字段区分来源。
     * 幂等性保证：
     * 1. out_trade_no唯一索引防重
     * 2. Redis分布式锁防并发回调
     * 3. 状态校验：已支付成功的不再处理
     * </p>
     *
     * @param dto 回调参数（支付单号、第三方交易号、渠道、回调数据）
     * @return 支付结果
     */
    @PostMapping("/callback")
    @Operation(summary = "支付回调", description = "第三方支付平台回调统一入口，三重幂等保障")
    public Result<PayResultVO> handleCallback(@Validated @RequestBody PayCallbackDTO dto) {
        PayResultVO result = paymentService.handleCallback(dto);
        return Result.success(result);
    }

    /**
     * 根据订单号查询支付信息
     *
     * @param orderNo 订单号
     * @return 支付信息
     */
    @GetMapping("/order/{orderNo}")
    @Operation(summary = "查询支付信息", description = "根据订单号查询支付状态和详情")
    public Result<PaymentVO> getPaymentByOrderNo(@PathVariable String orderNo) {
        // 从登录态获取当前用户ID，传入Service做归属校验，只能查自己的支付记录
        Long userId = StpUtil.getLoginIdAsLong();
        PaymentVO paymentVO = paymentService.getPaymentByOrderNo(userId, orderNo);
        return Result.success(paymentVO);
    }

    /**
     * 退款
     * <p>
     * 用户申请退款时调用，将支付状态改为"已退款"。
     * MVP阶段只改状态，不真实退款。
     * 退款金额校验：不能超过实付金额，必须大于0。
     * </p>
     *
     * @param paymentId 支付记录ID
     * @param amount    退款金额
     * @return 操作结果
     */
    @PostMapping("/refund")
    @Operation(summary = "退款", description = "申请退款，退款金额不能超过实付金额")
    public Result<Void> refund(@RequestParam @NotNull(message = "支付记录ID不能为空") Long paymentId,
                                @RequestParam @NotNull(message = "退款金额不能为空")
                                @DecimalMin(value = "0.01", message = "退款金额必须大于0") BigDecimal amount) {
        // 从登录态获取当前用户ID，传入Service做归属校验，只能退自己的支付单
        Long userId = StpUtil.getLoginIdAsLong();
        paymentService.refund(userId, paymentId, amount);
        return Result.success("退款成功", null);
    }
}
