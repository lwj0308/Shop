package com.shop.model.merchant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提现申请DTO
 * <p>
 * 商家发起提现时提交的参数，只需要提现金额。
 * 银行卡信息从商家已配置的结算账户中获取，不需要重复填写。
 * </p>
 */
@Data
public class WithdrawApplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提现金额（元），必须大于0 */
    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "1.00", message = "提现金额必须大于1元")
    private BigDecimal amount;
}
