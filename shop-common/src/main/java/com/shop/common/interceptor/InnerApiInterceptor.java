package com.shop.common.interceptor;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
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

    /**
     * 内部接口密钥，必须由配置中心显式提供（无默认值，缺失则启动失败）。
     * <p>
     * 这里曾经带过一个源码内的默认值，后果是：只要配置中心漏配这一项，
     * 所有服务都会静默退回到那个"任何读过仓库的人都知道"的密钥，
     * /inner/** 与 /admin/** 的守护随即形同虚设，而且不会有任何报错。
     * </p>
     */
    @Value("${shop.security.inner-key}")
    private String innerKey;

    /**
     * 校验密钥已配置且非空白
     * <p>
     * 空串比漏配更危险：漏配会让占位符解析失败直接启动不了，而空串能正常启动，
     * 但只要客户端提交一个空的 X-Inner-Key 头就能通过比对，等于门禁卡刷卡机常年不锁。
     * </p>
     *
     * @throws IllegalStateException 密钥为空或全是空白字符时抛出，阻止服务带着敞开的风控启动
     */
    @PostConstruct
    public void validateConfiguredKey() {
        if (!StringUtils.hasText(innerKey)) {
            throw new IllegalStateException(
                    "shop.security.inner-key 未配置或为空，内部接口(/inner/**、/admin/**)将失去保护，"
                            + "请在Nacos配置中心为该服务显式设置此属性（各服务须使用同一个值）");
        }
    }

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
