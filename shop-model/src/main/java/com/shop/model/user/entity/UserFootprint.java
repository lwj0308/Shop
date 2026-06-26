package com.shop.model.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 浏览足迹实体
 * <p>
 * 对应数据库的 user_footprint 表，记录用户浏览过哪些商品。
 * 可以用来做"猜你喜欢"推荐，也可以让用户回顾自己看过的商品。
 * 同一商品重复浏览会更新时间，不会产生重复记录。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_footprint")
public class UserFootprint extends BaseEntity {

    /** 用户ID（谁浏览的） */
    private Long userId;

    /** 商品ID（浏览了哪个商品） */
    private Long productId;

    /** 商品分类ID（冗余字段，记录浏览时的商品分类，用于猜你喜欢推荐按分类查热销） */
    private Long categoryId;
}
