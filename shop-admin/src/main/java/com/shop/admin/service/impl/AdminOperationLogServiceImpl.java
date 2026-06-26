package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.admin.mapper.AdminOperationLogMapper;
import com.shop.admin.service.AdminOperationLogService;
import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminOperationLogQueryDTO;
import com.shop.model.admin.entity.AdminOperationLog;
import com.shop.model.admin.vo.AdminOperationLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现类
 * <p>
 * 实现操作日志的记录和查询功能。
 * 记录日志使用@Async异步保存，不会阻塞主业务流程。
 * 查询日志支持多条件筛选和分页。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOperationLogServiceImpl implements AdminOperationLogService {

    /** 操作日志Mapper，操作admin_operation_log表 */
    private final AdminOperationLogMapper adminOperationLogMapper;

    /**
     * 记录操作日志（异步保存）
     * <p>
     * 使用@Async注解实现异步保存，这样日志保存不会影响接口的响应速度。
     * 即使日志保存失败，也不会影响主业务流程。
     * </p>
     *
     * @param operationLog 操作日志实体
     */
    @Async("adminAsyncExecutor")
    @Override
    public void recordOperationLog(AdminOperationLog operationLog) {
        try {
            adminOperationLogMapper.insert(operationLog);
        } catch (Exception e) {
            // 日志保存失败不影响主流程，只记录错误日志
            log.error("保存操作日志失败，模块：{}，操作类型：{}", operationLog.getModule(), operationLog.getOperationType(), e);
        }
    }

    /**
     * 分页查询操作日志列表
     * <p>
     * 支持按用户名模糊搜索、模块精确筛选、操作类型精确筛选、
     * 状态精确筛选、时间范围筛选。
     * 查询结果按创建时间倒序排列，最新的排在前面。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<AdminOperationLogVO> getOperationLogList(AdminOperationLogQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<>();
        // 用户名模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getUsername()), AdminOperationLog::getUsername, queryDTO.getUsername());
        // 模块精确筛选
        wrapper.eq(StringUtils.hasText(queryDTO.getModule()), AdminOperationLog::getModule, queryDTO.getModule());
        // 操作类型精确筛选
        wrapper.eq(StringUtils.hasText(queryDTO.getOperationType()), AdminOperationLog::getOperationType, queryDTO.getOperationType());
        // 状态精确筛选
        wrapper.eq(queryDTO.getStatus() != null, AdminOperationLog::getStatus, queryDTO.getStatus());
        // 时间范围筛选：大于等于开始时间
        wrapper.ge(queryDTO.getStartTime() != null, AdminOperationLog::getCreateTime, queryDTO.getStartTime());
        // 时间范围筛选：小于等于结束时间
        wrapper.le(queryDTO.getEndTime() != null, AdminOperationLog::getCreateTime, queryDTO.getEndTime());
        // 按创建时间倒序排列，最新的排在前面
        wrapper.orderByDesc(AdminOperationLog::getCreateTime);

        // 执行分页查询
        Page<AdminOperationLog> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<AdminOperationLog> resultPage = adminOperationLogMapper.selectPage(page, wrapper);

        // 将实体列表转换为VO列表
        List<AdminOperationLogVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.from(resultPage, voList);
    }

    /**
     * 将AdminOperationLog实体转换为AdminOperationLogVO
     * <p>
     * 使用BeanUtils复制同名字段，简单快捷。
     * </p>
     *
     * @param entity 操作日志实体
     * @return 操作日志VO
     */
    private AdminOperationLogVO convertToVO(AdminOperationLog entity) {
        AdminOperationLogVO vo = new AdminOperationLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
