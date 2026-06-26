package com.shop.model.product.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评价查询参数DTO
 * <p>
 * 商品详情页查询评价列表时的筛选条件。
 * 支持按评分类型筛选（全部/好评/中评/差评）。
 * </p>
 */
@Data
public class CommentQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评分类型筛选：
     * - all：全部评价（不按分数筛选）
     * - good：好评（4-5分）
     * - medium：中评（3分）
     * - bad：差评（1-2分）
     */
    private String scoreType = "all";

    /** 页码（从1开始） */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
