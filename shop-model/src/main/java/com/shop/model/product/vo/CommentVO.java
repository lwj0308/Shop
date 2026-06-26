package com.shop.model.product.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价响应VO
 * <p>
 * 返回给前端的评价信息，包含评价内容、评分、图片等。
 * </p>
 */
@Data
public class CommentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评价ID */
    private Long id;

    /** 商品ID */
    private Long productId;

    /** 商品名称（商家端评价管理列表展示用） */
    private String productName;

    /** 用户ID */
    private Long userId;

    /** 用户昵称 */
    private String userNickname;

    /** 用户头像 */
    private String userAvatar;

    /** 评价内容 */
    private String content;

    /** 评价图片列表 */
    private List<String> images;

    /** 评分：1-5分 */
    private Integer score;

    /** 商家回复内容 */
    private String reply;

    /** 是否匿名评价：0否 1是 */
    private Integer isAnonymous;

    /** 评价类型：0初始评价 1追评 */
    private Integer commentType;

    /** 父评价ID（追评时指向初始评价ID） */
    private Long parentId;

    /** 追评列表（查询初始评价时附带其追评，初始评价为 null） */
    private List<CommentVO> replyList;

    /** 评价时间 */
    private LocalDateTime createTime;
}
