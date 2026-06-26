package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Banner管理实体
 * <p>
 * 对应数据库 admin_banner 表，存储首页轮播图信息。
 * Banner就是首页顶部滚动的那些大图，用来展示促销活动、推荐商品等。
 * 通过sort字段控制显示顺序，status字段控制是否启用。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_banner")
public class AdminBanner extends BaseEntity {

    /** Banner标题，比如"双十一大促"、"新品首发" */
    private String title;

    /** 图片地址，Banner图片的URL链接 */
    private String image;

    /** 跳转链接，点击Banner后跳转到的页面地址，可以为空 */
    private String link;

    /** 排序号，数字越小越靠前显示，比如0就排在1的前面 */
    private Integer sort;

    /** 状态：0禁用 1正常（禁用后前端不会显示这个Banner） */
    private Integer status;
}
