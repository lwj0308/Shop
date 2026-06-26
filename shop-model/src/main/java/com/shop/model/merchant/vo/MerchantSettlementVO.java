package com.shop.model.merchant.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.shop.model.admin.vo.BankCardDesensitizeSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算账户响应
 * <p>
 * 返回给前端的结算账户信息，银行账号通过 @JsonSerialize 强制脱敏（保留前4位和后4位，中间用*号代替），
 * 防止商家银行卡号泄露。比如 6222021234567890 显示为 6222********7890。
 * 同时返回商家的可用余额和冻结金额，让商家知道能提现多少钱。
 * </p>
 */
@Data
public class MerchantSettlementVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 结算账户ID */
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 银行名称，比如"中国工商银行" */
    private String bankName;

    /** 银行账号（序列化时强制脱敏，比如 6222********7890） */
    @JsonSerialize(using = BankCardDesensitizeSerializer.class)
    private String bankAccount;

    /** 账户名，银行卡持卡人姓名 */
    private String accountName;

    /** 可用余额（元），商家可提现的金额 */
    private BigDecimal balance;

    /** 冻结金额（元），提现申请中的金额 */
    private BigDecimal frozenAmount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
