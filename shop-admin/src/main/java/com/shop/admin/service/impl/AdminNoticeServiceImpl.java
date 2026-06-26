package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.admin.mapper.AdminNoticeMapper;
import com.shop.admin.service.AdminNoticeService;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.admin.dto.AdminNoticeCreateDTO;
import com.shop.model.admin.dto.AdminNoticeUpdateDTO;
import com.shop.model.admin.entity.AdminNotice;
import com.shop.model.admin.vo.AdminNoticeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 公告服务实现类
 * <p>
 * 实现公告分页查询、详情查询、新增、修改、删除等核心业务逻辑。
 * 公告数量可能较多，列表需要分页，支持按类型和状态筛选。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNoticeServiceImpl implements AdminNoticeService {

    /** 公告Mapper，操作admin_notice表 */
    private final AdminNoticeMapper adminNoticeMapper;

    /**
     * 分页查询公告列表
     * <p>
     * 1. 构建查询条件（支持按类型和状态筛选）
     * 2. 按创建时间降序排列（最新的公告排在前面）
     * 3. 执行分页查询
     * 4. 转换为VO并返回分页结果
     * </p>
     */
    @Override
    public PageResult<AdminNoticeVO> getNoticeList(Integer type, Integer status, int page, int size) {
        // 构建查询条件
        LambdaQueryWrapper<AdminNotice> queryWrapper = new LambdaQueryWrapper<>();
        // 按类型筛选（传了才过滤）
        if (type != null) {
            queryWrapper.eq(AdminNotice::getType, type);
        }
        // 按状态筛选（传了才过滤）
        if (status != null) {
            queryWrapper.eq(AdminNotice::getStatus, status);
        }
        // 按创建时间降序排列，最新的公告排在前面
        queryWrapper.orderByDesc(AdminNotice::getCreateTime);

        // 执行分页查询
        Page<AdminNotice> pageParam = new Page<>(page, size);
        Page<AdminNotice> resultPage = adminNoticeMapper.selectPage(pageParam, queryWrapper);

        // 转换为VO列表
        List<AdminNoticeVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 返回分页结果
        return PageResult.from(resultPage, voList);
    }

    /**
     * 根据ID查询公告详情
     * <p>
     * 查找公告，找不到则抛出ADMIN_NOTICE_NOT_FOUND异常。
     * </p>
     */
    @Override
    public AdminNoticeVO getNoticeById(Long id) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOTICE_NOT_FOUND);
        }
        return convertToVO(notice);
    }

    /**
     * 新增公告
     * <p>
     * 创建一条新的公告记录，设置默认值后保存。
     * </p>
     */
    @Override
    public void createNotice(AdminNoticeCreateDTO dto) {
        AdminNotice notice = new AdminNotice();
        notice.setTitle(dto.getTitle());
        notice.setContent(dto.getContent());
        notice.setType(dto.getType());
        // 状态不填默认为1（正常）
        notice.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        adminNoticeMapper.insert(notice);

        log.info("新增公告成功，公告ID：{}，标题：{}", notice.getId(), dto.getTitle());
    }

    /**
     * 修改公告信息
     * <p>
     * 1. 查找公告，找不到则抛异常
     * 2. 只更新传入的非空字段
     * </p>
     */
    @Override
    public void updateNotice(Long id, AdminNoticeUpdateDTO dto) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOTICE_NOT_FOUND);
        }

        // 只更新传入的非空字段
        if (dto.getTitle() != null) {
            notice.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            notice.setContent(dto.getContent());
        }
        if (dto.getType() != null) {
            notice.setType(dto.getType());
        }
        if (dto.getStatus() != null) {
            notice.setStatus(dto.getStatus());
        }
        adminNoticeMapper.updateById(notice);

        log.info("修改公告信息成功，公告ID：{}", id);
    }

    /**
     * 删除公告
     * <p>
     * 逻辑删除公告，MyBatis-Plus的deleteById会自动将deleted字段设为1。
     * </p>
     */
    @Override
    public void deleteNotice(Long id) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOTICE_NOT_FOUND);
        }

        // 逻辑删除
        adminNoticeMapper.deleteById(id);

        log.info("删除公告成功，公告ID：{}", id);
    }

    /**
     * 将AdminNotice实体转换为AdminNoticeVO
     *
     * @param notice 公告实体
     * @return 公告VO
     */
    private AdminNoticeVO convertToVO(AdminNotice notice) {
        AdminNoticeVO vo = new AdminNoticeVO();
        BeanUtils.copyProperties(notice, vo);
        return vo;
    }
}
