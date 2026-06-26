package com.shop.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 拦截所有Controller抛出的异常，统一转换为Result格式返回给前端。
 * 这样前端就不需要处理各种不同的错误格式了，只需要看code就行。
 * </p>
 * <p>
 * 处理顺序：
 * 1. Sa-Token登录异常（NotLoginException）
 * 2. 参数校验异常（@Valid校验不通过、请求体格式错误等）
 * 3. 业务异常（代码里主动抛出的BusinessException）
 * 4. 其他未知异常（兜底处理，防止直接返回500错误页面）
 * </p>
 * <p>
 * 日志策略：
 * - 业务异常：只记录warn级别（这是正常的业务逻辑，不是bug）
 * - 系统异常：记录error级别（这是代码bug，需要排查）
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理Sa-Token登录异常
     * <p>
     * 当用户未登录或Token无效时，Sa-Token会抛出NotLoginException。
     * 我们捕获这个异常，返回401状态码和"未登录"提示，
     * 前端会根据401跳转到登录页。
     * </p>
     *
     * @param e Sa-Token登录异常
     * @return 401未登录响应
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        // 记录warn日志，不记录error（用户没登录是正常的，不是bug）
        log.warn("未登录或Token无效: type={}, message={}", e.getType(), e.getMessage());
        // 返回401未登录，前端会拦截并跳转登录页
        return Result.fail(ErrorCode.UNAUTHORIZED.getCode(), "未登录或Token已过期");
    }

    /**
     * 处理参数校验异常 - @Valid注解校验不通过时触发
     * 比如DTO上加了@NotNull，但前端传了null，就会进这个方法
     *
     * @param e 校验异常
     * @return 包含具体哪个字段校验失败的错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 把所有字段的校验错误信息拼在一起，比如"用户名不能为空; 密码长度不能少于6位"
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(ErrorCode.PARAM_VALID_FAIL.getCode(), message);
    }

    /**
     * 处理参数绑定异常 - 表单提交时类型转换失败触发
     * 比如前端传了"abc"给Integer类型的字段
     *
     * @param e 绑定异常
     * @return 包含绑定错误信息的响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.fail(ErrorCode.PARAM_VALID_FAIL.getCode(), message);
    }

    /**
     * 处理约束校验异常 - @Validated注解校验路径变量或请求参数时触发
     * 比如接口上加了@PathVariable @Min(1)，但前端传了0
     *
     * @param e 约束校验异常
     * @return 包含校验错误信息的响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", message);
        return Result.fail(ErrorCode.PARAM_VALID_FAIL.getCode(), message);
    }

    /**
     * 处理请求体不可读异常 - JSON格式错误或缺少请求体时触发
     * 比如前端传了不合法的JSON字符串，或者该传JSON的地方什么都没传
     *
     * @param e 消息不可读异常
     * @return 提示请求数据格式错误的响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求数据格式错误: {}", e.getMessage());
        return Result.fail(ErrorCode.BODY_NOT_READABLE);
    }

    /**
     * 处理请求方法不支持异常 - 接口要求的HTTP方法和前端发的不一致时触发
     * 比如接口定义了@PostMapping，但前端发了GET请求
     *
     * @param e 请求方法不支持异常
     * @return 提示请求方法不支持的响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}，支持的方法: {}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 处理缺少请求参数异常 - @RequestParam标注的必填参数没传时触发
     * 比如接口要求传keyword参数，但前端没传
     *
     * @param e 缺少请求参数异常
     * @return 提示缺少必要参数的响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.fail(ErrorCode.MISSING_PARAM.getCode(),
                "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理业务异常 - 代码里主动抛出的BusinessException
     * 这是我们最常遇到的异常，表示业务逻辑不满足条件
     *
     * @param e 业务异常
     * @return 包含业务错误码和错误信息的响应
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 兜底处理 - 所有未被上面方法捕获的异常都会到这里
     * 这类异常通常是代码bug导致的，需要记录详细日志方便排查
     *
     * @param e 未知异常
     * @return 统一的500错误响应（不暴露具体错误信息给前端，防止泄露内部实现）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        // 未知异常要记录完整的堆栈信息，方便开发排查问题
        log.error("系统异常: ", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
