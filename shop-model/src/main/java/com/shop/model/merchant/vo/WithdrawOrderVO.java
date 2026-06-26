package com.shop.model.merchant.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.shop.model.admin.vo.BankCardDesensitizeSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现申请响应VO
 * <p>
 * 返回给前端的提现申请信息，银行账号通过 @JsonSerialize 强制脱敏。
 * 商家和管理员都能看到提现申请列表。
 * </p>
 */
@Data
public class WithdrawOrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提现申请ID */
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 商家名称（管理端展示用） */
    private String merchantName;

    /** 提现金额（元） */
    private BigDecimal amount;

    /** 状态：0-待审核 1-已通过 2-已拒绝 3-已打款 */
    private Integer status;

    /** 状态描述（中文） */
    private String statusDesc;

    /** 银行名称 */
    private String bankName;

    /** 银行账号（序列化时强制脱敏，比如 6222********7890） */
    @JsonSerialize(using = BankCardDesensitizeSerializer.class)
    private String bankAccount;

    /** 账户名 */
    private String accountName;

    /** 审核备注 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
