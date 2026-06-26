package com.shop.model.promotion.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 满减活动状态枚举
 * <p>
 * 满减活动的生命周期状态（和优惠券状态一致）：
 * - PENDING：待生效，创建后还未到开始时间
 * - ACTIVE：进行中，在活动时间窗口内，下单可享满减
 * - ENDED：已结束，超过活动结束时间，不再生效
 * - OFFLINE：已下架，商家/管理员手动下架，不再生效
 * </p>
 */
@Getter
@AllArgsConstructor
public enum PromotionStatusEnum {

    /** 待生效：还未到活动开始时间 */
    PENDING(0, "待生效"),
    /** 进行中：在活动时间窗口内 */
    ACTIVE(1, "进行中"),
    /** 已结束：超过活动结束时间 */
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
    public static PromotionStatusEnum getByCode(int code) {
        for (PromotionStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
