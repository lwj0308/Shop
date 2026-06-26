package com.shop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置
 * <p>
 * 前端页面和后端API通常不在同一个域名下，浏览器会阻止跨域请求。
 * 这个配置就是告诉浏览器"允许跨域"，让前端能正常调用后端接口。
 * </p>
 * <p>
 * 在网关层统一配置跨域，比在每个微服务里单独配置更方便。
 * </p>
 * <p>
 * 安全说明：
 * - 开发环境：允许所有来源（方便前端本地调试）
 * - 生产环境：只允许指定的前端域名（防止恶意网站调用我们的接口）
 * </p>
 */
@Configuration
public class CorsConfig {

    /** 当前运行环境，从配置文件读取，比如 dev、prod */
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /** 生产环境允许的前端域名列表，从配置文件读取 */
    @Value("${gateway.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    /**
     * 创建跨域过滤器
     * <p>
     * 根据运行环境自动切换安全策略：
     * - 开发/测试环境：允许所有来源（方便调试）
     * - 生产环境：只允许配置的域名（更安全）
     * </p>
     *
     * @return 跨域过滤器
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 根据环境配置允许的来源
        if (isProdEnvironment()) {
            // 生产环境：只允许指定的前端域名
            for (String origin : allowedOrigins.split(",")) {
                config.addAllowedOriginPattern(origin.trim());
            }
        } else {
            // 开发/测试环境：允许所有来源（方便前端本地调试）
            config.addAllowedOriginPattern("*");
        }

        // 明确指定允许的请求头（比 * 更安全，防止不必要的Header暴露）
        config.setAllowedHeaders(List.of(
                "Authorization",       // Token认证头
                "Content-Type",        // 请求体类型（JSON、表单等）
                "Accept",              // 客户端期望的响应类型
                "X-Requested-With",    // AJAX请求标识
                "X-Request-Id",        // 链路追踪ID
                "X-User-Id",           // 用户ID（网关注入）
                "X-User-Role"          // 用户角色（网关注入）
        ));

        // 明确指定允许的HTTP方法（比 * 更安全，防止不必要的请求方法）
        config.setAllowedMethods(List.of(
                "GET",     // 查询
                "POST",    // 新增
                "PUT",     // 修改
                "DELETE",  // 删除
                "OPTIONS"  // 预检请求（浏览器自动发送）
        ));

        // 允许携带Cookie（用于Session认证等场景）
        config.setAllowCredentials(true);

        // 预检请求的缓存时间（单位：秒），减少浏览器发预检请求的次数
        config.setMaxAge(3600L);

        // 暴露给前端的响应头（前端JS可以读取这些Header）
        config.setExposedHeaders(List.of(
                "X-Request-Id"  // 链路追踪ID，方便前端排查问题
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    /**
     * 判断是否为生产环境
     *
     * @return true=生产环境，false=非生产环境
     */
    private boolean isProdEnvironment() {
        return "prod".equalsIgnoreCase(activeProfile);
    }
}
