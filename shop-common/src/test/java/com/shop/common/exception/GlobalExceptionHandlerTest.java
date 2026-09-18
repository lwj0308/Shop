package com.shop.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 全局异常处理器测试
 * <p>
 * 小白理解：GlobalExceptionHandler 就像公司的前台，所有异常（访客）都要经过它，
 * 它负责把不同类型的异常转换成统一的 Result 格式返回给前端。
 * 这个测试类就是模拟各种异常场景，验证前台处理得对不对。
 * </p>
 */
@DisplayName("GlobalExceptionHandler 全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ==================== 登录异常测试 ====================

    @Nested
    @DisplayName("Sa-Token 登录异常处理")
    class NotLoginExceptionTest {

        @Test
        @DisplayName("未登录异常：返回401状态码和未登录提示")
        void handleNotLoginException_returns401() {
            // 场景：用户没登录就访问需要登录的接口
            // 用mock创建NotLoginException实例，避免依赖具体构造函数签名
            // （不同sa-token版本的NotLoginException构造函数参数不同）
            NotLoginException e = mock(NotLoginException.class);

            // 验证：返回401未登录
            Result<Void> result = handler.handleNotLoginException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
            assertThat(result.getMessage()).isEqualTo("未登录或Token已过期");
            assertThat(result.getData()).isNull();
        }
    }

    // ==================== 参数校验异常测试 ====================

    @Nested
    @DisplayName("参数校验异常处理")
    class ValidationExceptionTest {

        @Test
        @DisplayName("@Valid校验失败：返回400和字段错误信息")
        void handleMethodArgumentNotValidException_returns400WithFieldErrors() {
            // 场景：DTO的phone字段校验不通过（比如@NotBlank）
            // 用mock构造异常，避免依赖MethodParameter的复杂构造
            org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
            FieldError fieldError = new FieldError("userDTO", "phone", "手机号不能为空");
            when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

            MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
            when(e.getBindingResult()).thenReturn(bindingResult);

            // 验证：返回400，message包含字段名和错误信息
            Result<Void> result = handler.handleMethodArgumentNotValidException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_VALID_FAIL.getCode());
            assertThat(result.getMessage()).contains("phone");
            assertThat(result.getMessage()).contains("手机号不能为空");
        }

        @Test
        @DisplayName("表单绑定异常：返回400和字段错误信息")
        void handleBindException_returns400WithFieldErrors() {
            // 场景：前端传了"abc"给Integer字段，类型转换失败
            BindException e = new BindException(new Object(), "target");
            e.addError(new FieldError("target", "age", "类型不匹配"));

            Result<Void> result = handler.handleBindException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_VALID_FAIL.getCode());
            assertThat(result.getMessage()).contains("age");
            assertThat(result.getMessage()).contains("类型不匹配");
        }

        @Test
        @DisplayName("约束校验异常：返回400和校验提示")
        void handleConstraintViolationException_returns400WithMessages() {
            // 场景：@PathVariable @Min(1) 但前端传了0
            @SuppressWarnings("unchecked")
            ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
            when(violation.getMessage()).thenReturn("必须大于0");
            Set<ConstraintViolation<?>> violations = Collections.singleton(violation);

            ConstraintViolationException e = new ConstraintViolationException(violations);

            Result<Void> result = handler.handleConstraintViolationException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_VALID_FAIL.getCode());
            assertThat(result.getMessage()).contains("必须大于0");
        }
    }

    // ==================== 请求格式异常测试 ====================

    @Nested
    @DisplayName("请求格式异常处理")
    class RequestFormatExceptionTest {

        @Test
        @DisplayName("请求体不可读：返回400和格式错误提示")
        void handleHttpMessageNotReadableException_returns400() {
            // 场景：前端传了不合法的JSON
            HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                    "JSON解析失败", mock(org.springframework.http.HttpInputMessage.class));

            Result<Void> result = handler.handleHttpMessageNotReadableException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.BODY_NOT_READABLE.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.BODY_NOT_READABLE.getMessage());
        }

        @Test
        @DisplayName("请求方法不支持：返回405")
        void handleHttpRequestMethodNotSupportedException_returns405() {
            // 场景：接口要求POST，前端发了GET
            HttpRequestMethodNotSupportedException e = new HttpRequestMethodNotSupportedException("GET");
            // 模拟 HttpServletRequest 以获取请求路径（N-P 整改：日志增加路径信息）
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRequestURI()).thenReturn("/api/user/address/123");

            Result<Void> result = handler.handleHttpRequestMethodNotSupportedException(e, request);

            assertThat(result.getCode()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.getMessage());
        }

        @Test
        @DisplayName("缺少请求参数：返回400和参数名提示")
        void handleMissingServletRequestParameterException_returns400() {
            // 场景：接口要求传keyword参数，前端没传
            MissingServletRequestParameterException e = new MissingServletRequestParameterException(
                    "keyword", "String");

            Result<Void> result = handler.handleMissingServletRequestParameterException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.MISSING_PARAM.getCode());
            assertThat(result.getMessage()).contains("keyword");
        }
    }

    // ==================== 业务异常测试 ====================

    @Nested
    @DisplayName("业务异常处理")
    class BusinessExceptionTest {

        @Test
        @DisplayName("业务异常：返回业务错误码和提示")
        void handleBusinessException_returnsBusinessCode() {
            // 场景：代码主动抛出业务异常（比如库存不足）
            BusinessException e = new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);

            Result<Void> result = handler.handleBusinessException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getMessage());
        }

        @Test
        @DisplayName("业务异常自定义code和message：原样返回")
        void handleBusinessException_customCodeAndMessage() {
            // 场景：自定义错误码和消息
            BusinessException e = new BusinessException(99999, "自定义错误");

            Result<Void> result = handler.handleBusinessException(e);

            assertThat(result.getCode()).isEqualTo(99999);
            assertThat(result.getMessage()).isEqualTo("自定义错误");
        }
    }

    // ==================== 兜底异常测试 ====================

    @Nested
    @DisplayName("兜底异常处理")
    class UnknownExceptionTest {

        @Test
        @DisplayName("未知异常：返回500和服务器错误提示")
        void handleException_returns500() {
            // 场景：代码bug导致的NullPointerException等
            Exception e = new NullPointerException("空指针");

            Result<Void> result = handler.handleException(e);

            assertThat(result.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
        }

        @Test
        @DisplayName("未知异常不暴露具体错误信息：message不包含堆栈")
        void handleException_doesNotExposeStackTrace() {
            // 场景：验证兜底处理不会把具体异常信息泄露给前端
            Exception e = new RuntimeException("数据库连接失败: jdbc:mysql://10.0.0.1:3306");

            Result<Void> result = handler.handleException(e);

            // 返回的是通用提示，不能包含数据库地址等敏感信息
            assertThat(result.getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
            assertThat(result.getMessage()).doesNotContain("10.0.0.1");
            assertThat(result.getMessage()).doesNotContain("jdbc");
        }
    }
}
