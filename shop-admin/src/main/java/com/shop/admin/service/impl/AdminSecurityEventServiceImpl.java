package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.admin.mapper.AdminSecurityEventMapper;
import com.shop.admin.service.AdminSecurityEventService;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.admin.dto.AdminSecurityEventHandleDTO;
import com.shop.model.admin.dto.AdminSecurityEventQueryDTO;
import com.shop.model.admin.entity.AdminSecurityEvent;
import com.shop.model.admin.vo.AdminSecurityEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员安全事件服务实现类
 * <p>
 * 记录系统中的安全相关事件，比如暴力破解尝试、异地登录、异常操作等。
 * 安全团队可以在后台查看和处理这些事件，保障系统安全。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSecurityEventServiceImpl implements AdminSecurityEventService {

    /** 安全事件Mapper，操作admin_security_event表 */
    private final AdminSecurityEventMapper adminSecurityEventMapper;

    /**
     * 记录安全事件
     * <p>
     * 创建一条安全事件记录并保存到数据库。
     * 新记录的状态默认为"未处理(0)"，安全团队后续可以标记为"已处理"或"已忽略"。
     * </p>
     *
     * @param eventType 事件类型，比如"BRUTE_FORCE"（暴力破解）、"ABNORMAL_LOGIN"（异常登录）
     * @param userId    关联管理员ID，可能为null
     * @param username  关联用户名
     * @param detail    事件详情
     * @param ip        触发事件的IP地址
     */
    @Override
    public void recordSecurityEvent(String eventType, Long userId, String username, String detail, String ip) {
        AdminSecurityEvent event = new AdminSecurityEvent();
        event.setEventType(eventType);
        event.setUserId(userId);
        event.setUsername(username);
        event.setDetail(detail);
        event.setIp(ip);
        // 状态：0未处理
        event.setStatus(0);

        adminSecurityEventMapper.insert(event);

        log.warn("记录安全事件，类型：{}，用户名：{}，详情：{}", eventType, username, detail);
    }

    /**
     * 分页查询安全事件列表
     * <p>
     * 支持按事件类型筛选、用户名模糊搜索、处理状态筛选、时间范围筛选。
     * 查询结果按创建时间倒序排列，最新的排在前面。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<AdminSecurityEventVO> getSecurityEventList(AdminSecurityEventQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<AdminSecurityEvent> wrapper = new LambdaQueryWrapper<>();
        // 事件类型精确筛选
        wrapper.eq(StringUtils.hasText(queryDTO.getEventType()), AdminSecurityEvent::getEventType, queryDTO.getEventType());
        // 用户名模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getUsername()), AdminSecurityEvent::getUsername, queryDTO.getUsername());
        // 处理状态精确筛选
        wrapper.eq(queryDTO.getStatus() != null, AdminSecurityEvent::getStatus, queryDTO.getStatus());
        // 时间范围筛选：大于等于开始时间
        wrapper.ge(queryDTO.getStartTime() != null, AdminSecurityEvent::getCreateTime, queryDTO.getStartTime());
        // 时间范围筛选：小于等于结束时间
        wrapper.le(queryDTO.getEndTime() != null, AdminSecurityEvent::getCreateTime, queryDTO.getEndTime());
        // 按创建时间倒序排列，最新的排在前面
        wrapper.orderByDesc(AdminSecurityEvent::getCreateTime);

        // 执行分页查询
        Page<AdminSecurityEvent> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<AdminSecurityEvent> resultPage = adminSecurityEventMapper.selectPage(page, wrapper);

        // 将实体列表转换为VO列表
        List<AdminSecurityEventVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.from(resultPage, voList);
    }

    /**
     * 处理安全事件
     * <p>
     * 管理员对安全事件进行处理，更新处理状态、处理备注和处理时间。
     * 找不到事件则抛出ADMIN_SECURITY_EVENT_NOT_FOUND异常。
     * </p>
     *
     * @param id  安全事件ID
     * @param dto 处理请求参数，包含处理状态和处理备注
     */
    @Override
    public void handleSecurityEvent(Long id, AdminSecurityEventHandleDTO dto) {
        // 查找安全事件，找不到则抛异常
        AdminSecurityEvent event = adminSecurityEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ErrorCode.ADMIN_SECURITY_EVENT_NOT_FOUND);
        }

        // 更新处理状态
        event.setStatus(dto.getStatus());
        // 更新处理备注
        event.setHandleNote(dto.getHandleNote());
        // 记录处理时间
        event.setHandleTime(LocalDateTime.now());

        adminSecurityEventMapper.updateById(event);

        log.info("处理安全事件成功，事件ID：{}，处理状态：{}", id, dto.getStatus());
    }

    /**
     * 将AdminSecurityEvent实体转换为AdminSecurityEventVO
     * <p>
     * 使用BeanUtils复制同名字段，简单快捷。
     * </p>
     *
     * @param entity 安全事件实体
     * @return 安全事件VO
     */
    private AdminSecurityEventVO convertToVO(AdminSecurityEvent entity) {
        AdminSecurityEventVO vo = new AdminSecurityEventVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
