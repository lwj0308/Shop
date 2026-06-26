package com.shop.model.product.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评价类型枚举
 * <p>
 * 区分用户首次评价和追加评价（追评）：
 * - INITIAL：初始评价，用户首次对订单商品发表的评价
 * - APPEND：追评，用户在初始评价之后追加的评价
 * </p>
 * <p>
 * 追评通过 parent_id 关联到初始评价，查询初始评价时会附带其追评列表。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum CommentTypeEnum {

    /** 初始评价：用户首次发表的评价 */
    INITIAL(0, "初始评价"),
    /** 追评：在初始评价之后追加的评价 */
    APPEND(1, "追评");

    /** 类型码 */
    private final int code;
    /** 类型描述 */
    private final String desc;

    /**
     * 根据类型码获取枚举
     */
    public static CommentTypeEnum getByCode(int code) {
        for (CommentTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
