package com.shop.model.payment.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 支付状态枚举（状态机模式）
 * <p>
 * 支付状态流转：
 * 待支付 → 支付中（用户正在付款）
 * 支付中 → 已支付（付款成功）
 * 支付中 → 已失败（付款失败）
 * 待支付 → 已关闭（超时未支付，自动关闭）
 * 已支付 → 退款中（正在退款）
 * 退款中 → 已退款（退款成功）
 * </p>
 */
@Getter
@AllArgsConstructor
public enum PayStatusEnum {

    /** 待支付：刚创建支付记录，等用户付款 */
    WAIT(0, "待支付"),
    /** 支付中：用户正在付款（调用第三方支付中） */
    PAYING(1, "支付中"),
    /** 已支付：付款成功 */
    PAID(2, "已支付"),
    /** 已关闭：超时未支付，自动关闭 */
    CLOSED(3, "已关闭"),
    /** 已失败：付款失败 */
    FAILED(4, "已失败"),
    /** 退款中：正在退款 */
    REFUNDING(5, "退款中"),
    /** 已退款：退款成功 */
    REFUNDED(6, "已退款");

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
    public static PayStatusEnum getByCode(int code) {
        for (PayStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 校验支付状态转换是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true=允许转换
     */
    public static boolean canTransit(PayStatusEnum from, PayStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case WAIT -> Arrays.asList(PAYING, CLOSED).contains(to);
            case PAYING -> Arrays.asList(PAID, FAILED).contains(to);
            // MVP阶段：已支付可以直接到已退款（没有真实退款接口）
            // 后续接入真实退款后：已支付 → 退款中 → 已退款
            case PAID -> Arrays.asList(REFUNDING, REFUNDED).contains(to);
            case REFUNDING -> Arrays.asList(REFUNDED).contains(to);
            // 已关闭、已失败、已退款是终态
            case CLOSED, FAILED, REFUNDED -> false;
        };
    }

    /**
     * 校验支付状态转换，不合法则抛异常
     *
     * @param from   当前状态
     * @param to     目标状态
     * @param action 操作描述
     */
    public static void checkTransit(PayStatusEnum from, PayStatusEnum to, String action) {
        if (!canTransit(from, to)) {
            throw new IllegalArgumentException(
                    String.format("支付状态不允许从[%s]转换为[%s]，操作：%s", from.getDesc(), to.getDesc(), action));
        }
    }
}
