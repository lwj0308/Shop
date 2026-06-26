package com.shop.product.feign;

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
 * 当商品服务通过Feign调用商家服务（比如查询店铺的merchantId）时，
 * 需要把上游传过来的Authorization头继续往下传，保证调用链上的身份信息不丢。
 * </p>
 * <p>
 * 简单理解：就像你帮同事代领快递，得拿着他的取件码（token），
 * 快递站（商家服务）才认你，不然就当陌生人拒之门外。
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
        // 1. 从当前HTTP请求上下文中获取原始请求（就是上游发过来的那个请求）
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            // 2. 把Authorization头（Sa-Token的token）透传给Feign请求
            //    这样下游服务在校验登录态时才能验证通过
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isEmpty()) {
                template.header("Authorization", auth);
            }
        }
    }
}
