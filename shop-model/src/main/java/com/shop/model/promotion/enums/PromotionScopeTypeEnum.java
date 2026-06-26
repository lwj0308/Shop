package com.shop.model.promotion.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 满减活动参与范围枚举
 * <p>
 * 决定满减活动是全店商品都参与，还是只有指定商品参与：
 * - ALL：全店满减，商家所有商品都参与活动
 * - SPECIFIED：指定商品满减，只有关联了的商品才参与活动
 * </p>
 */
@Getter
@AllArgsConstructor
public enum PromotionScopeTypeEnum {

    /** 全店：商家所有商品都参与满减 */
    ALL(1, "全店"),
    /** 指定商品：只有关联的商品参与满减 */
    SPECIFIED(2, "指定商品");

    /** 范围码，和数据库里存的数字一致 */
    private final int code;
    /** 范围描述，给前端展示用 */
    private final String desc;

    /**
     * 根据范围码获取枚举
     *
     * @param code 范围码
     * @return 对应的枚举，找不到返回 null
     */
    public static PromotionScopeTypeEnum getByCode(int code) {
        for (PromotionScopeTypeEnum scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        return null;
    }
}
