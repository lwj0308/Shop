package com.shop.common.util;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.context.UserContext;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;

/**
 * 安全工具类
 * <p>
 * 封装了Sa-Token的常用操作，提供更简洁的API。
 * 业务代码中需要获取当前用户信息或校验权限时，用这个工具类就行，
 * 不用直接和Sa-Token的API打交道。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 获取当前登录用户ID
 *     Long userId = SecurityUtils.getCurrentUserId();
 *
 *     // 判断是否登录
 *     if (SecurityUtils.isLogin()) { ... }
 *
 *     // 必须登录才能继续，未登录直接抛异常
 *     SecurityUtils.requireLogin();
 *
 *     // 校验权限
 *     SecurityUtils.checkPermission("user:delete");
 * </pre>
 * </p>
 */
public class SecurityUtils {

    /** 私有构造方法，防止被new对象（工具类只需要调用静态方法） */
    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户ID
     * <p>
     * 以Sa-Token为权威来源，优先从Sa-Token获取（因为Token无法伪造）。
     * ThreadLocal仅作为辅助（性能优化，避免频繁查Redis），不单独作为登录依据。
     * </p>
     *
     * @return 用户ID，未登录时返回null
     */
    public static Long getCurrentUserId() {
        // 先从Sa-Token取（权威来源，Token无法伪造）
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsLong();
        }
        // Sa-Token未登录时，从ThreadLocal取（辅助，用于内部接口等场景）
        return UserContext.getUserId();
    }

    /**
     * 判断当前用户是否已登录
     * <p>
     * 只信任Sa-Token的登录状态，不单独信任ThreadLocal。
     * 原因：ThreadLocal可被X-User-Id Header伪造，但Sa-Token的Token无法伪造。
     * 之前用"或"逻辑（UserContext.isLogin() || StpUtil.isLogin()）会导致
     * 绕过Gateway直连服务时伪造X-User-Id即可通过登录校验。
     * </p>
     *
     * @return true已登录，false未登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 要求当前用户必须登录，未登录则抛出BusinessException
     * 用在需要登录才能操作的接口中，一行代码搞定登录校验
     *
     * @return 当前登录用户ID
     */
    public static Long requireLogin() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 校验当前用户是否拥有指定权限
     * 没有权限会抛出BusinessException
     *
     * @param permission 权限标识，比如"user:delete"
     */
    public static void checkPermission(String permission) {
        StpUtil.checkPermission(permission);
    }

    /**
     * 校验当前用户是否拥有指定角色
     * 没有角色会抛出BusinessException
     *
     * @param role 角色标识，比如"admin"
     */
    public static void checkRole(String role) {
        StpUtil.checkRole(role);
    }
}
