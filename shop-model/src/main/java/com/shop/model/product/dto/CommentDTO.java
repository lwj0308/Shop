package com.shop.model.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 商品评价请求参数
 * <p>
 * 前端调用发表评价接口时传的参数。
 * 用户购买商品后可以打分、写评价、上传图片。
 * </p>
 */
@Data
public class CommentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID，评价的是哪个商品 */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 订单ID，关联order_info表，用于校验订单归属和标记已评价 */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 订单明细ID，评价的是哪个订单中的商品 */
    @NotNull(message = "订单明细ID不能为空")
    private Long orderItemId;

    /** 评价内容，用户写的文字评价 */
    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容最长500个字符")
    private String content;

    /** 评价图片列表，用户上传的评价图片 */
    private List<String> images;

    /** 评分：1-5分，5分是好评，1分是差评 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低1分")
    @Max(value = 5, message = "评分最高5分")
    private Integer score;

    /** 是否匿名评价：false否 true是（默认不匿名） */
    private Boolean isAnonymous;
}
