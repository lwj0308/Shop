package com.shop.model.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收藏实体
 * <p>
 * 对应数据库的 user_favorite 表，记录用户收藏了哪些商品。
 * 同一个商品不能重复收藏（数据库有唯一索引 uk_user_product 保证）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_favorite")
public class UserFavorite extends BaseEntity {

    /** 用户ID（谁收藏的） */
    private Long userId;

    /** 商品ID（收藏了哪个商品） */
    private Long productId;
}
