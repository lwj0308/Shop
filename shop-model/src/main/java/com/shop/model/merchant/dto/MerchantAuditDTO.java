package com.shop.model.merchant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家审核参数
 * <p>
 * 管理员审核商家入驻申请时使用的参数。
 * 审核通过(1)后商家才能正常经营，审核拒绝(2)时需要填写拒绝原因。
 * </p>
 */
@Data
public class MerchantAuditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家ID，告诉系统要审核哪个商家 */
    @NotNull(message = "商家ID不能为空")
    private Long merchantId;

    /** 审核结果：1通过 2拒绝，只能选这两个值 */
    @NotNull(message = "审核状态不能为空")
    private Integer status;

    /** 审核备注，拒绝时最好写一下原因，方便商家了解为什么被拒 */
    private String auditNote;
}
