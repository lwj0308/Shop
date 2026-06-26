package com.shop.model.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑Banner请求参数
 * <p>
 * 修改Banner信息时提交的数据，和新增不同，所有字段都是可选的，
 * 只传需要修改的字段即可。
 * </p>
 */
@Data
public class AdminBannerUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Banner标题，最长100个字符 */
    @Size(max = 100, message = "Banner标题长度不能超过100个字符")
    private String title;

    /** 图片地址，Banner图片的URL链接 */
    private String image;

    /** 跳转链接，点击Banner后跳转到的页面地址 */
    private String link;

    /** 排序号，数字越小越靠前显示 */
    private Integer sort;

    /** 状态：0禁用 1正常 */
    private Integer status;
}
