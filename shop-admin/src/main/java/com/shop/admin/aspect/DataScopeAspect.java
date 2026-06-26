package com.shop.admin.aspect;

import com.shop.admin.annotation.DataScope;
import com.shop.admin.security.DataScopeContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 数据权限AOP切面
 * <p>
 * 拦截标注了@DataScope注解的Service方法，在方法执行前把注解信息存入ThreadLocal，
 * 方法执行后清除ThreadLocal，防止内存泄漏。
 * </p>
 * <p>
 * 工作流程：
 * 1. Service方法上标注@DataScope注解
 * 2. 本切面在方法执行前（@Before）把注解信息存入DataScopeContextHolder
 * 3. MyBatis拦截器从DataScopeContextHolder读取注解信息，拼接数据权限SQL
 * 4. 方法执行后（@After）清除ThreadLocal，避免影响其他请求
 * </p>
 */
@Aspect
@Component
@Slf4j
public class DataScopeAspect {

    /**
     * 方法执行前：将@DataScope注解信息存入ThreadLocal
     * <p>
     * Spring AOP会自动把方法上的@DataScope注解注入到dataScope参数中，
     * 我们只需要把它存到DataScopeContextHolder即可。
     * </p>
     *
     * @param joinPoint 切点信息（包含方法名、参数等，这里用不到）
     * @param dataScope 方法上的@DataScope注解实例（Spring自动注入）
     */
    @Before("@annotation(dataScope)")
    public void before(JoinPoint joinPoint, DataScope dataScope) {
        DataScopeContextHolder.set(dataScope);
    }

    /**
     * 方法执行后：清除ThreadLocal，防止内存泄漏
     * <p>
     * 必须在@After中清除，确保无论方法是否抛异常都能清理ThreadLocal。
     * 如果不清除，线程池中的线程被复用时，ThreadLocal中会残留上一次请求的数据，
     * 导致数据权限过滤出错。
     * </p>
     *
     * @param joinPoint 切点信息
     * @param dataScope 方法上的@DataScope注解实例
     */
    @After("@annotation(dataScope)")
    public void after(JoinPoint joinPoint, DataScope dataScope) {
        DataScopeContextHolder.clear();
    }
}
