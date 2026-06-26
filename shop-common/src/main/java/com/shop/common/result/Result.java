package com.shop.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一API响应结果封装
 * <p>
 * 所有接口返回值都使用这个类包装，保证前端接收到的数据格式一致。
 * 前端只需要判断code是否为200，就知道请求成功还是失败了。
 * </p>
 *
 * @param <T> 业务数据的类型，比如返回用户信息就是Result<User>
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码：200表示成功，其他表示失败 */
    private int code;

    /** 提示信息，比如"操作成功"、"用户不存在"等 */
    private String message;

    /** 业务数据，泛型T表示可以是任何类型 */
    private T data;

    /** 时间戳，方便排查问题 */
    private long timestamp;

    /**
     * 私有构造方法，强制使用静态工厂方法创建对象
     * 这样可以避免直接new对象时忘记设置必要字段
     */
    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应 - 无数据（用于删除、更新等不需要返回数据的操作）
     * 用法：Result.success() → {"code":200, "message":"操作成功", "data":null}
     *
     * @return 封装好的成功响应
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        return result;
    }

    /**
     * 成功响应 - 带数据
     * 用法：Result.success(user) → {"code":200, "message":"操作成功", "data":{...}}
     *
     * @param data 返回给前端的业务数据
     * @return 封装好的成功响应
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    /**
     * 成功响应 - 自定义提示信息 + 数据
     * 用法：Result.success("登录成功", token) → {"code":200, "message":"登录成功", "data":"xxx"}
     *
     * @param message 自定义的成功提示
     * @param data    返回给前端的业务数据
     * @return 封装好的成功响应
     */
    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 失败响应 - 自定义错误码和提示
     * 用法：Result.fail(10001, "用户不存在") → {"code":10001, "message":"用户不存在", "data":null}
     *
     * @param code    错误码，建议使用ErrorCode枚举中定义的
     * @param message 错误提示信息
     * @return 封装好的失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 失败响应 - 只传提示信息，错误码默认500
     * 用法：Result.fail("系统繁忙") → {"code":500, "message":"系统繁忙", "data":null}
     *
     * @param message 错误提示信息
     * @return 封装好的失败响应
     */
    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    /**
     * 失败响应 - 使用ErrorCode枚举
     * 用法：Result.fail(ErrorCode.USER_NOT_FOUND) → {"code":10001, "message":"用户不存在", "data":null}
     *
     * @param errorCode 错误码枚举
     * @return 封装好的失败响应
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 判断当前响应是否成功
     * 用法：if (result.isSuccess()) { ... }
     *
     * @return true表示成功（code=200），false表示失败
     */
    public boolean isSuccess() {
        return this.code == 200;
    }
}
