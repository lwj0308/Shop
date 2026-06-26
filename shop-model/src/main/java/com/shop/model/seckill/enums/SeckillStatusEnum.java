package com.shop.model.seckill.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 秒杀活动状态枚举
 * <p>
 * 秒杀活动的生命周期状态（和满减/优惠券状态一致）：
 * - PENDING：待生效，创建后还未到开始时间
 * - ACTIVE：进行中，在秒杀时间窗口内，用户可以抢购
 * - ENDED：已结束，超过秒杀结束时间
 * - OFFLINE：已下架，商家/管理员手动下架
 * </p>
 */
@Getter
@AllArgsConstructor
public enum SeckillStatusEnum {

    /** 待生效：还未到秒杀开始时间 */
    PENDING(0, "待生效"),
    /** 进行中：在秒杀时间窗口内，用户可抢购 */
    ACTIVE(1, "进行中"),
    /** 已结束：超过秒杀结束时间 */
    ENDED(2, "已结束"),
    /** 已下架：手动下架 */
    OFFLINE(3, "已下架");

    /** 状态码 */
    private final int code;
    /** 状态描述 */
    private final String desc;

    /**
     * 根据状态码获取枚举
     */
    public static SeckillStatusEnum getByCode(int code) {
        for (SeckillStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
