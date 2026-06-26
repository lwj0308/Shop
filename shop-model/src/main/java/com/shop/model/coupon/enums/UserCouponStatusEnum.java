package com.shop.model.coupon.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户优惠券状态枚举
 * <p>
 * 用户领取优惠券后的状态流转：
 * - UNUSED：未使用，在有效期内可以使用
 * - USED：已使用，下单核销后变为已使用
 * - EXPIRED：已过期，超过有效期未使用
 * </p>
 */
@Getter
@AllArgsConstructor
public enum UserCouponStatusEnum {

    /** 未使用 */
    UNUSED(0, "未使用"),
    /** 已使用 */
    USED(1, "已使用"),
    /** 已过期 */
    EXPIRED(2, "已过期");

    /** 状态码，和数据库里存的数字一致 */
    private final int code;
    /** 状态描述，给前端展示用 */
    private final String desc;

    /**
     * 根据状态码获取枚举
     *
     * @param code 状态码
     * @return 对应的枚举，找不到返回 null
     */
    public static UserCouponStatusEnum getByCode(int code) {
        for (UserCouponStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
