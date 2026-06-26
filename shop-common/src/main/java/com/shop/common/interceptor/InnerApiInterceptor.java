package com.shop.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部接口鉴权拦截器
 * <p>
 * 拦截 /inner/** 路径的请求，校验请求头中的 X-Inner-Key 是否匹配配置的密钥。
 * 防止绕过Gateway直连微服务端口调用内部接口造成资损。
 * </p>
 * <p>
 * 工作原理：
 * 1. Feign调用时自动带上 X-Inner-Key Header（由FeignInnerKeyConfig配置）
 * 2. 本拦截器校验 X-Inner-Key 是否等于配置的 shop.security.inner-key
 * 3. 不匹配则返回403，拒绝访问
 * </p>
 * <p>
 * 小白说明：内部接口是给微服务之间互相调用的（比如支付服务查订单金额），
 * 普通用户不应该能访问。这个拦截器就像门禁卡，只有带正确卡的人才能进。
 * </p>
 */
@Slf4j
public class InnerApiInterceptor implements HandlerInterceptor {

    /** 内部接口密钥，从配置文件读取，默认值仅用于开发环境 */
    @Value("${shop.security.inner-key:shop-inner-key-2024}")
    private String innerKey;

    /**
     * 在Controller方法执行之前调用，校验内部接口密钥
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  处理器
     * @return true继续执行，false拒绝访问
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = request.getHeader("X-Inner-Key");
        if (key == null || !key.equals(innerKey)) {
            log.warn("内部接口鉴权失败: path={}, remoteAddr={}", request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权访问内部接口\"}");
            return false;
        }
        return true;
    }
}
