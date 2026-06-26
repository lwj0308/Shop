package com.shop.order.feign.fallback;

import com.shop.common.result.Result;
import com.shop.model.user.vo.AddressVO;
import com.shop.order.feign.UserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务Feign降级工厂
 * <p>
 * 当用户服务不可用时，走降级逻辑返回友好提示。
 * 比如下单时获取不到地址信息，就告诉用户"用户服务暂时不可用"。
 * </p>
 */
@Slf4j
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的UserFeignClient实例
     */
    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("用户服务调用失败，触发降级", cause);
        return addressId -> Result.fail("用户服务暂时不可用，请稍后重试");
    }
}
