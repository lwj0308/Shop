package com.shop.model.product.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品牌响应VO
 * <p>
 * 返回给前端的品牌信息，不包含敏感字段。
 * </p>
 */
@Data
public class BrandVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 品牌ID */
    private Long id;

    /** 品牌名称 */
    private String name;

    /** 品牌Logo图片URL */
    private String logo;

    /** 品牌描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;
}
