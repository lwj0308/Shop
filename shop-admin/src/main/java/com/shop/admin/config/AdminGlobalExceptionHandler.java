package com.shop.admin.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 管理后台专用全局异常处理器
 * <p>
 * 这个处理器只拦截 com.shop.admin 包下的Controller抛出的异常，
 * 用来补充 shop-common 中通用 GlobalExceptionHandler 没有覆盖到的异常类型。
 * </p>
 * <p>
 * 为什么要单独搞一个？因为管理后台用到了 Sa-Token 做权限控制、Feign 做微服务调用，
 * 这些场景会抛出一些通用处理器不认识的异常，需要在这里专门处理。
 * </p>
 * <p>
 * 核心原则：严禁把后端非业务报错（系统错误信息）直接返回给前端！
 * 只能返回业务错误问题或者"系统内部错误"之类的通用提示。
 * 否则黑客可能通过错误信息了解到我们的技术栈、数据库结构等内部信息。
 * </p>
 * <p>
 * 优先级：使用 @Order(Ordered.HIGHEST_PRECEDENCE) 确保比通用异常处理器先执行，
 * 这样 admin 模块特有的异常就不会被通用处理器的兜底方法吞掉。
 * </p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.shop.admin")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminGlobalExceptionHandler {

    /**
     * 处理 Sa-Token 未登录异常
     * <p>
     * 当用户没登录就访问需要登录的接口时，Sa-Token 会抛出这个异常。
     * 比如用户 Token 过期了、或者根本没传 Token。
     * </p>
     * <p>
     * 安全注意：不要把 Sa-Token 的内部错误信息返回前端，
     * 比如"Token已过期"、"未提供Token"等，统一返回"未登录"就行。
     * </p>
     *
     * @param e Sa-Token 抛出的未登录异常
     * @return 返回401未登录的统一响应
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        // 记录warn级别日志，方便排查是哪种类型的未登录（Token过期、被踢下线等）
        log.warn("未登录访问: type={}", e.getType());
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    /**
     * 处理 Sa-Token 无权限异常
     * <p>
     * 当用户登录了但没有某个接口的权限时，Sa-Token 会抛出这个异常。
     * 比如普通管理员想访问超级管理员才能用的接口。
     * </p>
     * <p>
     * 安全注意：不要把具体缺少哪个权限标识返回前端！
     * 比如"缺少 admin:user:delete 权限"这种信息不能暴露，
     * 否则黑客就能知道我们系统里有哪些权限配置。
     * </p>
     *
     * @param e Sa-Token 抛出的无权限异常
     * @return 返回403无权限的统一响应
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        // 日志里记录缺少哪个权限，方便开发排查问题；但前端只返回通用的"无权限"
        log.warn("无权限访问: 缺少权限标识={}", e.getPermission());
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    /**
     * 处理 Sa-Token 无角色异常
     * <p>
     * 当用户没有某个角色时，Sa-Token 会抛出这个异常。
     * 比如需要"超级管理员"角色才能操作，但当前用户只是"普通管理员"。
     * </p>
     * <p>
     * 安全注意：不要把具体缺少哪个角色返回前端！
     * 避免暴露系统内部的角色体系设计。
     * </p>
     *
     * @param e Sa-Token 抛出的无角色异常
     * @return 返回403无权限的统一响应
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        // 日志里记录缺少哪个角色，方便排查；前端只返回通用的"无权限"
        log.warn("无角色权限: 缺少角色标识={}", e.getRole());
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    /**
     * 处理 Feign 微服务调用异常
     * <p>
     * 当管理后台通过 Feign 调用其他微服务（比如商品服务、订单服务）失败时，
     * Feign 会抛出这个异常。常见原因：远程服务宕机、网络超时、远程服务返回错误等。
     * </p>
     * <p>
     * 安全注意：绝对不能把远程服务的错误信息返回前端！
     * 远程服务的错误信息可能包含数据库报错、堆栈信息等敏感内容，
     * 如果泄露给前端，等于把整个微服务架构的内部细节都暴露了。
     * </p>
     *
     * @param e Feign 调用异常
     * @return 返回500服务器内部错误的统一响应
     */
    @ExceptionHandler(FeignException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleFeignException(FeignException e) {
        // 微服务调用失败是严重问题，需要记录完整的异常信息（包含远程服务返回的内容），方便排查
        log.error("微服务调用异常: status={}, message={}", e.status(), e.getMessage(), e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * 处理数字格式异常
     * <p>
     * 当前端传了非数字字符串给后端的数字类型字段时，就会抛出这个异常。
     * 比如接口要求传 id=123，但前端传了 id=abc。
     * </p>
     * <p>
     * 安全注意：不要暴露具体的格式错误信息，比如"For input string: 'abc'"，
     * 这类信息可能被用来探测后端的数据类型和输入处理逻辑。
     * </p>
     *
     * @param e 数字格式异常
     * @return 返回400参数错误的统一响应
     */
    @ExceptionHandler(NumberFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNumberFormatException(NumberFormatException e) {
        log.warn("数字格式异常: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR);
    }

    /**
     * 处理非法参数异常
     * <p>
     * 当代码里主动抛出 IllegalArgumentException，或者框架内部参数校验不通过时触发。
     * 比如传了不合法的枚举值、日期格式不对等。
     * </p>
     * <p>
     * 安全注意：不要暴露内部错误信息，比如"Unknown enum value: XXX"，
     * 这类信息可能暴露系统的枚举定义或内部逻辑。
     * </p>
     *
     * @param e 非法参数异常
     * @return 返回400参数错误的统一响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR);
    }
}
