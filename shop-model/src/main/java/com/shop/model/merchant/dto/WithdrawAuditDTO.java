package com.shop.model.merchant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提现审核DTO
 * <p>
 * 管理员审核商家提现申请时提交的参数。
 * 审核通过：冻结金额扣减（钱已打款给商家）。
 * 审核拒绝：冻结金额转回可用余额（解冻给商家）。
 * </p>
 */
@Data
public class WithdrawAuditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提现申请ID */
    @NotNull(message = "提现申请ID不能为空")
    private Long id;

    /** 审核结果：1-通过 2-拒绝 */
    @NotNull(message = "审核结果不能为空")
    private Integer status;

    /** 审核备注（拒绝时建议填写原因） */
    private String auditRemark;
}
