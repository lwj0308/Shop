package com.shop.common.context;

/**
 * 用户上下文工具类
 * <p>
 * 用ThreadLocal存储当前登录用户的信息，这样在Service层任何地方都能获取到当前用户，
 * 不用一层一层地传userId参数了。
 * </p>
 * <p>
 * 工作原理：
 * - 用户请求进来时，拦截器从Token中解析出用户ID，存到ThreadLocal
 * - Service层需要用户ID时，直接调用 UserContext.getUserId() 获取
 * - 请求结束时，拦截器清除ThreadLocal，防止内存泄漏
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 获取当前登录用户ID
 *     Long userId = UserContext.getUserId();
 *
 *     // 判断是否登录
 *     if (UserContext.isLogin()) {
 *         // 已登录逻辑
 *     }
 * </pre>
 * </p>
 */
public class UserContext {

    /** ThreadLocal存储当前用户ID，每个线程独立，互不影响 */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 设置当前用户ID
     * 通常在拦截器/过滤器中调用，从Token解析出用户ID后存入
     *
     * @param userId 当前登录用户的ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID，未登录时返回null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 判断当前用户是否已登录
     *
     * @return true已登录，false未登录
     */
    public static boolean isLogin() {
        return USER_ID.get() != null;
    }

    /**
     * 清除当前用户信息
     * 必须在请求结束时调用，否则会导致内存泄漏
     * 通常在拦截器的afterCompletion方法中调用
     */
    public static void clear() {
        USER_ID.remove();
    }
}
