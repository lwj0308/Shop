package com.shop.model.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 订单状态枚举（状态机模式）
 * <p>
 * 把订单状态从"魔法数字"变成枚举，好处：
 * 1. 代码可读性更好：OrderStatusEnum.UNPAID 比 0 清晰多了
 * 2. 状态转换规则集中管理：哪些状态能转到哪些状态，一目了然
 * 3. 防止非法状态转换：不能从"已取消"再变成"已付款"
 * </p>
 * <p>
 * 状态流转图：
 * 待付款 → 已取消（用户取消/超时自动取消）
 * 待付款 → 待发货（支付成功）
 * 待发货 → 退款中（用户申请退款）
 * 待发货 → 运输中（商家发货）
 * 退款中 → 已退款（商家同意退款）
 * 退款中 → 待发货（商家拒绝退款）
 * 运输中 → 已收货（用户确认收货）
 * 已收货 → 已完成（系统自动完成）
 * </p>
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    /** 待付款：用户刚下单，还没付钱 */
    UNPAID(0, "待付款"),
    /** 已取消：用户主动取消或超时自动取消 */
    CANCELLED(1, "已取消"),
    /** 待发货：用户已付款，等商家发货 */
    PAID(2, "待发货"),
    /** 运输中：商家已发货，快递在路上 */
    SHIPPING(3, "运输中"),
    /** 已收货：用户确认收到商品 */
    RECEIVED(4, "已收货"),
    /** 已完成：订单流程结束 */
    COMPLETED(5, "已完成"),
    /** 退款中：用户申请退款，等商家审核 */
    REFUNDING(6, "退款中"),
    /** 已退款：退款成功，钱已退回 */
    REFUNDED(7, "已退款");

    /** 状态码，和数据库里存的数字一致 */
    private final int code;
    /** 状态描述，给用户看的中文文字 */
    private final String desc;

    /**
     * 根据状态码获取枚举
     * <p>
     * 数据库查出来的是数字，用这个方法转成枚举。
     * 比如从数据库查出 status=2，调用 OrderStatusEnum.getByCode(2) 就得到 PAID。
     * </p>
     *
     * @param code 状态码
     * @return 对应的枚举，找不到返回null
     */
    public static OrderStatusEnum getByCode(int code) {
        for (OrderStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 校验状态转换是否合法
     * <p>
     * 这是状态机的核心：定义哪些状态可以转到哪些状态。
     * 比如不能从"已取消"转到"待发货"，这是不合理的。
     * </p>
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true=允许转换，false=不允许
     */
    public static boolean canTransit(OrderStatusEnum from, OrderStatusEnum to) {
        if (from == null || to == null) {
            return false;
        }
        // 相同状态不需要转换
        if (from == to) {
            return false;
        }

        // 定义每个状态允许转换到的目标状态列表
        return switch (from) {
            case UNPAID -> Arrays.asList(CANCELLED, PAID).contains(to);
            case PAID -> Arrays.asList(REFUNDING, SHIPPING).contains(to);
            case REFUNDING -> Arrays.asList(REFUNDED, PAID).contains(to);
            case SHIPPING -> Arrays.asList(RECEIVED).contains(to);
            case RECEIVED -> Arrays.asList(COMPLETED).contains(to);
            // 已取消、已退款、已完成 是终态，不能再转换
            case CANCELLED, REFUNDED, COMPLETED -> false;
        };
    }

    /**
     * 校验状态转换，不合法则抛出异常
     * <p>
     * 业务代码调用这个方法，如果状态转换不合法会直接抛异常，
     * 不用每次都写 if-else 判断。
     * </p>
     *
     * @param from 当前状态
     * @param to   目标状态
     * @param action 操作描述（比如"取消订单"），用于错误提示
     */
    public static void checkTransit(OrderStatusEnum from, OrderStatusEnum to, String action) {
        if (!canTransit(from, to)) {
            throw new IllegalArgumentException(
                    String.format("订单状态不允许从[%s]转换为[%s]，操作：%s", from.getDesc(), to.getDesc(), action));
        }
    }
}
