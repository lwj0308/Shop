package com.shop.model.coupon.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠券模板状态枚举
 * <p>
 * 优惠券模板的生命周期状态：
 * - PENDING：待生效，创建后还未到领取开始时间
 * - ACTIVE：进行中，在领取时间窗口内，用户可以领取
 * - ENDED：已结束，超过领取结束时间，不能再领取
 * - OFFLINE：已下架，商家/管理员手动下架，不能再领取
 * </p>
 */
@Getter
@AllArgsConstructor
public enum CouponStatusEnum {

    /** 待生效：还未到领取开始时间 */
    PENDING(0, "待生效"),
    /** 进行中：在领取时间窗口内 */
    ACTIVE(1, "进行中"),
    /** 已结束：超过领取结束时间 */
    ENDED(2, "已结束"),
    /** 已下架：手动下架 */
    OFFLINE(3, "已下架");

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
    public static CouponStatusEnum getByCode(int code) {
        for (CouponStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
