package com.shop.model.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 用户账户实体
 * <p>
 * 对应数据库的 user_account 表，存储用户的钱包信息（余额和积分）。
 * 使用乐观锁（version字段）防止并发修改导致数据不一致。
 * 比如：用户同时下单扣余额，不会出现余额变成负数的情况。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_account")
public class UserAccount extends BaseEntity {

    /** 用户ID（和user表的id关联，一个用户只有一个账户） */
    private Long userId;

    /** 余额（单位：元，精确到小数点后两位，避免浮点数精度问题） */
    private BigDecimal balance;

    /** 积分（购物可以攒积分，积分可以抵扣金额） */
    private Integer points;

    /** 乐观锁版本号（每次修改+1，防止并发修改覆盖数据） */
    @Version
    private Integer version;
}
