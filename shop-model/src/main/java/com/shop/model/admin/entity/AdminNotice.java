package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公告管理实体
 * <p>
 * 对应数据库 admin_notice 表，存储系统公告信息。
 * 公告就是管理员发布的系统通知，比如"系统维护通知"、"活动公告"等。
 * 通过type字段区分公告类型，status字段控制是否启用。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_notice")
public class AdminNotice extends BaseEntity {

    /** 公告标题，比如"系统维护通知"、"618活动公告" */
    private String title;

    /** 公告内容，公告的详细文字内容 */
    private String content;

    /** 类型：1通知 2活动 3维护（不同类型前端可以用不同样式展示） */
    private Integer type;

    /** 状态：0禁用 1正常（禁用后前端不会显示这条公告） */
    private Integer status;
}
