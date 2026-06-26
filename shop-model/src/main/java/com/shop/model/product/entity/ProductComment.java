package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 商品评价实体
 * <p>
 * 对应数据库 product_comment 表，存储用户购买商品后的评价。
 * 用户可以打分（1-5分）、写评价内容、上传图片。
 * 商家也可以回复用户的评价。
 * images字段用JSON格式存储评价图片列表，比如["img1.jpg","img2.jpg"]。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "product_comment", autoResultMap = true)
public class ProductComment extends BaseEntity {

    /** 商品ID，关联product表，表示评价的是哪个商品 */
    private Long productId;

    /** 订单明细ID，关联order_item表，表示评价的是哪个订单中的商品 */
    private Long orderItemId;

    /** 用户ID，关联user表，表示是谁发表的评价 */
    private Long userId;

    /** 评价内容，用户写的文字评价 */
    private String content;

    /** 评价图片列表（JSON数组），用户上传的评价图片 */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 评分：1-5分，5分是好评，1分是差评 */
    private Integer score;

    /** 商家回复内容，商家对用户评价的回复 */
    private String reply;

    /** 是否匿名评价：0否 1是（匿名时展示昵称显示"匿名用户"） */
    private Integer isAnonymous;

    /** 评价类型：0初始评价 1追评（追评时 parent_id 指向初始评价） */
    private Integer commentType;

    /** 父评价ID（追评时指向初始评价ID，初始评价为 null） */
    private Long parentId;

    // ===== 以下字段不对应数据库列，仅用于JOIN查询时接收关联表的数据 =====

    /** 商品名称（JOIN product 表获取，非数据库字段） */
    @TableField(exist = false)
    private String productName;

    /** 店铺ID（JOIN product 表获取，非数据库字段，用于校验评价归属） */
    @TableField(exist = false)
    private Long shopId;
}
