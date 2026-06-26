package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.admin.mapper.AdminLoginLogMapper;
import com.shop.admin.service.AdminLoginLogService;
import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminLoginLogQueryDTO;
import com.shop.model.admin.entity.AdminLoginLog;
import com.shop.model.admin.vo.AdminLoginLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员登录日志服务实现类
 * <p>
 * 记录管理员的每一次登录尝试，包括成功和失败。
 * 登录日志可以用来检测异常登录行为（比如频繁失败可能是暴力破解），
 * 也可以用来审计管理员的登录历史。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLoginLogServiceImpl implements AdminLoginLogService {

    /** 登录日志Mapper，操作admin_login_log表 */
    private final AdminLoginLogMapper adminLoginLogMapper;

    /**
     * 记录登录日志
     * <p>
     * 创建一条登录日志记录并保存到数据库。
     * 登录成功时status=1，失败时status=0，失败时还会记录失败原因。
     * </p>
     *
     * @param username    登录用户名
     * @param ip          登录IP地址
     * @param browser     浏览器信息
     * @param os          操作系统信息
     * @param success     是否登录成功
     * @param failReason  失败原因，登录成功时传null
     */
    @Override
    public void recordLoginLog(String username, String ip, String browser, String os, boolean success, String failReason) {
        AdminLoginLog loginLog = new AdminLoginLog();
        loginLog.setUsername(username);
        loginLog.setIp(ip);
        loginLog.setBrowser(browser);
        loginLog.setOs(os);
        loginLog.setStatus(success ? 1 : 0);
        loginLog.setFailReason(failReason);
        loginLog.setLoginTime(LocalDateTime.now());

        adminLoginLogMapper.insert(loginLog);

        if (success) {
            log.debug("记录登录成功日志，用户名：{}", username);
        } else {
            log.debug("记录登录失败日志，用户名：{}，失败原因：{}", username, failReason);
        }
    }

    /**
     * 分页查询登录日志列表
     * <p>
     * 支持按用户名模糊搜索、IP模糊搜索、状态精确筛选、时间范围筛选。
     * 查询结果按登录时间倒序排列，最新的排在前面。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<AdminLoginLogVO> getLoginLogList(AdminLoginLogQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<AdminLoginLog> wrapper = new LambdaQueryWrapper<>();
        // 用户名模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getUsername()), AdminLoginLog::getUsername, queryDTO.getUsername());
        // IP模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getIp()), AdminLoginLog::getIp, queryDTO.getIp());
        // 状态精确筛选
        wrapper.eq(queryDTO.getStatus() != null, AdminLoginLog::getStatus, queryDTO.getStatus());
        // 时间范围筛选：大于等于开始时间
        wrapper.ge(queryDTO.getStartTime() != null, AdminLoginLog::getLoginTime, queryDTO.getStartTime());
        // 时间范围筛选：小于等于结束时间
        wrapper.le(queryDTO.getEndTime() != null, AdminLoginLog::getLoginTime, queryDTO.getEndTime());
        // 按登录时间倒序排列，最新的排在前面
        wrapper.orderByDesc(AdminLoginLog::getLoginTime);

        // 执行分页查询
        Page<AdminLoginLog> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<AdminLoginLog> resultPage = adminLoginLogMapper.selectPage(page, wrapper);

        // 将实体列表转换为VO列表
        List<AdminLoginLogVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.from(resultPage, voList);
    }

    /**
     * 将AdminLoginLog实体转换为AdminLoginLogVO
     * <p>
     * 使用BeanUtils复制同名字段，简单快捷。
     * </p>
     *
     * @param entity 登录日志实体
     * @return 登录日志VO
     */
    private AdminLoginLogVO convertToVO(AdminLoginLog entity) {
        AdminLoginLogVO vo = new AdminLoginLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
