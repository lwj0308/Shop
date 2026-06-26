package com.shop.model.merchant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商家状态枚举
 * <p>
 * 把商家状态从魔法数字（0、1、2、3）变成有意义的枚举值，
 * 这样代码里看到 PENDING 就知道是"待审核"，不用再猜 0 代表什么了。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum MerchantStatusEnum {

    /** 待审核：商家刚提交入驻申请，等管理员审核 */
    PENDING(0, "待审核"),

    /** 已通过：管理员审核通过，商家可以正常经营 */
    APPROVED(1, "已通过"),

    /** 已拒绝：管理员审核拒绝，商家需要修改后重新提交 */
    REJECTED(2, "已拒绝"),

    /** 已禁用：商家违规被管理员封禁，不能经营 */
    DISABLED(3, "已禁用");

    /** 状态值，对应数据库里的 status 字段 */
    private final int code;

    /** 状态描述，给人看的中文说明 */
    private final String desc;

    /**
     * 根据状态值获取枚举
     * <p>
     * 从数据库查出 status=1，用这个方法转成枚举，方便做业务判断。
     * </p>
     *
     * @param code 状态值
     * @return 对应的枚举，找不到返回null
     */
    public static MerchantStatusEnum fromCode(int code) {
        for (MerchantStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据状态值获取描述文字
     * <p>
     * 直接拿到中文描述，比如给前端显示"待审核"、"已通过"等。
     * </p>
     *
     * @param code 状态值
     * @return 状态描述，找不到返回"未知状态"
     */
    public static String getDescByCode(int code) {
        MerchantStatusEnum status = fromCode(code);
        return status != null ? status.desc : "未知状态";
    }
}
