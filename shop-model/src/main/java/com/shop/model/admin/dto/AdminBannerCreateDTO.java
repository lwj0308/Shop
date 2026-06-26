package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增Banner请求参数
 * <p>
 * 在后台创建一个新的Banner时提交的数据，比如上传一张促销活动图。
 * 标题和图片地址是必填的，跳转链接和排序号可以选填。
 * </p>
 */
@Data
public class AdminBannerCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Banner标题，比如"双十一大促"，不能为空，最长100个字符 */
    @NotBlank(message = "Banner标题不能为空")
    @Size(max = 100, message = "Banner标题长度不能超过100个字符")
    private String title;

    /** 图片地址，Banner图片的URL链接，不能为空 */
    @NotBlank(message = "图片地址不能为空")
    private String image;

    /** 跳转链接，点击Banner后跳转到的页面地址，可以为空 */
    private String link;

    /** 排序号，数字越小越靠前显示，不填默认为0 */
    private Integer sort;

    /** 状态：0禁用 1正常，不填默认为1（正常） */
    private Integer status;
}
