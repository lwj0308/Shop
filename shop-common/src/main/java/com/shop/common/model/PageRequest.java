package com.shop.common.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页请求基类
 * <p>
 * 所有需要分页查询的接口都继承这个类，统一分页参数。
 * 前端只需要传pageNum和pageSize，后端自动处理分页逻辑。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 定义请求DTO
 *     public class UserQueryRequest extends PageRequest {
 *         private String keyword;  // 搜索关键词
 *     }
 *
 *     // Controller中使用
 *     public Result&lt;PageResult&lt;UserVO&gt;&gt; listUsers(UserQueryRequest request) {
 *         // request.getPageNum() 获取页码
 *         // request.getPageSize() 获取每页条数
 *     }
 * </pre>
 * </p>
 */
@Data
public class PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页码，默认第1页（前端不传就用默认值） */
    @Min(value = 1, message = "页码最小为1")
    private int pageNum = 1;

    /** 每页条数，默认10条（前端不传就用默认值） */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private int pageSize = 10;
}
