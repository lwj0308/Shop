package com.shop.model.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 取消订单请求参数
 * <p>
 * 用户取消订单时，需要填写取消原因。
 * 只有"待付款"状态的订单才能取消。
 * </p>
 */
@Data
public class OrderCancelDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 取消原因（比如"不想买了"、"价格太贵了"） */
    @NotBlank(message = "取消原因不能为空")
    private String reason;
}
