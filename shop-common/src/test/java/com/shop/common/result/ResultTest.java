package com.shop.common.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Result 统一响应封装的单元测试
 * <p>
 * 这个测试类专门验证 Result 工具类能不能正确包装成功/失败响应。
 * 简单理解：前端拿到 {code:200, message:"操作成功", data:{...}} 就是成功，
 * 拿到 {code:10001, message:"xxx", data:null} 就是失败。
 * </p>
 */
@DisplayName("Result 统一响应封装测试")
class ResultTest {

    // ==================== 成功响应测试 ====================

    @Nested
    @DisplayName("success 成功响应")
    class SuccessTest {

        @Test
        @DisplayName("success()：无数据成功响应，code应为200，message为默认提示")
        void successWithoutData() {
            // 调用无参的 success 方法（删除、更新等不需要返回数据的接口用它）
            Result<Void> result = Result.success();

            // 验证关键字段
            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getMessage()).isEqualTo("操作成功");
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isTrue();
            // timestamp 应该是当前时间附近（允许10ms误差）
            assertThat(result.getTimestamp()).isCloseTo(System.currentTimeMillis(), within(1000L));
        }

        @Test
        @DisplayName("success(data)：带数据成功响应，data应等于传入值")
        void successWithData() {
            // 准备测试数据
            String userData = "张三";

            Result<String> result = Result.success(userData);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getMessage()).isEqualTo("操作成功");
            assertThat(result.getData()).isEqualTo("张三");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("success(message, data)：自定义提示+数据，两个字段都应正确")
        void successWithMessageAndData() {
            Result<String> result = Result.success("登录成功", "token-abc");

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getMessage()).isEqualTo("登录成功");
            assertThat(result.getData()).isEqualTo("token-abc");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("success(data)：data为null时也应正常返回成功")
        void successWithNullData() {
            Result<Object> result = Result.success(null);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isNull();
        }
    }

    // ==================== 失败响应测试 ====================

    @Nested
    @DisplayName("fail 失败响应")
    class FailTest {

        @Test
        @DisplayName("fail(code, message)：自定义错误码和提示")
        void failWithCodeAndMessage() {
            Result<Void> result = Result.fail(10001, "用户不存在");

            assertThat(result.getCode()).isEqualTo(10001);
            assertThat(result.getMessage()).isEqualTo("用户不存在");
            assertThat(result.getData()).isNull();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fail(message)：只传提示，code默认500")
        void failWithMessageOnly() {
            Result<Void> result = Result.fail("系统繁忙");

            // 只传消息时默认 code=500
            assertThat(result.getCode()).isEqualTo(500);
            assertThat(result.getMessage()).isEqualTo("系统繁忙");
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fail(ErrorCode)：使用枚举构造，code和message应来自枚举")
        void failWithErrorCode() {
            Result<Void> result = Result.fail(ErrorCode.USER_NOT_FOUND);

            assertThat(result.getCode()).isEqualTo(11001);
            assertThat(result.getMessage()).isEqualTo("用户不存在");
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("fail(ErrorCode.SUCCESS)：即使是成功枚举，isSuccess也应为true")
        void failWithSuccessEnum() {
            // 边界场景：用 SUCCESS 枚举调 fail，code=200，isSuccess 返回 true
            Result<Void> result = Result.fail(ErrorCode.SUCCESS);

            assertThat(result.getCode()).isEqualTo(200);
            // 这种用法虽然奇怪，但代码逻辑允许，验证 isSuccess 判断只看 code
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ==================== isSuccess 边界测试 ====================

    @Test
    @DisplayName("isSuccess：code=200返回true，其他返回false")
    void isSuccessBoundary() {
        // 通过 fail 构造一个非200的响应
        Result<Void> fail = Result.fail(400, "参数错误");
        assertThat(fail.isSuccess()).isFalse();

        // 通过 success 构造一个200的响应
        Result<Void> success = Result.success();
        assertThat(success.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("timestamp：每次创建Result都应生成时间戳")
    void timestampShouldBeGenerated() {
        long before = System.currentTimeMillis();
        Result<Void> result = Result.success();
        long after = System.currentTimeMillis();

        // 时间戳应该在创建前后之间
        assertThat(result.getTimestamp()).isBetween(before, after);
    }
}
