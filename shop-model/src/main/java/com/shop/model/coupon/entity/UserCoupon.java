package com.shop.model.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券实体
 * <p>
 * 对应数据库 user_coupon 表，存储用户领取的优惠券记录。
 * 包含优惠券模板的冗余信息（名称/面额/门槛等），这样查询"我的优惠券"时
 * 不需要跨服务调用 shop-merchant 查询模板信息，提升性能。
 * </p>
 * <p>
 * 注意：本表没有 deleted 逻辑删除字段（用户券不删除，只标记状态），
 * 所以不继承 BaseEntity，自己管理 id/createTime/updateTime 字段。
 * </p>
 */
@Data
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户券ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 优惠券模板ID */
    private Long couponId;

    /** 商家ID（冗余，0表示平台券） */
    private Long merchantId;

    /** 优惠券名称（冗余，避免跨服务查询） */
    private String couponName;

    /** 优惠券类型（冗余）：1满减 2折扣 3立减 */
    private Integer couponType;

    /** 面额（冗余） */
    private BigDecimal amount;

    /** 使用门槛（冗余） */
    private BigDecimal threshold;

    /** 有效期开始（冗余） */
    private LocalDateTime validStartTime;

    /** 有效期结束（冗余） */
    private LocalDateTime validEndTime;

    /** 状态：0未使用 1已使用 2已过期（对应 UserCouponStatusEnum） */
    private Integer status;

    /** 使用的订单号（核销时写入） */
    private String orderNo;

    /** 领取时间 */
    private LocalDateTime getTime;

    /** 使用时间（核销时写入） */
    private LocalDateTime useTime;

    /** 创建时间（新增时自动填充） */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（新增和修改时自动填充） */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
