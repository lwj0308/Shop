package com.shop.admin.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdminGlobalExceptionHandler 管理后台全局异常处理器的单元测试
 * <p>
 * 验证6个 @ExceptionHandler 方法：
 * 1. handleNotLoginException → 401 + UNAUTHORIZED（不暴露Sa-Token内部信息）
 * 2. handleNotPermissionException → 403 + FORBIDDEN（不暴露具体权限标识）
 * 3. handleNotRoleException → 403 + FORBIDDEN（不暴露具体角色标识）
 * 4. handleFeignException → 500 + INTERNAL_ERROR（不暴露远程错误信息）
 * 5. handleNumberFormatException → 400 + PARAM_ERROR
 * 6. handleIllegalArgumentException → 400 + PARAM_ERROR
 * </p>
 * <p>
 * 小白理解：异常处理器是后端的"安全卫士"，把各种异常转换成统一的 Result 格式返回前端。
 * 核心原则是：绝不把后端的内部错误信息（比如堆栈、数据库报错、权限配置）泄露给前端，
 * 避免黑客通过错误信息了解系统内部结构。
 * </p>
 * <p>
 * 测试模式：直接调用 handler 方法，验证返回的 Result 的状态码和消息。
 * </p>
 */
@DisplayName("AdminGlobalExceptionHandler 管理后台异常处理器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminGlobalExceptionHandlerTest {

    /** 被测异常处理器，没有外部依赖需要注入 */
    @InjectMocks
    private AdminGlobalExceptionHandler handler;

    // ==================== 1. handleNotLoginException 未登录异常 ====================

    @Nested
    @DisplayName("handleNotLoginException 未登录异常处理")
    class HandleNotLoginExceptionTest {

        @Test
        @DisplayName("未登录异常：返回401状态码和UNAUTHORIZED错误码，不暴露Sa-Token内部信息")
        void shouldReturnUnauthorized() {
            // 用mock创建异常实例，避免依赖具体构造函数签名
            // （不同sa-token版本的NotLoginException构造函数参数不同）
            NotLoginException e = mock(NotLoginException.class);
            when(e.getType()).thenReturn("token timeout");

            Result<Void> result = handler.handleNotLoginException(e);

            // 验证：返回401未登录
            assertThat(result.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.UNAUTHORIZED.getMessage());
            // 验证：不暴露Sa-Token内部错误信息（比如"token timeout"）
            assertThat(result.getMessage()).doesNotContain("token");
            assertThat(result.getMessage()).doesNotContain("timeout");
        }
    }

    // ==================== 2. handleNotPermissionException 无权限异常 ====================

    @Nested
    @DisplayName("handleNotPermissionException 无权限异常处理")
    class HandleNotPermissionExceptionTest {

        @Test
        @DisplayName("无权限异常：返回403状态码和FORBIDDEN错误码")
        void shouldReturnForbidden() {
            NotPermissionException e = mock(NotPermissionException.class);
            when(e.getPermission()).thenReturn("admin:user:delete");

            Result<Void> result = handler.handleNotPermissionException(e);

            // 验证：返回403无权限
            assertThat(result.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("安全验证：返回消息不包含具体权限标识，避免暴露权限配置")
        void shouldNotExposePermissionKey() {
            NotPermissionException e = mock(NotPermissionException.class);
            when(e.getPermission()).thenReturn("admin:user:delete");

            Result<Void> result = handler.handleNotPermissionException(e);

            // 验证：返回消息是通用的"无权限"，不包含具体权限标识
            assertThat(result.getMessage()).doesNotContain("admin:user:delete");
            assertThat(result.getMessage()).doesNotContain("user:delete");
            // 应该只返回通用的"无权限"
            assertThat(result.getMessage()).isEqualTo("无权限");
        }
    }

    // ==================== 3. handleNotRoleException 无角色异常 ====================

    @Nested
    @DisplayName("handleNotRoleException 无角色异常处理")
    class HandleNotRoleExceptionTest {

        @Test
        @DisplayName("无角色异常：返回403状态码和FORBIDDEN错误码，不暴露具体角色")
        void shouldReturnForbidden() {
            NotRoleException e = mock(NotRoleException.class);
            when(e.getRole()).thenReturn("super_admin");

            Result<Void> result = handler.handleNotRoleException(e);

            // 验证：返回403无权限
            assertThat(result.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
            // 验证：不暴露具体角色标识
            assertThat(result.getMessage()).doesNotContain("super_admin");
        }
    }

    // ==================== 4. handleFeignException Feign调用异常 ====================

    @Nested
    @DisplayName("handleFeignException Feign微服务调用异常处理")
    class HandleFeignExceptionTest {

        @Test
        @DisplayName("Feign异常：返回500状态码和INTERNAL_ERROR错误码")
        void shouldReturnInternalError() {
            FeignException e = mock(FeignException.class);
            when(e.status()).thenReturn(500);
            when(e.getMessage()).thenReturn("connection refused to user-service");

            Result<Void> result = handler.handleFeignException(e);

            // 验证：返回500服务器内部错误
            assertThat(result.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
        }

        @Test
        @DisplayName("安全验证：返回消息不包含远程服务错误信息，避免暴露微服务架构")
        void shouldNotExposeRemoteError() {
            FeignException e = mock(FeignException.class);
            when(e.status()).thenReturn(500);
            // 远程服务可能返回包含敏感信息的错误
            when(e.getMessage()).thenReturn("MySQLSyntaxErrorException: Unknown column 'password' in 'user'");

            Result<Void> result = handler.handleFeignException(e);

            // 验证：返回消息是通用的"服务器内部错误"，不包含远程错误详情
            assertThat(result.getMessage()).doesNotContain("MySQL");
            assertThat(result.getMessage()).doesNotContain("password");
            assertThat(result.getMessage()).doesNotContain("user-service");
            // 应该只返回通用的"服务器内部错误"
            assertThat(result.getMessage()).isEqualTo("服务器内部错误");
        }
    }

    // ==================== 5. handleNumberFormatException 数字格式异常 ====================

    @Nested
    @DisplayName("handleNumberFormatException 数字格式异常处理")
    class HandleNumberFormatExceptionTest {

        @Test
        @DisplayName("数字格式异常：返回400状态码和PARAM_ERROR错误码，不暴露格式详情")
        void shouldReturnParamError() {
            NumberFormatException e = new NumberFormatException("For input string: \"abc\"");

            Result<Void> result = handler.handleNumberFormatException(e);

            // 验证：返回400参数错误
            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.PARAM_ERROR.getMessage());
            // 验证：不暴露具体的格式错误信息
            assertThat(result.getMessage()).doesNotContain("abc");
            assertThat(result.getMessage()).doesNotContain("For input string");
        }
    }

    // ==================== 6. handleIllegalArgumentException 非法参数异常 ====================

    @Nested
    @DisplayName("handleIllegalArgumentException 非法参数异常处理")
    class HandleIllegalArgumentExceptionTest {

        @Test
        @DisplayName("非法参数异常：返回400状态码和PARAM_ERROR错误码，不暴露内部错误")
        void shouldReturnParamError() {
            IllegalArgumentException e = new IllegalArgumentException("Unknown enum value: STATUS_XYZ");

            Result<Void> result = handler.handleIllegalArgumentException(e);

            // 验证：返回400参数错误
            assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo(ErrorCode.PARAM_ERROR.getMessage());
            // 验证：不暴露内部错误信息
            assertThat(result.getMessage()).doesNotContain("STATUS_XYZ");
            assertThat(result.getMessage()).doesNotContain("Unknown enum value");
        }
    }
}
