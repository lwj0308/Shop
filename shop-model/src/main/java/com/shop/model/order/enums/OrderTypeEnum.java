package com.shop.model.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单类型枚举
 * <p>
 * 区分普通下单和秒杀下单：
 * - NORMAL：普通订单，用户正常下单购买
 * - SECKILL：秒杀订单，通过秒杀活动下单，价格为秒杀价
 * </p>
 * <p>
 * 秒杀订单取消时需要额外回退 Redis 秒杀库存。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum OrderTypeEnum {

    /** 普通订单：正常下单 */
    NORMAL(1, "普通订单"),
    /** 秒杀订单：通过秒杀活动下单 */
    SECKILL(2, "秒杀订单");

    /** 类型码 */
    private final int code;
    /** 类型描述 */
    private final String desc;

    /**
     * 根据类型码获取枚举
     */
    public static OrderTypeEnum getByCode(int code) {
        for (OrderTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
