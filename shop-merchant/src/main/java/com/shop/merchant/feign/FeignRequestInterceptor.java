package com.shop.merchant.feign;

import cn.dev33.satoken.stp.StpUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign请求拦截器：把当前请求的认证信息透传给下游服务
 * <p>
 * 当商家端通过Feign调用商品服务时，需要把Sa-Token的token和商家ID传递过去，
 * 否则商品服务的 @SaCheckLogin 会报"未登录"，getShopId() 也拿不到店铺ID。
 * </p>
 * <p>
 * 简单理解：就像你帮同事代领快递，得拿着他的取件码（token）和工号（shopId），
 * 快递站（商品服务）才认你。
 * </p>
 */
@Slf4j
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    /**
     * 在Feign发送请求前，往请求头里塞认证信息
     *
     * @param template Feign请求模板，可以往里面加header
     */
    @Override
    public void apply(RequestTemplate template) {
        // 1. 从当前HTTP请求上下文中获取原始请求（就是前端发过来的那个请求）
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            // 2. 把Authorization头（Sa-Token的token）透传给Feign请求
            //    这样商品服务的 @SaCheckLogin 才能验证通过
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isEmpty()) {
                template.header("Authorization", auth);
            }
        }

        // 3. 从Sa-Token Session取出shopId，注入X-Shop-Id头传递给下游服务
        //    小白讲解：商家登录时已经把shopId存进Session了，这里取出来传给商品服务
        //    商品服务拿到X-Shop-Id后，就能校验"你操作的商品是不是你自己店铺的"
        if (StpUtil.isLogin()) {
            Object shopId = StpUtil.getSession().get("shopId");
            if (shopId != null) {
                template.header("X-Shop-Id", String.valueOf(shopId));
            }
        }
    }
}
