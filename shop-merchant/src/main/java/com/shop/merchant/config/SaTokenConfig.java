package com.shop.merchant.config;

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
     * <p>
     * 拦截顺序按注册顺序：先取用户ID → 再校验内部密钥 → 最后校验登录态。
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
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                // 排除不需要登录的接口
                .excludePathPatterns(
                        "/merchant/auth/login",     // 商家登录接口，肯定不需要登录
                        "/merchant/apply",          // 商家入驻申请（通过网关传递X-User-Id识别用户）
                        "/merchant/inner/**",       // 内部接口（供其他微服务通过Feign调用，如通过userId反查merchantId）
                        // ===== 管理端接口仅供shop-admin通过Feign内部调用（由InnerApiInterceptor校验X-Inner-Key） =====
                        "/merchant/admin/**",              // 管理端商家接口
                        "/merchant/settlement/admin/**",   // 管理端结算/提现审核接口
                        "/merchant/shop/{shopId}",  // 公开查看店铺信息（N-P 性能测试整改：原路径 /shop/{shopId} 缺少 /merchant 前缀，导致 Feign 调用被 401 拦截，merchantId 始终为 null）
                        "/doc.html",                // Swagger文档页面
                        "/webjars/**",              // Swagger静态资源
                        "/v3/api-docs/**",          // OpenAPI文档接口
                        "/swagger-resources/**",    // Swagger资源
                        "/favicon.ico",             // 网站图标
                        "/error"                    // 错误页面
                );
    }
}
