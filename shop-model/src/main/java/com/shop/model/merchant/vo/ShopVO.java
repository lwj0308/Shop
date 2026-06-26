package com.shop.model.merchant.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 店铺信息响应
 * <p>
 * 返回给前端的店铺信息，包含店铺的基本展示数据。
 * 店铺是商家对外的窗口，用户浏览商品时看到的就是店铺信息。
 * </p>
 */
@Data
public class ShopVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 店铺ID */
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 店铺名称 */
    private String name;

    /** 店铺Logo图片地址 */
    private String logo;

    /** 店铺Banner图片地址 */
    private String banner;

    /** 店铺描述 */
    private String description;

    /** 状态：0关闭 1正常 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
