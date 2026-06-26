package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 处理安全事件请求参数
 * <p>
 * 管理员处理安全事件时提交的数据，比如标记为"已处理"或"已忽略"，
 * 并填写处理说明。
 * </p>
 */
@Data
public class AdminSecurityEventHandleDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 处理状态：0未处理 1已处理 2已忽略，不能为空 */
    @NotNull(message = "处理状态不能为空")
    private Integer status;

    /** 处理备注，管理员填写的处理说明，最长200个字符 */
    @Size(max = 200, message = "处理备注长度不能超过200个字符")
    private String handleNote;
}
