package com.shop.model.coupon.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 核销优惠券请求DTO
 * <p>
 * shop-order 下单时通过 Feign 调用 shop-user 内部接口核销优惠券，
 * 把用户ID、用户券ID、订单号、订单金额封装在此 DTO 中传过去。
 * shop-user 核销成功后返回优惠金额（discountAmount），shop-order 用于计算实付金额。
 * </p>
 * <p>
 * 使用示例（shop-order 下单核销）：
 * <pre>
 * CouponUseDTO dto = new CouponUseDTO();
 * dto.setUserId(userId);
 * dto.setUserCouponId(couponId);
 * dto.setOrderNo(orderNo);
 * dto.setOrderAmount(totalAmount);
 * BigDecimal discount = couponFeignClient.useCoupon(dto);
 * // payAmount = totalAmount - discount
 * </pre>
 * </p>
 */
@Data
public class CouponUseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户券ID（user_coupon 表的主键，不是 coupon 模板ID） */
    private Long userCouponId;

    /** 订单号 */
    private String orderNo;

    /** 订单金额（用于校验满减门槛和计算优惠金额） */
    private BigDecimal orderAmount;
}
