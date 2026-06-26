package com.shop.common.config;

import com.shop.common.interceptor.InnerApiInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign内部调用配置
 * <p>
 * 自动在Feign调用的请求头中添加 X-Inner-Key，用于内部接口鉴权。
 * 配合InnerApiInterceptor使用，确保只有内部Feign调用才能访问/inner/**接口。
 * </p>
 * <p>
 * 小白说明：微服务之间通过Feign互相调用时，会自动带上这张"门禁卡"，
 * 这样内部接口就能识别"自己人"放行，外部请求没卡就会被拦。
 * </p>
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignInnerKeyConfig {

    /** 内部接口密钥，和InnerApiInterceptor用的同一个值 */
    @Value("${shop.security.inner-key:shop-inner-key-2024}")
    private String innerKey;

    /**
     * 注册Feign请求拦截器，自动添加内部密钥Header
     * <p>
     * 每次Feign调用都会自动在请求头加上 X-Inner-Key，
     * 内部接口的InnerApiInterceptor会校验这个值。
     * </p>
     */
    @Bean
    public RequestInterceptor innerKeyRequestInterceptor() {
        return template -> template.header("X-Inner-Key", innerKey);
    }

    /**
     * 注册内部接口鉴权拦截器
     * <p>
     * 各微服务的SaTokenConfig注入此Bean，拦截/inner/**路径。
     * 通过@Bean注册避免各微服务需要@ComponentScan扫描com.shop.common包。
     * </p>
     */
    @Bean
    public InnerApiInterceptor innerApiInterceptor() {
        return new InnerApiInterceptor();
    }
}
