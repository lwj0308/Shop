package com.shop.model.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商家结算账户实体
 * <p>
 * 对应数据库 merchant_settlement 表，存储商家的银行账户信息和余额。
 * 用户下单付款后，钱需要结算到商家的银行账户，所以商家必须配置结算账户。
 * balance 字段记录商家可用余额（可提现金额），
 * frozen_amount 字段记录冻结金额（提现申请中的金额）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant_settlement")
public class MerchantSettlement extends BaseEntity {

    /** 商家ID，关联 merchant 表的 id */
    private Long merchantId;

    /** 银行名称，比如"中国工商银行" */
    private String bankName;

    /** 银行账号，商家的收款银行卡号 */
    private String bankAccount;

    /** 账户名，银行卡持卡人姓名 */
    private String accountName;

    /** 可用余额（元），商家可提现的金额 */
    private BigDecimal balance;

    /** 冻结金额（元），提现申请中的金额，提现成功后扣除 */
    private BigDecimal frozenAmount;
}
