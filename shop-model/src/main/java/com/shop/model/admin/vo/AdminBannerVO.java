package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Banner信息响应数据
 * <p>
 * 返回给前端的Banner详细信息，用于Banner管理页面展示。
 * 包含Banner的基本信息和创建时间。
 * </p>
 */
@Data
public class AdminBannerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Banner ID */
    private Long id;

    /** Banner标题，比如"双十一大促" */
    private String title;

    /** 图片地址，Banner图片的URL链接 */
    private String image;

    /** 跳转链接，点击Banner后跳转到的页面地址 */
    private String link;

    /** 排序号，数字越小越靠前显示 */
    private Integer sort;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
