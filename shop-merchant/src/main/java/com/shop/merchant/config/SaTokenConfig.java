package com.shop.merchant.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.context.UserContext;
import com.shop.common.interceptor.InnerApiInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
     * 注册两个拦截器：
     * 1. UserContext拦截器：从Sa-Token或请求头中获取用户ID，存入ThreadLocal
     * 2. Sa-Token拦截器：校验是否登录，未登录的请求返回401
     * </p>
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一步：注册UserContext拦截器，将用户ID存入ThreadLocal
        // 这个拦截器必须在Sa-Token拦截器之前执行，这样后续代码才能通过UserContext获取用户ID
        registry.addInterceptor(new HandlerInterceptor() {
            /**
             * 请求处理前：从Sa-Token或请求头获取用户ID，存入UserContext
             * <p>
             * 优先从Sa-Token Session获取userId（商家登录时存入的），
             * 其次从Sa-Token loginId获取，
             * 最后从网关传递的X-User-Id请求头获取（用于商家入驻等场景）。
             * </p>
             */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // 方式1：从Sa-Token获取用户ID（商家已登录的情况）
                if (StpUtil.isLogin()) {
                    Object userId = StpUtil.getSession().get("userId");
                    if (userId != null) {
                        // 用 Number.longValue() 而不是 (Long) 强转
                        // 因为 Redis-Jackson 序列化时，Long 值如果在 Integer 范围内（如 1001），
                        // 反序列化后会变成 Integer，直接 (Long) 强转会抛 ClassCastException
                        UserContext.setUserId(((Number) userId).longValue());
                        return true;
                    }
                    // Session中没有userId，用loginId作为fallback
                    UserContext.setUserId(StpUtil.getLoginIdAsLong());
                    return true;
                }

                // 方式2：从网关传递的请求头获取用户ID（用户通过网关访问的情况）
                // 商家入驻申请时，用户还没登录商家端，但网关已经验证了用户身份
                String userIdHeader = request.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    try {
                        UserContext.setUserId(Long.parseLong(userIdHeader));
                    } catch (NumberFormatException e) {
                        // 请求头格式不对，忽略
                    }
                }
                return true;
            }

            /**
             * 请求完成后：清除ThreadLocal，防止内存泄漏
             * <p>
             * 线程池中的线程会被复用，如果不清理，下一个请求可能会拿到上一个请求的用户ID，
             * 导致数据错乱。所以必须在请求结束后清理。
             * </p>
             */
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                UserContext.clear();
            }
        }).addPathPatterns("/**");

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
                        "/merchant/inner/**",       // 内部接口（供其他微服务通过Feign调用，如订单结算）
                        "/merchant/promotion/inner/**", // 满减活动内部接口（供shop-order通过Feign调用计算满减优惠）
                        "/merchant/seckill/inner/**",   // 秒杀活动内部接口（供shop-order通过Feign调用查询秒杀活动信息）
                        "/merchant/seckill/public/**",  // 秒杀活动公开接口（用户端查询秒杀列表/详情，不需要登录）
                        // ===== 管理端接口仅供shop-admin通过Feign内部调用（由InnerApiInterceptor校验X-Inner-Key） =====
                        "/merchant/admin/**",              // 管理端商家接口
                        "/merchant/settlement/admin/**",   // 管理端结算/提现审核接口
                        "/merchant/coupon/admin/**",       // 管理端优惠券接口
                        "/merchant/seckill/admin/**",      // 管理端秒杀活动接口
                        "/merchant/promotion/admin/**",   // 管理端满减活动接口
                        "/shop/{shopId}",           // 公开查看店铺信息
                        "/doc.html",                // Swagger文档页面
                        "/webjars/**",              // Swagger静态资源
                        "/v3/api-docs/**",          // OpenAPI文档接口
                        "/swagger-resources/**",    // Swagger资源
                        "/favicon.ico",             // 网站图标
                        "/error"                    // 错误页面
                );
    }
}
