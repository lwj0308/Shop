package com.shop.marketing.feign;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign请求拦截器：把当前请求的认证信息透传给下游服务
 * <p>
 * 当营销服务通过Feign调用 shop-merchant 时，需要把Sa-Token的token和用户ID传递过去，
 * 否则 shop-merchant 的登录校验会报"未登录"，内部接口也拿不到用户身份。
 * </p>
 * <p>
 * 简单理解：就像你帮同事代领快递，得拿着他的取件码（token）和工号（userId），
 * 快递站（shop-merchant）才认你。
 * </p>
 * <p>
 * 注意：内部接口的 X-Inner-Key 由 shop-common 的 FeignInnerKeyConfig 自动注入，
 * 这里不需要处理，只负责透传登录态相关的头。
 * </p>
 */
@Slf4j
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    /**
     * 在Feign发送请求前，往请求头里塞认证信息
     *
     * @param template Feign请求模板，可以往里面加header
     */
    @Override
    public void apply(RequestTemplate template) {
        // 1. 从当前HTTP请求上下文中获取原始请求（就是前端发过来的那个请求）
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            // 2. 把Authorization头（Sa-Token的token）透传给Feign请求
            //    这样下游服务的登录校验才能通过
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isEmpty()) {
                template.header("Authorization", auth);
            }

            // 3. 透传网关注入的 X-User-Id 头（内部接口可能用这个识别用户）
            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                template.header("X-User-Id", userIdHeader);
            }
        }

        // 4. 兜底：如果请求头里没有 X-User-Id，从 Sa-Token Session 或 UserContext 取
        //    这样即使从定时任务等非HTTP上下文发起的Feign调用也能带上用户身份
        if (template.headers().getOrDefault("X-User-Id", java.util.Collections.emptyList()).isEmpty()) {
            Long userId = null;
            // 优先从Sa-Token Session取（商家登录时存入的userId）
            if (StpUtil.isLogin()) {
                Object sessionUserId = StpUtil.getSession().get("userId");
                if (sessionUserId != null) {
                    userId = ((Number) sessionUserId).longValue();
                } else {
                    userId = StpUtil.getLoginIdAsLong();
                }
            }
            // Sa-Token没取到，从ThreadLocal取（内部接口等场景）
            if (userId == null) {
                userId = UserContext.getUserId();
            }
            if (userId != null) {
                template.header("X-User-Id", String.valueOf(userId));
            }
        }
    }
}
