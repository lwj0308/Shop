package com.shop.admin.service;

import com.shop.common.model.PageResult;
import com.shop.model.admin.dto.AdminNoticeCreateDTO;
import com.shop.model.admin.dto.AdminNoticeUpdateDTO;
import com.shop.model.admin.vo.AdminNoticeVO;

/**
 * 公告服务接口
 * <p>
 * 定义公告相关的业务方法，包括公告分页查询、详情查询、新增、修改、删除等。
 * 公告数量可能较多，所以列表需要分页。
 * </p>
 */
public interface AdminNoticeService {

    /**
     * 分页查询公告列表
     * <p>
     * 支持按类型和状态筛选，返回分页结果。
     * 公告数量可能很多，所以需要分页，避免一次返回太多数据。
     * </p>
     *
     * @param type   公告类型：1通知 2活动 3维护，为null则查全部
     * @param status 状态：0禁用 1正常，为null则查全部
     * @param page   当前页码
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<AdminNoticeVO> getNoticeList(Integer type, Integer status, int page, int size);

    /**
     * 根据ID查询公告详情
     * <p>
     * 查询单条公告的详细信息，找不到则抛出异常。
     * </p>
     *
     * @param id 公告ID
     * @return 公告详细信息
     */
    AdminNoticeVO getNoticeById(Long id);

    /**
     * 新增公告
     * <p>
     * 创建一条新的系统公告，比如发布"系统维护通知"。
     * </p>
     *
     * @param dto 新增参数，包含标题、内容、类型、状态等
     */
    void createNotice(AdminNoticeCreateDTO dto);

    /**
     * 修改公告信息
     * <p>
     * 修改公告的基本信息，只更新传入的字段。
     * </p>
     *
     * @param id  公告ID
     * @param dto 修改参数，包含标题、内容、类型、状态等
     */
    void updateNotice(Long id, AdminNoticeUpdateDTO dto);

    /**
     * 删除公告
     * <p>
     * 逻辑删除公告，删除后前端不再显示。
     * </p>
     *
     * @param id 公告ID
     */
    void deleteNotice(Long id);
}
