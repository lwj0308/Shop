package com.shop.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.common.result.Result;
import com.shop.model.order.entity.OrderInfo;
import com.shop.order.mapper.OrderInfoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 订单内部接口（供其他微服务通过 Feign 调用，不鉴权）
 * <p>
 * 供 shop-product 评价服务调用：
 * - 校验订单归属：发表评价前校验订单是否属于当前用户
 * - 标记已评价：评价成功后标记订单为已评价，防止重复评价
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/order/inner")
@RequiredArgsConstructor
@Tag(name = "订单内部接口", description = "供其他微服务Feign调用，不鉴权")
public class OrderInnerController {

    /** 订单主表Mapper */
    private final OrderInfoMapper orderInfoMapper;

    /**
     * 校验订单归属
     * <p>
     * 检查指定订单是否属于指定用户。发表评价前调用，确保用户只能评价自己的订单。
     * </p>
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return true=归属正确 false=不属于该用户
     */
    @Operation(summary = "校验订单归属", description = "检查订单是否属于指定用户")
    @GetMapping("/{orderId}/owner")
    public Result<Boolean> checkOrderOwnership(
            @PathVariable Long orderId,
            @RequestParam Long userId) {
        Long count = orderInfoMapper.selectCount(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, orderId)
                        .eq(OrderInfo::getUserId, userId)
        );
        return Result.success(count > 0);
    }

    /**
     * 标记订单为已评价
     * <p>
     * 用户发表评价后调用，将 is_reviewed 设为 1，防止重复评价。
     * </p>
     *
     * @param orderId 订单ID
     * @return 操作结果
     */
    @Operation(summary = "标记订单已评价", description = "将订单的 is_reviewed 设为 1")
    @PostMapping("/{orderId}/mark-reviewed")
    public Result<Void> markOrderReviewed(@PathVariable Long orderId) {
        int updated = orderInfoMapper.update(null,
                new LambdaUpdateWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, orderId)
                        .set(OrderInfo::getIsReviewed, 1)
        );
        if (updated == 0) {
            log.warn("标记订单已评价失败，订单不存在: orderId={}", orderId);
            return Result.fail("订单不存在");
        }
        log.info("订单标记已评价成功: orderId={}", orderId);
        return Result.success(null);
    }

    /**
     * 根据订单号获取实付金额（供支付服务反查，防止前端篡改金额）
     * <p>
     * 支付安全核心：创建支付记录时，金额不能信任前端传入的值，
     * 必须从订单服务反查真实的实付金额。
     * 比如用户下单100元，但前端把金额改成0.01元传给支付服务，
     * 如果支付服务不反查，用户就能0.01元买100元商品。
     * </p>
     * <p>
     * 同时校验订单归属：传入userId确保只能给自己的订单创建支付。
     * </p>
     *
     * @param orderNo 订单号
     * @param userId  当前用户ID（用于归属校验）
     * @return 订单实付金额（如果订单不存在或不属于该用户，返回fail）
     */
    @Operation(summary = "获取订单实付金额", description = "供支付服务反查金额，防止前端篡改金额")
    @GetMapping("/pay-amount")
    public Result<BigDecimal> getPayAmount(
            @RequestParam String orderNo,
            @RequestParam Long userId) {
        // 查询订单，条件：订单号匹配 + 用户ID匹配（归属校验）
        OrderInfo orderInfo = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getOrderNo, orderNo)
                        .eq(OrderInfo::getUserId, userId)
                        .select(OrderInfo::getPayAmount, OrderInfo::getStatus)
        );
        if (orderInfo == null) {
            log.warn("反查订单金额失败，订单不存在或不属于该用户: orderNo={}, userId={}", orderNo, userId);
            return Result.fail("订单不存在");
        }
        log.info("反查订单金额成功: orderNo={}, payAmount={}", orderNo, orderInfo.getPayAmount());
        return Result.success(orderInfo.getPayAmount());
    }
}
