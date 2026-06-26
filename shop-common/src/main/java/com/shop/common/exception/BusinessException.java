package com.shop.common.exception;

import com.shop.common.result.ErrorCode;
import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 当业务逻辑不满足条件时抛出这个异常，比如"库存不足"、"用户不存在"等。
 * 全局异常处理器（GlobalExceptionHandler）会捕获它，自动转换为Result格式返回给前端。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 方式1：直接传错误码和消息
 *     throw new BusinessException(10001, "用户不存在");
 *
 *     // 方式2：使用ErrorCode枚举（推荐，更规范）
 *     throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 *
 *     // 方式3：使用ErrorCode枚举 + 格式化参数
 *     throw new BusinessException(ErrorCode.PARAM_ERROR, "用户ID");
 *     // 结果消息："参数错误: 用户ID"
 *
 *     // 方式4：使用ErrorCode枚举 + 原始异常（保留异常链，方便排查问题）
 *     throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
 * </pre>
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final int code;

    /**
     * 构造方法 - 指定错误码和错误消息
     *
     * @param code    错误码，比如10001
     * @param message 错误消息，比如"用户不存在"
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法 - 使用ErrorCode枚举（推荐用法）
     * 好处是错误码和消息集中管理，不会写错
     *
     * @param errorCode 错误码枚举，比如ErrorCode.USER_NOT_FOUND
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 构造方法 - 使用ErrorCode枚举 + 格式化参数
     * 可以在枚举消息后面追加更多信息，让错误提示更具体
     *
     * @param errorCode 错误码枚举
     * @param args      格式化参数，会拼接到消息后面
     */
    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage() + ": " + formatArgs(args));
        this.code = errorCode.getCode();
    }

    /**
     * 构造方法 - 使用ErrorCode枚举 + 原始异常
     * 保留异常链，方便排查问题根源
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常（真正导致问题的异常）
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

    /**
     * 构造方法 - 指定错误码、错误消息和原始异常
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 将格式化参数拼接成字符串
     * 比如传入 "用户ID", 123 → "用户ID, 123"
     *
     * @param args 格式化参数数组
     * @return 拼接后的字符串
     */
    private static String formatArgs(Object... args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
