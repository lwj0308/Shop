package com.shop.seckill.feign;

import com.shop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 商家服务 Feign 客户端
 * <p>
 * 秒杀服务通过这个接口远程调用 shop-merchant 服务，
 * 用于通过 userId 获取 merchantId 等商家信息。
 * </p>
 * <p>
 * 小白讲解：秒杀服务拆分出来后，不再直接持有 MerchantService，
 * 而是通过 Feign "打电话" 给 shop-merchant 服务查询商家信息。
 * </p>
 */
@FeignClient(name = "shop-merchant")
public interface MerchantFeignClient {

    /**
     * 根据用户ID获取商家ID（内部接口）
     * <p>
     * 商家在 shop-merchant 登录后，秒杀服务需要通过 userId 反查 merchantId，
     * 用来校验"这个商家能创建/下架秒杀活动"。
     * </p>
     * <p>
     * 小白讲解：商家创建秒杀活动时，前端只带了用户的登录态（userId），
     * 秒杀服务拿着 userId 去问 shop-merchant "这个用户是哪个商家的？"，
     * 拿到 merchantId 后才能把活动挂到这个商家名下。
     * </p>
     *
     * @param userId 用户ID（通过请求头 X-User-Id 传递）
     * @return 商家ID；如果该用户没有入驻商家则返回 null
     */
    @GetMapping("/merchant/inner/merchant-id")
    Result<Long> getMerchantIdByUserId(@RequestHeader("X-User-Id") Long userId);
}
