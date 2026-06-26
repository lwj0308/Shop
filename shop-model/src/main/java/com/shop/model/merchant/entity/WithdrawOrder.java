package com.shop.model.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现申请实体
 * <p>
 * 对应数据库 withdraw_order 表，记录商家申请提现的记录。
 * 商家在结算管理页面发起提现申请，管理员审核通过后打款。
 * </p>
 * <p>
 * 提现流程：
 * 1. 商家发起提现 → 金额从 balance 转入 frozen_amount（冻结）
 * 2. 管理员审核通过 → frozen_amount 扣除（钱已打款）
 * 3. 管理员审核拒绝 → frozen_amount 转回 balance（解冻）
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("withdraw_order")
public class WithdrawOrder extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** 提现金额（元） */
    private BigDecimal amount;

    /** 状态：0-待审核 1-已通过 2-已拒绝 3-已打款 */
    private Integer status;

    /** 银行名称（申请时快照，防止后续修改银行卡影响） */
    private String bankName;

    /** 银行账号（申请时快照） */
    private String bankAccount;

    /** 账户名（申请时快照） */
    private String accountName;

    /** 审核备注 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditTime;
}
