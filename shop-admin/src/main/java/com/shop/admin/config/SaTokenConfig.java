package com.shop.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.context.UserContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sa-Token配置类
 * <p>
 * 配置Sa-Token的拦截器，实现管理后台接口的登录校验和权限控制。
 * 管理后台使用独立的Sa-Token登录体系，与用户端、商家端互不影响。
 * 同时配置AdminUserInfoInterceptor，将管理员ID存入ThreadLocal，方便Service层获取。
 * </p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册拦截器
     * <p>
     * 注册两个拦截器：
     * 1. AdminUserInfoInterceptor：从Sa-Token Session中获取管理员ID，存入ThreadLocal
     * 2. Sa-Token拦截器：校验是否登录，未登录的请求返回401
     * </p>
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一步：注册AdminUserInfo拦截器，将管理员ID存入ThreadLocal
        // 这个拦截器必须在Sa-Token拦截器之前执行，这样后续代码才能通过UserContext获取管理员ID
        registry.addInterceptor(new HandlerInterceptor() {
            /**
             * 请求处理前：从Sa-Token Session获取管理员ID，存入UserContext
             * <p>
             * 从Sa-Token Session获取adminUserId（管理员登录时存入的），
             * 如果Session中没有则用loginId作为fallback。
             * 不再信任任何请求头传入的管理员ID，防止伪造攻击。
             * </p>
             */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // 从Sa-Token获取管理员ID（管理员已登录的情况）
                if (StpUtil.isLogin()) {
                    Object adminUserId = StpUtil.getSession().get("adminUserId");
                    if (adminUserId != null) {
                        UserContext.setUserId((Long) adminUserId);
                        return true;
                    }
                    // Session中没有adminUserId，用loginId作为fallback
                    UserContext.setUserId(StpUtil.getLoginIdAsLong());
                }
                return true;
            }

            /**
             * 请求完成后：清除ThreadLocal，防止内存泄漏
             * <p>
             * 线程池中的线程会被复用，如果不清理，下一个请求可能会拿到上一个请求的管理员ID，
             * 导致数据错乱。所以必须在请求结束后清理。
             * </p>
             */
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                UserContext.clear();
            }
        }).addPathPatterns("/**");

        // 第二步：注册Sa-Token拦截器，校验登录状态
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                // 排除不需要登录的接口
                .excludePathPatterns(
                        "/admin/auth/login",      // 管理员登录接口，肯定不需要登录
                        "/admin/auth/captcha",    // 验证码接口，登录前就要获取
                        "/doc.html",              // Swagger文档页面
                        "/webjars/**",            // Swagger静态资源
                        "/v3/api-docs/**",        // OpenAPI文档接口
                        "/swagger-resources/**",  // Swagger资源
                        "/favicon.ico",           // 网站图标
                        "/error"                  // 错误页面
                );
    }
}
