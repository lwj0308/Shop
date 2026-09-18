package com.shop.order.util;

import com.shop.model.order.entity.OrderLog;
import com.shop.order.mapper.OrderLogMapper;

/**
 * 订单状态日志记录工具
 * <p>
 * 每次订单状态变化都要写一条 t_order_log，方便追溯订单的完整生命周期。
 * 下单、发货、退款、秒杀下单等多处都要写这条日志，字段组装完全一样，
 * 因此统一收敛到这里，避免同一个方法在几个类里各抄一遍。
 * </p>
 */
public final class OrderLogRecorder {

    private OrderLogRecorder() {
    }

    /**
     * 写入一条订单状态变更日志
     *
     * @param orderLogMapper 订单日志Mapper
     * @param orderId        订单ID
     * @param orderNo        订单号
     * @param fromStatus     变化前的状态（首次创建为null）
     * @param toStatus       变化后的状态
     * @param action         操作类型
     * @param operatorId     操作人ID
     * @param operatorType   操作人类型：1用户 2商家 3系统
     * @param note           备注
     */
    public static void record(OrderLogMapper orderLogMapper, Long orderId, String orderNo,
                              Integer fromStatus, Integer toStatus, String action,
                              Long operatorId, Integer operatorType, String note) {
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
