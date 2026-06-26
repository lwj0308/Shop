package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.model.promotion.dto.PromotionCalculateDTO;
import com.shop.order.feign.fallback.PromotionFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/**
 * 满减活动服务Feign客户端
 * <p>
 * shop-order 下单时通过此客户端调用 shop-merchant 的内部接口，
 * 计算当前订单可享受的满减优惠金额。
 * </p>
 * <p>
 * 内部接口路径 /merchant/promotion/inner/** 在 shop-merchant 的 SaTokenConfig 白名单中，不需要鉴权。
 * </p>
 */
@FeignClient(name = "shop-merchant", fallbackFactory = PromotionFeignClientFallbackFactory.class)
public interface PromotionFeignClient {

    /**
     * 计算满减优惠金额
     * <p>
     * 传入商家ID、订单金额和SKU列表，由 shop-merchant 查询进行中的满减活动并计算优惠。
     * 如果没有满足门槛的活动，返回 0。
     * </p>
     *
     * @param dto 计算参数（merchantId, orderAmount, skuIds）
     * @return 满减优惠金额（0表示无满减优惠）
     */
    @PostMapping("/merchant/promotion/inner/calculate")
    Result<BigDecimal> calculatePromotion(@RequestBody PromotionCalculateDTO dto);
}
