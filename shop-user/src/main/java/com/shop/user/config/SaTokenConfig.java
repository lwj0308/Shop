package com.shop.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.interceptor.InnerApiInterceptor;
import com.shop.user.interceptor.UserInfoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token配置类
 * <p>
 * 配置Sa-Token的拦截器，实现接口的登录校验。
 * 所有需要登录的接口，如果没带Token或Token过期，会自动返回401错误。
 * 认证相关接口（/auth/**）不需要登录，放行处理。
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

    /** 内部接口鉴权拦截器，校验X-Inner-Key防止绕过Gateway直连 */
    private final InnerApiInterceptor innerApiInterceptor;

    /**
     * 注册拦截器
     * <p>
     * 拦截器执行顺序按注册顺序：
     * 1. UserInfoInterceptor：先从Header获取用户信息
     * 2. InnerApiInterceptor：校验内部接口密钥
     * 3. SaInterceptor：再校验是否登录
     * </p>
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册用户信息拦截器（优先级最高，先获取用户信息）
        registry.addInterceptor(userInfoInterceptor)
                .addPathPatterns("/**");

        // 2. 注册内部接口鉴权拦截器（校验X-Inner-Key，防止绕过Gateway直连）
        // 同时拦截 /inner/** 和 /admin/** 路径：admin接口仅供shop-admin通过Feign内部调用
        registry.addInterceptor(innerApiInterceptor)
                .addPathPatterns("/**/inner/**", "/**/admin/**");

        // 3. 注册Sa-Token拦截器（校验登录状态）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**") // 拦截所有接口
                .excludePathPatterns(
                        "/auth/register",    // 注册接口不需要登录
                        "/auth/login",       // 登录接口不需要登录
                        "/auth/refresh",     // 刷新Token接口不需要登录
                        "/auth/wx-login",    // 微信登录接口不需要登录
                        "/user/notification/inner/**", // 消息通知内部接口，供其他微服务Feign调用
                        "/user/coupon/inner/**",       // 用户优惠券内部接口，供shop-order Feign调用
                        "/user/inner/**",             // 用户内部接口，供shop-product评价服务Feign调用：批量查询用户信息
                        "/user/admin/**",             // 管理端接口，仅供shop-admin通过Feign内部调用（由InnerApiInterceptor校验X-Inner-Key）
                        "/doc.html",         // Swagger文档页面
                        "/swagger-resources/**", // Swagger资源
                        "/v3/api-docs/**",   // OpenAPI文档
                        "/webjars/**",       // Swagger依赖的静态资源
                        "/favicon.ico"       // 网站图标
                );
        // 注意：/auth/logout 不在排除列表中，退出登录需要先登录才能操作
    }
}
