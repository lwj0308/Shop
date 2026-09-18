package com.shop.marketing.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.interceptor.InnerApiInterceptor;
import com.shop.common.interceptor.TokenUserContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token配置类
 * <p>
 * 配置Sa-Token的拦截器，实现接口的登录校验和权限控制。
 * Sa-Token是一个轻量级的Java权限认证框架，比Spring Security简单很多。
 * 同时配置UserContext拦截器，将用户ID存入ThreadLocal，方便Service层获取。
 * </p>
 * <p>
 * 小白说明：营销服务本身不提供登录接口（登录由 shop-merchant 处理），
 * 但营销服务需要验证商家身份，所以保留 Sa-Token 拦截器（StpUtil.checkLogin）。
 * 商家在 shop-merchant 登录后，token 存在 Redis 中共享，营销服务可以直接校验。
 * </p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 内部接口鉴权拦截器，校验X-Inner-Key防止绕过Gateway直连 */
    @Autowired
    private InnerApiInterceptor innerApiInterceptor;

    /**
     * 注册拦截器
     * <p>
     * 注册三个拦截器：
     * 1. UserContext拦截器：从Sa-Token或请求头中获取用户ID，存入ThreadLocal
     * 2. 内部接口鉴权拦截器：校验X-Inner-Key，防止绕过Gateway直连调用内部接口
     * 3. Sa-Token拦截器：校验是否登录，未登录的请求返回401
     * </p>
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一步：注册UserContext拦截器，将用户ID存入ThreadLocal
        // 这个拦截器必须在Sa-Token拦截器之前执行，这样后续代码才能通过UserContext获取用户ID
        registry.addInterceptor(new TokenUserContextInterceptor()).addPathPatterns("/**");

        // 第二步：注册内部接口鉴权拦截器（校验X-Inner-Key，防止绕过Gateway直连）
        // 同时拦截 /inner/** 和 /admin/** 路径：admin接口仅供shop-admin通过Feign内部调用
        // X-Inner-Key 由 FeignInnerKeyConfig 的 RequestInterceptor 自动注入，仅服务间Feign调用可携带
        registry.addInterceptor(innerApiInterceptor)
                .addPathPatterns("/**/inner/**", "/**/admin/**");

        // 第三步：注册Sa-Token拦截器，校验登录状态
        // 营销服务没有登录接口，但商家在shop-merchant登录后token共享在Redis，这里只做校验
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                // 排除不需要登录的接口
                .excludePathPatterns(
                        "/marketing/coupon/inner/**",       // 优惠券内部接口（供shop-user/shop-order通过Feign调用）
                        "/marketing/promotion/inner/**",    // 满减活动内部接口（供shop-order通过Feign调用计算满减优惠）
                        // ===== 管理端接口仅供shop-admin通过Feign内部调用（由InnerApiInterceptor校验X-Inner-Key） =====
                        "/marketing/**/admin/**",           // 管理端营销接口（优惠券/满减的管理端接口）
                        "/marketing/coupon/public/**",      // 优惠券公开接口（用户端查询可领取的券列表）
                        // ===== Swagger相关 =====
                        "/swagger-ui/**",                   // Swagger UI页面
                        "/swagger-resources/**",            // Swagger资源
                        "/webjars/**",                      // Swagger静态资源
                        "/v3/api-docs/**",                  // OpenAPI文档接口
                        "/doc.html",                        // Swagger文档页面
                        "/favicon.ico",                     // 网站图标
                        "/error"                            // 错误页面
                );
    }
}
