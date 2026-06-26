package com.shop.cart.interceptor;

import com.shop.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户信息拦截器
 * <p>
 * 从Gateway传递的Header中获取用户信息（X-User-Id），
 * 存入UserContext的ThreadLocal中，方便后续业务使用。
 * </p>
 * <p>
 * 工作流程：
 * 1. 用户请求 → Gateway校验Token → Gateway把userId放到Header（X-User-Id）中转发给购物车服务
 * 2. 本拦截器从Header中取出X-User-Id → 存入UserContext的ThreadLocal
 * 3. 后续业务代码通过 SecurityUtils.getCurrentUserId() 获取用户ID
 * 4. 请求结束时，清除ThreadLocal，防止内存泄漏
 * </p>
 * <p>
 * 安全说明：不调用 StpUtil.login(userId)，因为X-User-Id Header可被伪造。
 * 鉴权完全依赖Sa-Token的Token校验（Gateway已校验Token并重写X-User-Id）。
 * </p>
 */
@Slf4j
@Component
public class UserInfoInterceptor implements HandlerInterceptor {

    /** Header中用户ID的key名，和Gateway约定好的一致 */
    private static final String USER_ID_HEADER = "X-User-Id";

    /**
     * 在Controller方法执行之前调用
     * <p>
     * 从请求Header中获取Gateway传递过来的用户ID，
     * 如果存在就存入UserContext的ThreadLocal中。
     * </p>
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  处理器
     * @return true继续执行，false中断请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从Header中获取用户ID（Gateway转发时会带上）
        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                Long userId = Long.parseLong(userIdStr);
                // 存入UserContext的ThreadLocal，方便后续业务代码获取
                // 注意：不调用StpUtil.login()，因为X-User-Id可被伪造
                // 鉴权由Sa-Token的Token校验负责（Gateway已校验Token）
                UserContext.setUserId(userId);
                log.debug("从Gateway获取用户信息: userId={}", userId);
            } catch (NumberFormatException e) {
                log.warn("X-User-Id格式错误: {}", userIdStr);
            }
        }
        return true;
    }

    /**
     * 请求处理完成后调用
     * <p>
     * 清除UserContext的ThreadLocal，防止内存泄漏。
     * ThreadLocal用完必须清，否则线程复用时会导致用户信息串了。
     * </p>
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  处理器
     * @param ex       异常（如果有）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清除ThreadLocal，防止内存泄漏和用户信息串线程
        UserContext.clear();
    }
}
