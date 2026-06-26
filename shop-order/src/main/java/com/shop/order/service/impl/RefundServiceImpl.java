package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.order.dto.RefundApplyDTO;
import com.shop.model.order.dto.RefundAuditDTO;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.entity.RefundOrder;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.enums.RefundStatusEnum;
import com.shop.model.order.vo.RefundVO;
import com.shop.order.feign.ProductFeignClient;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderLogMapper;
import com.shop.order.mapper.RefundOrderMapper;
import com.shop.order.service.RefundService;
import com.shop.order.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 退款服务实现类
 * <p>
 * 实现退款的申请、审核和查询功能。
 * 核心优化点：
 * 1. 退款金额校验：退款金额不能超过实付金额
 * 2. 退款状态机：使用枚举定义合法状态转换
 * 3. 退款同意后自动回滚库存
 * 4. 乐观锁更新：防止并发审核导致状态异常
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    /** 退款单Mapper */
    private final RefundOrderMapper refundOrderMapper;

    /** 订单主表Mapper */
    private final OrderInfoMapper orderInfoMapper;

    /** 订单明细Mapper */
    private final OrderItemMapper orderItemMapper;

    /** 订单状态日志Mapper */
    private final OrderLogMapper orderLogMapper;

    /** 商品服务Feign客户端（用于回滚库存） */
    private final ProductFeignClient productFeignClient;

    /** 订单号生成器（也用来生成退款单号） */
    private final OrderNoGenerator orderNoGenerator;

    /** 操作人类型：1用户 2商家 3系统 */
    private static final int OPERATOR_TYPE_USER = 1;
    private static final int OPERATOR_TYPE_MERCHANT = 2;

    /**
     * 申请退款
     * <p>
     * 只有"待发货"状态的订单才能申请退款。
     * 新增退款金额校验：退款金额不能超过该商品的实付金额。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    退款申请参数
     * @return 退款单信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO applyRefund(Long userId, RefundApplyDTO dto) {
        // 查询订单
        OrderInfo order = orderInfoMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        // 校验订单归属权
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作此订单");
        }

        // 使用状态机校验：只有"待发货"状态才能申请退款
        OrderStatusEnum fromStatus = OrderStatusEnum.getByCode(order.getStatus());
        OrderStatusEnum.checkTransit(fromStatus, OrderStatusEnum.REFUNDING, "申请退款");

        // 查询订单明细，获取退款金额
        OrderItem orderItem = orderItemMapper.selectById(dto.getOrderItemId());
        if (orderItem == null || !orderItem.getOrderId().equals(dto.getOrderId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "订单明细不存在");
        }

        // 退款金额校验：退款金额不能超过该商品的实付金额
        // 实付金额 = 小计金额（这里暂无优惠，实付=小计）
        BigDecimal maxRefundAmount = orderItem.getSubtotal();
        if (dto.getRefundAmount() != null && dto.getRefundAmount().compareTo(maxRefundAmount) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "退款金额不能超过实付金额：" + maxRefundAmount + "元");
        }
        // 如果前端没传退款金额，默认退该商品的全额
        BigDecimal refundAmount = dto.getRefundAmount() != null ? dto.getRefundAmount() : maxRefundAmount;

        // 创建退款单
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setRefundNo(orderNoGenerator.generateRefundNo());
        refundOrder.setOrderId(order.getId());
        refundOrder.setOrderNo(order.getOrderNo());
        refundOrder.setOrderItemId(dto.getOrderItemId());
        refundOrder.setUserId(userId);
        refundOrder.setMerchantId(order.getMerchantId());
        refundOrder.setRefundAmount(refundAmount);
        refundOrder.setReason(dto.getReason());
        refundOrder.setStatus(RefundStatusEnum.PENDING.getCode());
        refundOrderMapper.insert(refundOrder);

        // 乐观锁更新订单状态为"退款中"
        int updated = orderInfoMapper.update(null,
                new LambdaUpdateWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, order.getId())
                        .eq(OrderInfo::getStatus, OrderStatusEnum.PAID.getCode())
                        .set(OrderInfo::getStatus, OrderStatusEnum.REFUNDING.getCode())
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR.getCode(), "申请退款失败，订单状态已变更");
        }

        // 记录状态日志
        saveOrderLog(order.getId(), order.getOrderNo(), OrderStatusEnum.PAID.getCode(),
                OrderStatusEnum.REFUNDING.getCode(),
                "申请退款", userId, OPERATOR_TYPE_USER, dto.getReason());

        log.info("退款申请成功: refundNo={}, orderNo={}", refundOrder.getRefundNo(), order.getOrderNo());
        return convertToVO(refundOrder);
    }

    /**
     * 审核退款
     * <p>
     * 商家审核退款申请，可以同意或拒绝。
     * 使用退款状态机校验状态转换合法性。
     * 同意退款后，自动回滚库存。
     * </p>
     *
     * @param dto 退款审核参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditRefund(RefundAuditDTO dto) {
        // 查询退款单
        RefundOrder refundOrder = refundOrderMapper.selectById(dto.getRefundId());
        if (refundOrder == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "退款单不存在");
        }

        // 使用退款状态机校验
        RefundStatusEnum currentStatus = RefundStatusEnum.getByCode(refundOrder.getStatus());

        // 查询关联的订单
        OrderInfo order = orderInfoMapper.selectById(refundOrder.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (dto.getStatus() == RefundStatusEnum.APPROVED.getCode()) {
            // ========== 同意退款 ==========
            // 校验退款状态转换：待审核 → 已同意
            RefundStatusEnum.checkTransit(currentStatus, RefundStatusEnum.APPROVED, "同意退款");

            // 乐观锁更新退款单状态为"已同意"
            int updated = refundOrderMapper.update(null,
                    new LambdaUpdateWrapper<RefundOrder>()
                            .eq(RefundOrder::getId, dto.getRefundId())
                            .eq(RefundOrder::getStatus, RefundStatusEnum.PENDING.getCode())
                            .set(RefundOrder::getStatus, RefundStatusEnum.APPROVED.getCode())
                            .set(RefundOrder::getAuditNote, dto.getAuditNote())
                            .set(RefundOrder::getAuditTime, LocalDateTime.now())
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR.getCode(), "审核失败，退款单状态已变更");
            }

            // 乐观锁更新订单状态为"已退款"
            updated = orderInfoMapper.update(null,
                    new LambdaUpdateWrapper<OrderInfo>()
                            .eq(OrderInfo::getId, order.getId())
                            .eq(OrderInfo::getStatus, OrderStatusEnum.REFUNDING.getCode())
                            .set(OrderInfo::getStatus, OrderStatusEnum.REFUNDED.getCode())
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR.getCode(), "更新订单状态失败，订单状态已变更");
            }

            // 回滚库存
            rollbackStockForRefund(refundOrder);

            // 更新退款单状态为"已退款"
            refundOrderMapper.update(null,
                    new LambdaUpdateWrapper<RefundOrder>()
                            .eq(RefundOrder::getId, dto.getRefundId())
                            .set(RefundOrder::getStatus, RefundStatusEnum.REFUNDED.getCode())
                            .set(RefundOrder::getRefundTime, LocalDateTime.now())
            );

            // 记录状态日志
            saveOrderLog(order.getId(), order.getOrderNo(), OrderStatusEnum.REFUNDING.getCode(),
                    OrderStatusEnum.REFUNDED.getCode(),
                    "退款成功", null, OPERATOR_TYPE_MERCHANT, dto.getAuditNote());

            log.info("退款审核通过: refundNo={}", refundOrder.getRefundNo());

        } else if (dto.getStatus() == RefundStatusEnum.REJECTED.getCode()) {
            // ========== 拒绝退款 ==========
            // 校验退款状态转换：待审核 → 已拒绝
            RefundStatusEnum.checkTransit(currentStatus, RefundStatusEnum.REJECTED, "拒绝退款");

            // 乐观锁更新退款单状态为"已拒绝"
            int updated = refundOrderMapper.update(null,
                    new LambdaUpdateWrapper<RefundOrder>()
                            .eq(RefundOrder::getId, dto.getRefundId())
                            .eq(RefundOrder::getStatus, RefundStatusEnum.PENDING.getCode())
                            .set(RefundOrder::getStatus, RefundStatusEnum.REJECTED.getCode())
                            .set(RefundOrder::getAuditNote, dto.getAuditNote())
                            .set(RefundOrder::getAuditTime, LocalDateTime.now())
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR.getCode(), "审核失败，退款单状态已变更");
            }

            // 乐观锁恢复订单状态为"待发货"
            orderInfoMapper.update(null,
                    new LambdaUpdateWrapper<OrderInfo>()
                            .eq(OrderInfo::getId, order.getId())
                            .eq(OrderInfo::getStatus, OrderStatusEnum.REFUNDING.getCode())
                            .set(OrderInfo::getStatus, OrderStatusEnum.PAID.getCode())
            );

            // 记录状态日志
            saveOrderLog(order.getId(), order.getOrderNo(), OrderStatusEnum.REFUNDING.getCode(),
                    OrderStatusEnum.PAID.getCode(),
                    "退款被拒绝", null, OPERATOR_TYPE_MERCHANT, dto.getAuditNote());

            log.info("退款审核拒绝: refundNo={}", refundOrder.getRefundNo());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "审核状态参数错误，只能是1（同意）或2（拒绝）");
        }
    }

    /**
     * 获取退款列表
     *
     * @param orderId 订单ID
     * @return 退款列表
     */
    @Override
    public List<RefundVO> getRefundList(Long orderId) {
        List<RefundOrder> refundOrders = refundOrderMapper.selectList(
                new LambdaQueryWrapper<RefundOrder>()
                        .eq(RefundOrder::getOrderId, orderId)
                        .orderByDesc(RefundOrder::getCreateTime)
        );
        return refundOrders.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 退款时回滚库存
     * <p>
     * 根据退款单关联的订单明细，把对应SKU的库存加回去。
     * 如果回滚失败，记录错误日志，可以通过定时任务补偿。
     * </p>
     *
     * @param refundOrder 退款单
     */
    private void rollbackStockForRefund(RefundOrder refundOrder) {
        OrderItem orderItem = orderItemMapper.selectById(refundOrder.getOrderItemId());
        if (orderItem != null) {
            try {
                productFeignClient.addStock(orderItem.getSkuId(), orderItem.getQuantity());
            } catch (Exception e) {
                log.error("退款库存回滚失败: skuId={}, quantity={}，需要通过定时任务补偿",
                        orderItem.getSkuId(), orderItem.getQuantity(), e);
            }
        }
    }

    /**
     * 保存订单状态日志
     *
     * @param orderId      订单ID
     * @param orderNo      订单号
     * @param fromStatus   变化前状态
     * @param toStatus     变化后状态
     * @param action       操作类型
     * @param operatorId   操作人ID
     * @param operatorType 操作人类型
     * @param note         备注
     */
    private void saveOrderLog(Long orderId, String orderNo, Integer fromStatus, Integer toStatus,
                              String action, Long operatorId, Integer operatorType, String note) {
        com.shop.model.order.entity.OrderLog orderLog = new com.shop.model.order.entity.OrderLog();
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

    /**
     * 获取退款状态描述
     * <p>
     * 使用枚举替代原来的switch，更优雅也更安全。
     * </p>
     *
     * @param status 退款状态码
     * @return 状态描述
     */
    private String getRefundStatusDesc(Integer status) {
        if (status == null) return "未知";
        RefundStatusEnum statusEnum = RefundStatusEnum.getByCode(status);
        return statusEnum != null ? statusEnum.getDesc() : "未知";
    }

    /**
     * RefundOrder转RefundVO
     *
     * @param refundOrder 退款单实体
     * @return 退款单VO
     */
    private RefundVO convertToVO(RefundOrder refundOrder) {
        RefundVO vo = new RefundVO();
        vo.setId(refundOrder.getId());
        vo.setRefundNo(refundOrder.getRefundNo());
        vo.setOrderId(refundOrder.getOrderId());
        vo.setOrderNo(refundOrder.getOrderNo());
        vo.setOrderItemId(refundOrder.getOrderItemId());
        vo.setRefundAmount(refundOrder.getRefundAmount());
        vo.setReason(refundOrder.getReason());
        vo.setStatus(refundOrder.getStatus());
        vo.setStatusDesc(getRefundStatusDesc(refundOrder.getStatus()));
        vo.setAuditNote(refundOrder.getAuditNote());
        vo.setAuditTime(refundOrder.getAuditTime());
        vo.setRefundTime(refundOrder.getRefundTime());
        vo.setCreateTime(refundOrder.getCreateTime());
        return vo;
    }
}
