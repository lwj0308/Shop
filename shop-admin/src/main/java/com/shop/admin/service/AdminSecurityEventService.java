package com.shop.admin.service;

import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminSecurityEventHandleDTO;
import com.shop.model.admin.dto.AdminSecurityEventQueryDTO;
import com.shop.model.admin.vo.AdminSecurityEventVO;

/**
 * 管理员安全事件服务接口
 * <p>
 * 定义安全事件记录相关的业务方法。
 * 当检测到异常行为（比如暴力破解、异地登录等）时，记录安全事件，
 * 方便安全团队审计和处理。
 * </p>
 */
public interface AdminSecurityEventService {

    /**
     * 记录安全事件
     * <p>
     * 当系统检测到安全风险时调用，比如连续多次登录失败（暴力破解）、
     * 异地登录、异常操作等。安全团队可以在后台查看和处理这些事件。
     * </p>
     *
     * @param eventType 事件类型，比如"BRUTE_FORCE"（暴力破解）、"ABNORMAL_LOGIN"（异常登录）
     * @param userId    关联管理员ID，可能为null（比如用户名不存在时的暴力破解）
     * @param username  关联用户名
     * @param detail    事件详情，描述具体发生了什么
     * @param ip        触发事件的IP地址
     */
    void recordSecurityEvent(String eventType, Long userId, String username, String detail, String ip);

    /**
     * 分页查询安全事件列表
     * <p>
     * 支持按事件类型筛选、用户名模糊搜索、处理状态筛选、时间范围筛选。
     * 返回结果包含分页信息，方便前端展示。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult<AdminSecurityEventVO> getSecurityEventList(AdminSecurityEventQueryDTO queryDTO);

    /**
     * 处理安全事件
     * <p>
     * 管理员对安全事件进行处理，比如标记为"已处理"或"已忽略"，
     * 并填写处理说明。处理后会记录处理时间和处理备注。
     * </p>
     *
     * @param id  安全事件ID
     * @param dto 处理请求参数，包含处理状态和处理备注
     */
    void handleSecurityEvent(Long id, AdminSecurityEventHandleDTO dto);
}
