package com.shop.model.user.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货地址响应VO
 * <p>
 * 返回给前端的收货地址信息，包含完整的地址详情。
 * 注意：收货人手机号不脱敏，因为用户需要确认收货电话、商家需要联系收货人发货，
 * 脱敏后会导致双方都无法看到完整号码。
 * </p>
 */
@Data
public class AddressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 地址ID */
    private Long id;

    /** 收货人姓名 */
    private String name;

    /** 收货人手机号（不脱敏，用户和商家都需要完整号码） */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区 */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 是否默认：0否 1是 */
    private Integer isDefault;

    /** 创建时间 */
    private LocalDateTime createTime;
}
