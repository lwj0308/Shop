package com.shop.model.merchant.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.shop.model.admin.vo.PhoneDesensitizeSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家信息响应
 * <p>
 * 返回给前端的商家信息，联系电话通过 @JsonSerialize 强制脱敏（中间4位用*号代替），
 * 防止商家手机号泄露。比如 13812345678 显示为 138****5678。
 * </p>
 */
@Data
public class MerchantVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家ID */
    private Long id;

    /** 商家名称 */
    private String name;

    /** 商家Logo图片地址 */
    private String logo;

    /** 商家描述 */
    private String description;

    /** 联系电话（序列化时强制脱敏，比如 138****5678） */
    @JsonSerialize(using = PhoneDesensitizeSerializer.class)
    private String contactPhone;

    /** 状态：0待审核 1已通过 2已拒绝 3已禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
