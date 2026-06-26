package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.model.user.vo.AddressVO;
import com.shop.order.feign.fallback.UserFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务Feign客户端
 * <p>
 * 通过Feign远程调用用户服务，获取用户的收货地址信息。
 * 下单时需要获取地址信息来创建地址快照。
 * </p>
 */
@FeignClient(name = "shop-user", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 根据地址ID获取地址信息
     * <p>
     * 下单时需要获取用户的收货地址，然后拍快照存到订单地址表里。
     * </p>
     *
     * @param addressId 地址ID
     * @return 地址信息
     */
    @GetMapping("/user/address/{addressId}")
    Result<AddressVO> getAddressById(@PathVariable("addressId") Long addressId);
}
