package com.shop.model.promotion.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 满减优惠计算请求DTO（内部接口用）
 * <p>
 * shop-order 下单时通过 Feign 调用 shop-merchant 的内部接口，
 * 传入订单信息，由 shop-merchant 计算满减优惠金额并返回。
 * </p>
 * <p>
 * 计算逻辑：
 * - 全店满减：直接用 orderAmount 判断是否达到 threshold
 * - 指定商品满减：从 skuIds 中筛选参与活动的 SKU，计算这些 SKU 的金额是否达到 threshold
 *   （注意：指定商品满减时，orderAmount 是全部商品总额，
 *    但门槛判断基于参与活动的商品金额，需要 shop-merchant 根据 skuIds 反查）
 * </p>
 */
@Data
public class PromotionCalculateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家ID（查询该商家进行中的满减活动） */
    private Long merchantId;

    /** 订单总金额（全部商品的总额） */
    private BigDecimal orderAmount;

    /** 订单中的所有 SKU ID 列表（用于指定商品满减时筛选参与活动的商品） */
    private List<Long> skuIds;
}
