package com.shop.model.coupon.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠券类型枚举
 * <p>
 * 把优惠券类型从"魔法数字"变成枚举，代码可读性更好。
 * 比如 CouponTypeEnum.FULL_REDUCTION 比 1 清晰多了。
 * </p>
 * <p>
 * 各类型说明：
 * - FULL_REDUCTION：满减券，订单金额满 threshold 元时减 amount 元（如满100减20）
 * - DISCOUNT：折扣券，按 amount 折结算（如 amount=0.85 表示85折）
 * - DIRECT_DISCOUNT：立减券，无门槛直接减 amount 元
 * </p>
 */
@Getter
@AllArgsConstructor
public enum CouponTypeEnum {

    /** 满减券：满 threshold 元减 amount 元 */
    FULL_REDUCTION(1, "满减"),
    /** 折扣券：打 amount 折（如 0.85 表示85折） */
    DISCOUNT(2, "折扣"),
    /** 立减券：无门槛直接减 amount 元 */
    DIRECT_DISCOUNT(3, "立减");

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
    public static CouponTypeEnum getByCode(int code) {
        for (CouponTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
