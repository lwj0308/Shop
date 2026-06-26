package com.shop.admin.security;

import com.shop.admin.annotation.DataScope;

/**
 * 数据权限上下文持有者
 * <p>
 * 使用ThreadLocal在Service层和MyBatis拦截器之间传递数据权限配置信息。
 * Service方法上标注@DataScope注解后，AOP切面会将注解信息存入ThreadLocal，
 * MyBatis拦截器从ThreadLocal读取，实现自动拼接数据权限SQL。
 * </p>
 * <p>
 * 为什么需要ThreadLocal？
 * 因为@DataScope注解标在Service方法上，而MyBatis拦截器只能拦截Mapper方法，
 * 两者不在同一层，无法直接传递信息。ThreadLocal就像一个"中转站"，
 * AOP切面把注解信息放进去，拦截器再取出来用。
 * </p>
 */
public class DataScopeContextHolder {

    /** ThreadLocal容器，存储当前线程的数据权限注解信息 */
    private static final ThreadLocal<DataScope> CONTEXT = new ThreadLocal<>();

    /**
     * 设置数据权限注解信息
     * <p>
     * AOP切面在Service方法执行前调用，把@DataScope注解信息存入ThreadLocal。
     * </p>
     *
     * @param dataScope @DataScope注解实例
     */
    public static void set(DataScope dataScope) {
        CONTEXT.set(dataScope);
    }

    /**
     * 获取数据权限注解信息
     * <p>
     * MyBatis拦截器调用，从ThreadLocal读取@DataScope注解信息。
     * 如果为null，说明当前方法不需要数据权限过滤。
     * </p>
     *
     * @return @DataScope注解实例，可能为null
     */
    public static DataScope get() {
        return CONTEXT.get();
    }

    /**
     * 清除数据权限注解信息
     * <p>
     * AOP切面在Service方法执行后调用，防止ThreadLocal内存泄漏。
     * 一定要在finally或@After中调用，确保无论方法是否抛异常都能清理。
     * </p>
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
