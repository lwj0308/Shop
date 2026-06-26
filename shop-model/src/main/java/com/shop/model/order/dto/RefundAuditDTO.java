package com.shop.model.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 退款审核请求参数
 * <p>
 * 商家审核退款申请时使用的参数，可以同意或拒绝。
 * status=1表示同意退款，status=2表示拒绝退款。
 * </p>
 */
@Data
public class RefundAuditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退款单ID（要审核哪个退款申请） */
    @NotNull(message = "退款单ID不能为空")
    private Long refundId;

    /** 审核结果：1同意 2拒绝 */
    @NotNull(message = "审核结果不能为空")
    private Integer status;

    /** 审核备注（商家填的说明，比如"同意退款"或"不符合退款条件"） */
    private String auditNote;
}
