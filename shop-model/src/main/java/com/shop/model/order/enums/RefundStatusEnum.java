package com.shop.model.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 退款状态枚举（状态机模式）
 * <p>
 * 退款也有自己的状态流转，不能随便改：
 * 待审核 → 已同意（商家同意退款）
 * 待审核 → 已拒绝（商家拒绝退款）
 * 已同意 → 退款中（正在退款，调用支付平台退款接口）
 * 退款中 → 已退款（退款成功，钱已退回用户）
 * </p>
 */
@Getter
@AllArgsConstructor
public enum RefundStatusEnum {

    /** 待审核：用户刚申请退款，等商家审核 */
    PENDING(0, "待审核"),
    /** 已同意：商家同意退款 */
    APPROVED(1, "已同意"),
    /** 已拒绝：商家拒绝退款 */
    REJECTED(2, "已拒绝"),
    /** 退款中：正在调用支付平台退款接口 */
    REFUNDING(3, "退款中"),
    /** 已退款：退款成功，钱已退回用户 */
    REFUNDED(4, "已退款");

    /** 状态码 */
    private final int code;
    /** 状态描述 */
    private final String desc;

    /**
     * 根据状态码获取枚举
     *
     * @param code 状态码
     * @return 对应的枚举
     */
    public static RefundStatusEnum getByCode(int code) {
        for (RefundStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 校验退款状态转换是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true=允许转换
     */
    public static boolean canTransit(RefundStatusEnum from, RefundStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case PENDING -> Arrays.asList(APPROVED, REJECTED).contains(to);
            // MVP阶段：已同意可以直接到已退款（没有真实退款接口）
            // 后续接入真实退款后：已同意 → 退款中 → 已退款
            case APPROVED -> Arrays.asList(REFUNDING, REFUNDED).contains(to);
            case REFUNDING -> Arrays.asList(REFUNDED).contains(to);
            // 已拒绝、已退款是终态
            case REJECTED, REFUNDED -> false;
        };
    }

    /**
     * 校验退款状态转换，不合法则抛异常
     *
     * @param from   当前状态
     * @param to     目标状态
     * @param action 操作描述
     */
    public static void checkTransit(RefundStatusEnum from, RefundStatusEnum to, String action) {
        if (!canTransit(from, to)) {
            throw new IllegalArgumentException(
                    String.format("退款状态不允许从[%s]转换为[%s]，操作：%s", from.getDesc(), to.getDesc(), action));
        }
    }
}
