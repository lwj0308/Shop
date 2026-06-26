package com.shop.model.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知类型枚举
 * <p>
 * 把通知类型从"魔法数字"变成枚举，代码可读性更好。
 * 比如 NotificationTypeEnum.ORDER 比 1 清晰多了。
 * </p>
 * <p>
 * 各类型说明：
 * - ORDER：订单相关通知（下单、发货、确认收货等）
 * - PAY：支付相关通知（支付成功、支付失败）
 * - REFUND：退款相关通知（退款申请、退款审核结果）
 * - MERCHANT_AUDIT：商家审核通知（入驻申请通过/拒绝）
 * - WITHDRAW：提现通知（提现申请、提现审核结果）
 * - SYSTEM：系统通知（公告、活动等）
 * </p>
 */
@Getter
@AllArgsConstructor
public enum NotificationTypeEnum {

    /** 订单相关通知 */
    ORDER(1, "订单"),
    /** 支付相关通知 */
    PAY(2, "支付"),
    /** 退款相关通知 */
    REFUND(3, "退款"),
    /** 商家审核通知 */
    MERCHANT_AUDIT(4, "商家审核"),
    /** 提现通知 */
    WITHDRAW(5, "提现"),
    /** 系统通知 */
    SYSTEM(6, "系统");

    /** 类型码，和数据库里存的数字一致 */
    private final int code;
    /** 类型描述，给前端展示用 */
    private final String desc;

    /**
     * 根据类型码获取枚举
     *
     * @param code 类型码
     * @return 对应的枚举，找不到返回 null
     */
    public static NotificationTypeEnum getByCode(int code) {
        for (NotificationTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
