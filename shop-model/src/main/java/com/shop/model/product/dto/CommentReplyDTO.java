package com.shop.model.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家回复评价DTO
 * <p>
 * 商家回复用户评价时提交的参数，包含评价ID和回复内容。
 * </p>
 */
@Data
public class CommentReplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评价ID，表示回复的是哪条评价 */
    @NotNull(message = "评价ID不能为空")
    private Long commentId;

    /** 回复内容，商家写的回复文字 */
    @NotBlank(message = "回复内容不能为空")
    private String reply;
}
