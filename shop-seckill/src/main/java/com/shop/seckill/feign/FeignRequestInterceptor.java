package com.shop.seckill.feign;

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
 * 秒杀服务通过Feign调用shop-merchant等下游服务时，需要把认证信息传递过去，
 * 否则下游服务的登录校验和身份识别会失败。
 * </p>
 * <p>
 * 传递两个请求头：
 * 1. Authorization：Sa-Token的token，让下游服务能验证登录态
 * 2. X-User-Id：用户ID，让下游服务的UserContext能拿到当前用户
 * </p>
 * <p>
 * 小白理解：就像你帮同事代领快递，得拿着他的取件码（token）和工号（userId），
 * 快递站（下游服务）才认你。这个拦截器就是自动帮你"带证件"的。
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
            //    这样下游服务的Sa-Token登录校验才能通过
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isEmpty()) {
                template.header("Authorization", auth);
            }

            // 3. 把原始请求的X-User-Id透传给下游服务（如果上游网关传过来的话）
            //    这样下游服务的UserContext能直接拿到用户ID
            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                template.header("X-User-Id", userIdHeader);
            }
        }

        // 4. 如果请求头里没有X-User-Id，从Sa-Token Session或UserContext取userId注入
        //    小白讲解：上游没带用户ID时，我们从登录态里拿出来补上，
        //    保证下游服务一定能知道"是谁在调用"
        if (!template.headers().containsKey("X-User-Id")) {
            Long userId = resolveUserId();
            if (userId != null) {
                template.header("X-User-Id", String.valueOf(userId));
            }
        }
    }

    /**
     * 解析当前用户ID
     * <p>
     * 优先从Sa-Token Session获取userId（商家登录时存入的），
     * 其次从Sa-Token loginId获取，
     * 最后从UserContext（ThreadLocal）获取。
     * </p>
     *
     * @return 当前用户ID，获取不到返回null
     */
    private Long resolveUserId() {
        // 优先从Sa-Token Session取（商家登录时存入的userId）
        if (StpUtil.isLogin()) {
            Object userId = StpUtil.getSession().get("userId");
            if (userId != null) {
                // 用 Number.longValue() 避免 Integer/Long 强转问题
                return ((Number) userId).longValue();
            }
            // Session中没有userId，用loginId作为fallback
            return StpUtil.getLoginIdAsLong();
        }
        // Sa-Token未登录时，从ThreadLocal取（内部接口等场景）
        return UserContext.getUserId();
    }
}
