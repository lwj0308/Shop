package com.shop.user.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 用户上下文工具类
 * <p>
 * 封装从Sa-Token获取当前登录用户信息的常用操作，
 * 这样业务代码就不用每次都写 StpUtil.getLoginIdAsLong() 了，
 * 直接 UserContext.getUserId() 就行，更简洁也更不容易写错。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 *     Long userId = UserContext.getUserId();    // 获取当前登录用户ID
 *     boolean loggedIn = UserContext.isLogin();  // 判断是否已登录
 * </pre>
 */
public class UserContext {

    /** 私有构造方法，防止别人new对象，工具类只需要调用静态方法 */
    private UserContext() {
    }

    /**
     * 获取当前登录用户的ID
     * <p>
     * 从Sa-Token中获取当前登录用户的ID，如果没登录会抛异常。
     * 所以调用这个方法之前，确保接口已经做了登录校验。
     * </p>
     *
     * @return 当前登录用户ID
     */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 判断当前是否已登录
     * <p>
     * 不会抛异常，没登录就返回false，适合在不确定是否登录的场景使用。
     * </p>
     *
     * @return true已登录，false未登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 获取当前的Token值
     * <p>
     * 返回当前请求携带的Token字符串，没登录则返回null。
     * </p>
     *
     * @return Token字符串，未登录返回null
     */
    public static String getTokenValue() {
        return StpUtil.getTokenValue();
    }
}
