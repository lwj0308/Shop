package com.shop.common.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态用户上下文拦截器
 * <p>
 * 商家端服务（shop-merchant / shop-marketing / shop-seckill）共用：把当前登录者ID写入
 * {@link UserContext} 的ThreadLocal，供Service层通过 SecurityUtils 读取。
 * </p>
 * <p>
 * 取值优先级（与拆分前完全一致）：
 * 1. Sa-Token Session 里的 userId（商家登录时存入的）
 * 2. Sa-Token 的 loginId（Session里没有userId时兜底）
 * 3. Gateway 传递的 X-User-Id 请求头（商家入驻申请等未登录商家端的场景）
 * </p>
 * <p>
 * 注意：这个类不是Spring Bean，由各模块的SaTokenConfig用 new 创建后注册，
 * 保持"每个模块各一个实例、注册在Sa-Token拦截器之前"的原有语义。
 * </p>
 */
public class TokenUserContextInterceptor implements HandlerInterceptor {

    /** Gateway传递用户ID的请求头名称 */
    private static final String USER_ID_HEADER = "X-User-Id";

    /**
     * 请求处理前：从Sa-Token或请求头获取用户ID，存入UserContext
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  处理器
     * @return 始终返回true，用户ID取不到时不阻断请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (StpUtil.isLogin()) {
            fillFromTokenSession();
            return true;
        }
        // 商家入驻申请时，用户还没登录商家端，但网关已经验证了用户身份
        fillFromRequestHeader(request);
        return true;
    }

    /**
     * 请求完成后：清除ThreadLocal，防止内存泄漏
     * <p>
     * 线程池中的线程会被复用，如果不清理，下一个请求可能会拿到上一个请求的用户ID，
     * 导致数据错乱。所以必须在请求结束后清理。
     * </p>
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  处理器
     * @param ex       异常（如果有）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 从Sa-Token会话中取用户ID
     * <p>
     * 优先取Session里的userId，没有则退回loginId。
     * </p>
     */
    private void fillFromTokenSession() {
        Object userId = StpUtil.getSession().get("userId");
        if (userId == null) {
            // Session中没有userId，用loginId作为fallback
            UserContext.setUserId(StpUtil.getLoginIdAsLong());
            return;
        }
        // 用 Number.longValue() 而不是 (Long) 强转
        // 因为 Redis-Jackson 序列化时，Long 值如果在 Integer 范围内（如 1001），
        // 反序列化后会变成 Integer，直接 (Long) 强转会抛 ClassCastException
        UserContext.setUserId(((Number) userId).longValue());
    }

    /**
     * 从Gateway传递的请求头中取用户ID
     *
     * @param request HTTP请求
     */
    private void fillFromRequestHeader(HttpServletRequest request) {
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            return;
        }
        try {
            UserContext.setUserId(Long.parseLong(userIdHeader));
        } catch (NumberFormatException e) {
            // 请求头格式不对，忽略
        }
    }
}
