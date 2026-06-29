package com.shop.common.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ErrorCode 错误码枚举的单元测试
 * <p>
 * 验证错误码枚举的 code 和 message 是否符合预期。
 * 错误码规范：5位数字，前2位是模块标识，后3位是具体错误编号。
 * </p>
 */
@DisplayName("ErrorCode 错误码枚举测试")
class ErrorCodeTest {

    @Test
    @DisplayName("SUCCESS：code应为200，message为操作成功")
    void successEnum() {
        assertThat(ErrorCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ErrorCode.SUCCESS.getMessage()).isEqualTo("操作成功");
    }

    @Test
    @DisplayName("通用错误码：10xxx 段位")
    void commonErrorCodes() {
        // 验证通用错误码段位
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(401);
        assertThat(ErrorCode.UNAUTHORIZED.getMessage()).isEqualTo("未登录");

        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(403);
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(500);
        assertThat(ErrorCode.PARAM_VALID_FAIL.getCode()).isEqualTo(10001);
        assertThat(ErrorCode.TOO_MANY_REQUESTS.getCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("用户模块错误码：11xxx 段位")
    void userErrorCodes() {
        assertThat(ErrorCode.USER_NOT_FOUND.getCode()).isEqualTo(11001);
        assertThat(ErrorCode.USER_PASSWORD_ERROR.getCode()).isEqualTo(11002);
        assertThat(ErrorCode.USER_PHONE_EXISTS.getCode()).isEqualTo(11003);
        assertThat(ErrorCode.USER_DISABLED.getCode()).isEqualTo(11004);
        // 验证用户错误码都在 11000-11999 范围内
        assertThat(ErrorCode.USER_NOT_FOUND.getCode()).isBetween(11000, 11999);
    }

    @Test
    @DisplayName("商家模块错误码：20xxx 段位")
    void merchantErrorCodes() {
        assertThat(ErrorCode.MERCHANT_NOT_FOUND.getCode()).isEqualTo(20001);
        assertThat(ErrorCode.MERCHANT_PASSWORD_ERROR.getCode()).isEqualTo(20006);
        assertThat(ErrorCode.SHOP_NOT_FOUND.getCode()).isEqualTo(20009);
    }

    @Test
    @DisplayName("商品模块错误码：30xxx 段位")
    void productErrorCodes() {
        assertThat(ErrorCode.PRODUCT_NOT_FOUND.getCode()).isEqualTo(30001);
        assertThat(ErrorCode.PRODUCT_OFF_SHELF.getCode()).isEqualTo(30002);
        assertThat(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode()).isEqualTo(30003);
        assertThat(ErrorCode.PRODUCT_SKU_NOT_FOUND.getCode()).isEqualTo(30008);
    }

    @Test
    @DisplayName("订单模块错误码：40xxx 段位")
    void orderErrorCodes() {
        assertThat(ErrorCode.ORDER_NOT_FOUND.getCode()).isEqualTo(40001);
        assertThat(ErrorCode.ORDER_STATUS_ERROR.getCode()).isEqualTo(40002);
        assertThat(ErrorCode.ORDER_NOT_YOURS.getCode()).isEqualTo(40008);
        assertThat(ErrorCode.REFUND_NOT_FOUND.getCode()).isEqualTo(40009);
    }

    @Test
    @DisplayName("支付模块错误码：50xxx 段位")
    void paymentErrorCodes() {
        assertThat(ErrorCode.PAYMENT_FAIL.getCode()).isEqualTo(50001);
        assertThat(ErrorCode.PAYMENT_NOT_FOUND.getCode()).isEqualTo(50004);
        assertThat(ErrorCode.PAYMENT_BALANCE_NOT_ENOUGH.getCode()).isEqualTo(50009);
    }

    @Test
    @DisplayName("购物车模块错误码：60xxx 段位")
    void cartErrorCodes() {
        assertThat(ErrorCode.CART_ITEM_NOT_FOUND.getCode()).isEqualTo(60001);
        assertThat(ErrorCode.CART_ITEM_EXISTS.getCode()).isEqualTo(60002);
        assertThat(ErrorCode.CART_ITEM_LIMIT_EXCEED.getCode()).isEqualTo(60003);
        assertThat(ErrorCode.CART_EMPTY.getCode()).isEqualTo(60004);
    }

    @Test
    @DisplayName("管理后台模块错误码：70xxx 段位")
    void adminErrorCodes() {
        assertThat(ErrorCode.ADMIN_NOT_FOUND.getCode()).isEqualTo(70001);
        assertThat(ErrorCode.ADMIN_USERNAME_EXISTS.getCode()).isEqualTo(70002);
        assertThat(ErrorCode.ADMIN_ROLE_NOT_FOUND.getCode()).isEqualTo(70007);
        assertThat(ErrorCode.ADMIN_NOTICE_NOT_FOUND.getCode()).isEqualTo(70018);
    }

    @Test
    @DisplayName("枚举完整性：所有枚举项的code和message都不为空")
    void allEnumsShouldHaveCodeAndMessage() {
        // 遍历所有枚举项，确保每个都有合法的 code 和非空 message
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getCode())
                    .as("枚举 %s 的 code 不能为 0", errorCode.name())
                    .isNotEqualTo(0);
            assertThat(errorCode.getMessage())
                    .as("枚举 %s 的 message 不能为空", errorCode.name())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("枚举唯一性：所有 code 互不相同")
    void allCodesShouldBeUnique() {
        // 收集所有 code 到列表
        var codes = java.util.Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .toList();
        // 去重后数量应该和原来一样
        var uniqueCodes = new java.util.HashSet<>(codes);
        assertThat(uniqueCodes).hasSize(codes.size());
    }
}
