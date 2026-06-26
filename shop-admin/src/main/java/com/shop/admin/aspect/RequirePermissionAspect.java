package com.shop.admin.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.admin.annotation.Logical;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminSecurityEventService;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 权限校验切面
 * <p>
 * 拦截加了@RequirePermission注解的Controller方法，在方法执行前校验当前管理员是否拥有所需权限。
 * 如果没有权限，会记录安全事件并抛出BusinessException，阻止方法执行。
 * 超级管理员（角色标识为"admin"）自动跳过权限校验，拥有所有权限。
 * </p>
 * <p>
 * 工作流程：
 * 1. 从注解中获取需要校验的权限列表和逻辑类型（AND/OR）
 * 2. 从Sa-Token获取当前登录管理员的权限列表和角色列表
 * 3. 如果是超级管理员（角色包含"admin"），直接放行
 * 4. 根据逻辑类型校验权限：AND需要全部拥有，OR只需其中一个
 * 5. 校验不通过则记录安全事件并抛出异常
 * </p>
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RequirePermissionAspect {

    /** 安全事件服务，权限越权时记录安全事件 */
    private final AdminSecurityEventService adminSecurityEventService;

    /**
     * 环绕通知：在方法执行前校验权限
     * <p>
     * 拦截所有标注了@RequirePermission注解的方法，在方法执行前进行权限校验。
     * 校验通过则继续执行方法，校验失败则记录安全事件并抛出FORBIDDEN异常。
     * </p>
     *
     * @param joinPoint         切点信息，包含被拦截方法的详细信息
     * @param requirePermission 注解实例，包含权限配置
     * @return 方法执行结果
     * @throws Throwable 方法执行过程中可能抛出的异常
     */
    @Around("@annotation(requirePermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        // 获取注解中声明的权限标识数组
        String[] requiredPermissions = requirePermission.value();
        Logical logical = requirePermission.logical();

        // 获取当前登录管理员的ID
        long userId = StpUtil.getLoginIdAsLong();

        // 获取当前管理员的角色列表
        List<String> roleList = StpUtil.getRoleList();

        // 超级管理员（角色包含"admin"）跳过权限校验，拥有所有权限
        if (roleList.contains("admin")) {
            return joinPoint.proceed();
        }

        // 获取当前管理员的权限列表
        List<String> permissionList = StpUtil.getPermissionList();

        // 根据逻辑类型校验权限
        boolean hasPermission;
        if (logical == Logical.AND) {
            // AND逻辑：需要同时拥有所有权限
            hasPermission = permissionList.containsAll(Arrays.asList(requiredPermissions));
        } else {
            // OR逻辑：只需拥有其中一个权限即可
            hasPermission = false;
            for (String permission : requiredPermissions) {
                if (permissionList.contains(permission)) {
                    hasPermission = true;
                    break;
                }
            }
        }

        // 权限校验不通过，记录安全事件并抛出异常
        if (!hasPermission) {
            // 获取客户端IP地址
            String ip = com.shop.admin.util.IpUtils.getClientIp();

            // 获取当前管理员的用户名（Sa-Token中loginId就是userId，用userId当标识）
            String username = String.valueOf(userId);

            // 记录安全事件：有人尝试访问无权限的接口
            adminSecurityEventService.recordSecurityEvent(
                    "权限越权",
                    userId,
                    username,
                    "尝试访问无权限的接口: " + Arrays.toString(requiredPermissions),
                    ip
            );

            log.warn("权限越权访问，用户ID：{}，所需权限：{}", userId, Arrays.toString(requiredPermissions));

            // 抛出无权限异常，阻止方法执行
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 权限校验通过，继续执行方法
        return joinPoint.proceed();
    }

}
