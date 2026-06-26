package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增公告请求参数
 * <p>
 * 在后台创建一条新的系统公告时提交的数据，比如发布"系统维护通知"。
 * 标题、内容和类型是必填的，状态可以选填。
 * </p>
 */
@Data
public class AdminNoticeCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公告标题，比如"系统维护通知"，不能为空，最长200个字符 */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题长度不能超过200个字符")
    private String title;

    /** 公告内容，公告的详细文字内容，不能为空 */
    @NotBlank(message = "公告内容不能为空")
    private String content;

    /** 类型：1通知 2活动 3维护，必须指定公告类型 */
    @NotNull(message = "公告类型不能为空")
    private Integer type;

    /** 状态：0禁用 1正常，不填默认为1（正常） */
    private Integer status;
}
