package com.shop.model.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 追评请求参数DTO
 * <p>
 * 用户在初始评价之后，可以追加一条追评。
 * 追评关联到初始评价（通过 parentId）。
 * </p>
 */
@Data
public class CommentAppendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父评价ID（初始评价的ID，追评必须关联到一条初始评价） */
    @NotNull(message = "父评价ID不能为空")
    private Long parentId;

    /** 追评内容 */
    @NotBlank(message = "追评内容不能为空")
    @Size(max = 500, message = "追评内容最长500个字符")
    private String content;

    /** 追评图片列表 */
    private List<String> images;
}
