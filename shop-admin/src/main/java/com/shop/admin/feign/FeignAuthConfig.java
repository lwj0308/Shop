package com.shop.admin.feign;

import cn.dev33.satoken.stp.StpUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign鉴权配置
 * <p>
 * 微服务之间通过Feign调用时，默认不会携带登录凭证（Token），
 * 导致下游服务不知道是谁在调用，可能会拒绝请求。
 * 这个配置类的作用就是在每次Feign调用时，自动把当前用户的Token
 * 塞到请求头里带给下游服务，让下游服务也能识别调用者身份。
 * </p>
 * <p>
 * 工作原理：
 * 1. 管理员登录后，Sa-Token会在当前线程维护一个Token
 * 2. 当admin服务通过Feign调用其他服务时，这个拦截器会自动获取当前Token
 * 3. 把Token放到请求头的satoken字段中，传给下游服务
 * 4. 下游服务收到请求后，从请求头取出Token，就能知道是哪个管理员在操作
 * </p>
 */
@Slf4j
@Configuration
public class FeignAuthConfig {

    /**
     * 创建Feign请求拦截器，自动透传Sa-Token
     * <p>
     * 每次Feign发起请求前，这个拦截器都会被调用。
     * 它会检查当前线程是否有登录的Token，如果有就添加到请求头中。
     * </p>
     *
     * @return Feign请求拦截器
     */
    @Bean
    public RequestInterceptor feignAuthInterceptor() {
        return new RequestInterceptor() {
            /**
             * 在请求发送前，把当前用户的Token添加到请求头
             *
             * @param template Feign请求模板，可以往里面添加请求头
             */
            @Override
            public void apply(RequestTemplate template) {
                try {
                    // 检查当前线程是否有登录的Token
                    if (StpUtil.isLogin()) {
                        // 获取当前请求的Token值，添加到请求头中
                        String tokenValue = StpUtil.getTokenValue();
                        if (tokenValue != null && !tokenValue.isEmpty()) {
                            // Sa-Token默认从satoken请求头或参数中读取Token
                            template.header("satoken", tokenValue);
                            log.debug("Feign调用透传Token: {}", tokenValue.substring(0, Math.min(8, tokenValue.length())) + "...");
                        }
                    }
                } catch (Exception e) {
                    // 获取Token失败不影响Feign调用，只记录警告日志
                    // 比如在定时任务中调用Feign，就没有登录Token，这是正常的
                    log.warn("Feign调用获取Token失败: {}", e.getMessage());
                }
            }
        };
    }
}
