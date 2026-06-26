package com.shop.model.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发货请求参数
 * <p>
 * 商家发货时填写的物流信息，包括快递单号和快递公司。
 * 只有"待发货"状态的订单才能发货。
 * </p>
 */
@Data
public class DeliveryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单ID（要给哪个订单发货） */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 快递单号（快递公司给的追踪号，比如"SF1234567890"） */
    @NotBlank(message = "快递单号不能为空")
    private String logisticsNo;

    /** 快递公司名称（比如"顺丰速运"、"中通快递"） */
    @NotBlank(message = "快递公司不能为空")
    private String logisticsCompany;
}
