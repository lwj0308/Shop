package com.shop.product.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.interceptor.InnerApiInterceptor;
import com.shop.product.interceptor.UserInfoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token配置类
 * <p>
 * 配置Sa-Token的拦截器，实现接口的登录校验。
 * 所有需要登录的接口，如果没带Token或Token过期，会自动返回401错误。
 * 商品搜索、分类树、品牌列表等接口不需要登录，放行处理。
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
                        // ===== 商品浏览不需要登录 =====
                        "/product/list",          // 商品列表
                        "/product/search",        // 商品搜索
                        "/product/suggest",       // 搜索建议
                        "/product/hot-keywords",  // 热门搜索词
                        "/product/{id}",          // 商品详情（GET请求）
                        "/product/category/tree", // 分类树
                        "/product/brand/list",    // 品牌列表
                        "/product/brand/{id}",    // 品牌详情
                        "/product/comment/list",  // 评价列表
                        // ===== Feign接口不需要登录（服务间内部调用） =====
                        "/product/sku/**",        // SKU相关接口（供订单服务Feign调用）
                        "/product/comment/shop/list",  // 按店铺查询评价（供商家服务Feign调用）
                        "/product/comment/reply",      // 商家回复评价（供商家服务Feign调用）
                        "/product/recommend/**",      // 商品推荐接口（热销/新品/相关/猜你喜欢，公开访问）
                        "/product/inner/**",          // 商品内部接口（供shop-order Feign调用：销量累加）
                        // ===== 管理端接口仅供shop-admin通过Feign内部调用（由InnerApiInterceptor校验X-Inner-Key） =====
                        "/product/comment/admin/**",   // 管理端评价接口（供管理后台Feign调用）
                        "/product/admin/**",          // 管理端商品接口（供管理后台Feign调用）
                        // ===== Swagger文档不需要登录 =====
                        "/doc.html",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/favicon.ico"
                );
    }
}
