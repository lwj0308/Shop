package com.shop.admin.service;

import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminLoginLogQueryDTO;
import com.shop.model.admin.vo.AdminLoginLogVO;

/**
 * 管理员登录日志服务接口
 * <p>
 * 定义登录日志记录相关的业务方法。
 * 每次管理员登录（无论成功还是失败）都记录一条日志，方便安全审计和异常检测。
 * </p>
 */
public interface AdminLoginLogService {

    /**
     * 记录登录日志
     * <p>
     * 管理员每次尝试登录时调用，记录登录的用户名、IP、浏览器、操作系统等信息。
     * 登录成功和失败都会记录，失败时还会记录失败原因。
     * </p>
     *
     * @param username    登录用户名
     * @param ip          登录IP地址
     * @param browser     浏览器信息，比如"Chrome 120"
     * @param os          操作系统信息，比如"Windows 11"
     * @param success     是否登录成功
     * @param failReason  失败原因，登录成功时传null
     */
    void recordLoginLog(String username, String ip, String browser, String os, boolean success, String failReason);

    /**
     * 分页查询登录日志列表
     * <p>
     * 支持按用户名模糊搜索、IP模糊搜索、状态精确筛选、时间范围筛选。
     * 返回结果包含分页信息，方便前端展示。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult<AdminLoginLogVO> getLoginLogList(AdminLoginLogQueryDTO queryDTO);
}
