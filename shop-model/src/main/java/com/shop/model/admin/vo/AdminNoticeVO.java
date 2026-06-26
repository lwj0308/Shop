package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公告信息响应数据
 * <p>
 * 返回给前端的公告详细信息，用于公告管理页面展示。
 * 包含公告的基本信息和创建时间。
 * </p>
 */
@Data
public class AdminNoticeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公告ID */
    private Long id;

    /** 公告标题，比如"系统维护通知" */
    private String title;

    /** 公告内容，公告的详细文字内容 */
    private String content;

    /** 类型：1通知 2活动 3维护 */
    private Integer type;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
