package com.shop.marketing.feign;

import com.shop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 商家服务 Feign 客户端
 * <p>
 * 营销服务通过这个接口调用 shop-merchant 服务，获取商家相关信息。
 * 因为营销服务从 shop-merchant 拆分出来后，商家数据还在 shop-merchant，
 * 比如商家创建优惠券时，需要通过 userId 查出对应的 merchantId。
 * </p>
 * <p>
 * 小白说明：Feign 就像"远程方法调用"，你在这个服务里调用 getMerchantIdByUserId，
 * Feign 会自动帮你发 HTTP 请求到 shop-merchant 服务，把结果返回给你，
 * 用起来就像调本地方法一样简单。
 * </p>
 */
@FeignClient(name = "shop-merchant")
public interface MerchantFeignClient {

    /**
     * 通过用户ID获取商家ID（内部接口）
     * <p>
     * 调用 shop-merchant 的 /merchant/inner/merchant-id 接口。
     * 商家在 shop-merchant 登录后，userId 和 merchantId 的对应关系存在那边，
     * 营销服务需要这个映射来校验"你操作的是不是你自己的券/活动"。
     * </p>
     *
     * @param userId 用户ID（从Sa-Token登录态获取）
     * @return 商家ID，如果用户不是商家则返回null
     */
    @GetMapping("/merchant/inner/merchant-id")
    Result<Long> getMerchantIdByUserId(@RequestHeader("X-User-Id") Long userId);
}
