package com.shop.common.exception;

import com.shop.common.result.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BusinessException 业务异常的单元测试
 * <p>
 * 验证业务异常的各种构造方式，以及 code 和 message 是否正确设置。
 * 业务异常是项目里最常用的异常，比如"用户不存在"、"库存不足"都会抛它。
 * </p>
 */
@DisplayName("BusinessException 业务异常测试")
class BusinessExceptionTest {

    // ==================== 构造方法测试 ====================

    @Nested
    @DisplayName("构造方法")
    class ConstructorTest {

        @Test
        @DisplayName("(code, message)：基础构造，code和message都应正确")
        void constructWithCodeAndMessage() {
            BusinessException ex = new BusinessException(10001, "用户不存在");

            assertThat(ex.getCode()).isEqualTo(10001);
            assertThat(ex.getMessage()).isEqualTo("用户不存在");
        }

        @Test
        @DisplayName("(ErrorCode)：枚举构造，code和message来自枚举")
        void constructWithErrorCode() {
            BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND);

            assertThat(ex.getCode()).isEqualTo(11001);
            assertThat(ex.getMessage()).isEqualTo("用户不存在");
        }

        @Test
        @DisplayName("(ErrorCode, args...)：格式化参数，message应拼接参数")
        void constructWithErrorCodeAndArgs() {
            // 传一个参数
            BusinessException ex1 = new BusinessException(ErrorCode.PARAM_ERROR, "用户ID");
            assertThat(ex1.getCode()).isEqualTo(400);
            assertThat(ex1.getMessage()).isEqualTo("参数错误: 用户ID");
        }

        @Test
        @DisplayName("(ErrorCode, args...)：多个参数应按逗号拼接")
        void constructWithErrorCodeAndMultipleArgs() {
            // 传多个参数，应该用 ", " 拼接
            BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, "用户ID", 123, "非法");
            assertThat(ex.getMessage()).isEqualTo("参数错误: 用户ID, 123, 非法");
        }

        @Test
        @DisplayName("(ErrorCode, cause)：保留异常链，getCause应等于传入值")
        void constructWithErrorCodeAndCause() {
            Throwable rootCause = new RuntimeException("数据库连接失败");
            BusinessException ex = new BusinessException(ErrorCode.INTERNAL_ERROR, rootCause);

            assertThat(ex.getCode()).isEqualTo(500);
            assertThat(ex.getMessage()).isEqualTo("服务器内部错误");
            assertThat(ex.getCause()).isSameAs(rootCause);
        }

        @Test
        @DisplayName("(code, message, cause)：完整构造，三个字段都正确")
        void constructWithCodeMessageAndCause() {
            Throwable rootCause = new RuntimeException("IO异常");
            BusinessException ex = new BusinessException(500, "服务器错误", rootCause);

            assertThat(ex.getCode()).isEqualTo(500);
            assertThat(ex.getMessage()).isEqualTo("服务器错误");
            assertThat(ex.getCause()).isSameAs(rootCause);
        }
    }

    // ==================== formatArgs 私有方法间接测试 ====================

    @Nested
    @DisplayName("formatArgs 参数格式化（通过构造方法间接测试）")
    class FormatArgsTest {

        @Test
        @DisplayName("无参数：message应只有枚举本身的消息，不拼接")
        void noArgs() {
            // 注意：无参构造走的是 (ErrorCode) 而不是 (ErrorCode, args...)
            // 即使走 (ErrorCode, args...) 传空数组，也不应拼接
            BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR);
            assertThat(ex.getMessage()).isEqualTo("参数错误");
        }

        @Test
        @DisplayName("null参数数组：不应拼接，不抛NPE")
        void nullArgs() {
            // 传 null 作为可变参数
            BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, (Object[]) null);
            // formatArgs 检测到 null 会返回空字符串，但拼接时会有 ": " 分隔符
            // 实际 message = "参数错误: "（因为 formatArgs 返回 ""，但前面有 ": "）
            // 等等，看源码：super(errorCode.getMessage() + ": " + formatArgs(args));
            // formatArgs(null) 返回 ""，所以 message = "参数错误: "
            assertThat(ex.getMessage()).isEqualTo("参数错误: ");
        }

        @Test
        @DisplayName("包含null元素的参数：应拼接字符串null")
        void argsWithNullElement() {
            // 参数数组中包含 null 元素
            BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, "字段A", null, "字段B");
            // StringBuilder.append(null) 会拼接 "null"
            assertThat(ex.getMessage()).isEqualTo("参数错误: 字段A, null, 字段B");
        }
    }

    // ==================== 异常抛出测试 ====================

    @Test
    @DisplayName("assertThatThrownBy：抛出异常时应能被捕获并断言")
    void throwAndCatch() {
        // 验证异常可以被抛出并被 AssertJ 捕获
        assertThatThrownBy(() -> {
            throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);
        })
                .isInstanceOf(BusinessException.class)
                .hasMessage("库存不足")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30003));
    }

    @Test
    @DisplayName("BusinessException是RuntimeException：不需要在方法签名声明throws")
    void shouldBeRuntimeException() {
        // 验证继承关系：BusinessException 是 RuntimeException 的子类
        BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
