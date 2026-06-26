package com.shop.model.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 品牌请求参数
 * <p>
 * 前端调用添加/修改品牌接口时传的参数。
 * </p>
 */
@Data
public class BrandDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 品牌名称，比如"苹果"、"华为" */
    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 50, message = "品牌名称最长50个字符")
    private String name;

    /** 品牌Logo图片URL */
    @Size(max = 255, message = "Logo URL最长255个字符")
    private String logo;

    /** 品牌描述，介绍品牌的故事和特色 */
    @Size(max = 500, message = "品牌描述最长500个字符")
    private String description;
}
