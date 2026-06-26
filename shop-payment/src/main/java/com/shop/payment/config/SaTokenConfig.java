package com.shop.payment.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.payment.interceptor.UserInfoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token配置类
 * <p>
 * 配置Sa-Token的拦截器，实现接口的登录校验。
 * 所有需要登录的接口，如果没带Token或Token过期，会自动返回401错误。
 * 支付回调接口（/payment/callback）不需要登录，因为是第三方平台调用的。
 * </p>
 * <p>
 * 同时注册UserInfoInterceptor，从Gateway传递的Header中获取用户信息。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    /** 用户信息拦截器，从Gateway Header中获取用户ID */
    private final UserInfoInterceptor userInfoInterceptor;

    /**
     * 注册拦截器
     * <p>
     * 拦截器执行顺序按注册顺序：
     * 1. UserInfoInterceptor：先从Header获取用户信息
     * 2. SaInterceptor：再校验是否登录
     * </p>
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册用户信息拦截器（优先级最高，先获取用户信息）
        registry.addInterceptor(userInfoInterceptor)
                .addPathPatterns("/**");

        // 2. 注册Sa-Token拦截器（校验登录状态）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**") // 拦截所有接口
                .excludePathPatterns(
                        "/doc.html",         // Swagger文档页面
                        "/swagger-resources/**", // Swagger资源
                        "/v3/api-docs/**",   // OpenAPI文档
                        "/webjars/**",       // Swagger依赖的静态资源
                        "/favicon.ico",      // 网站图标
                        "/payment/callback"  // 支付回调接口不需要登录（第三方平台调用的）
                );
    }
}
