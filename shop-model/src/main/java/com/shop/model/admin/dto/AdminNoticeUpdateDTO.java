package com.shop.model.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑公告请求参数
 * <p>
 * 修改公告信息时提交的数据，和新增不同，所有字段都是可选的，
 * 只传需要修改的字段即可。
 * </p>
 */
@Data
public class AdminNoticeUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公告标题，最长200个字符 */
    @Size(max = 200, message = "公告标题长度不能超过200个字符")
    private String title;

    /** 公告内容，公告的详细文字内容 */
    private String content;

    /** 类型：1通知 2活动 3维护 */
    private Integer type;

    /** 状态：0禁用 1正常 */
    private Integer status;
}
