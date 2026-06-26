package com.shop.model.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 结算账户参数
 * <p>
 * 商家配置银行账户信息时使用的参数。
 * 用户下单付款后，钱需要结算到商家的银行账户，所以这些信息必须准确。
 * </p>
 */
@Data
public class MerchantSettlementDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 银行名称，比如"中国工商银行"，不能为空 */
    @NotBlank(message = "银行名称不能为空")
    private String bankName;

    /** 银行账号，商家的收款银行卡号，不能为空 */
    @NotBlank(message = "银行账号不能为空")
    private String bankAccount;

    /** 账户名，银行卡持卡人姓名，必须和银行卡上的一致 */
    @NotBlank(message = "账户名不能为空")
    private String accountName;
}
