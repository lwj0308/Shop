package com.shop.model.merchant.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家审核状态响应
 * <p>
 * 商家查询自己审核状态时返回的数据。
 * 包含审核状态和管理员的审核意见，让商家知道申请进展。
 * </p>
 */
@Data
public class MerchantAuditVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家ID */
    private Long merchantId;

    /** 审核状态：0待审核 1已通过 2已拒绝 3已禁用 */
    private Integer status;

    /** 审核备注，管理员填写的审核意见 */
    private String auditNote;

    /** 状态描述，直接告诉商家当前是什么状态，比如"待审核"、"已通过" */
    private String statusDesc;
}
