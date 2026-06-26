package com.shop.admin.service;

import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminOperationLogQueryDTO;
import com.shop.model.admin.entity.AdminOperationLog;
import com.shop.model.admin.vo.AdminOperationLogVO;

/**
 * 操作日志服务接口
 * <p>
 * 定义操作日志相关的业务方法，包括记录日志和分页查询日志。
 * 操作日志记录管理员在后台的每一次操作，方便审计和排查问题。
 * </p>
 */
public interface AdminOperationLogService {

    /**
     * 记录操作日志
     * <p>
     * 保存一条操作日志到数据库。由OperationLogAspect切面自动调用，
     * 业务代码一般不需要手动调用此方法。
     * 使用异步方式保存，不影响主业务流程的响应速度。
     * </p>
     *
     * @param operationLog 操作日志实体，包含操作人、模块、请求参数等信息
     */
    void recordOperationLog(AdminOperationLog operationLog);

    /**
     * 分页查询操作日志列表
     * <p>
     * 支持按用户名、模块、操作类型、状态、时间范围等条件筛选。
     * 返回结果包含分页信息，方便前端展示。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult<AdminOperationLogVO> getOperationLogList(AdminOperationLogQueryDTO queryDTO);
}
