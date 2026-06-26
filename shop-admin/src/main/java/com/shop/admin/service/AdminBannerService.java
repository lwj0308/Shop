package com.shop.admin.service;

import com.shop.model.admin.dto.AdminBannerCreateDTO;
import com.shop.model.admin.dto.AdminBannerUpdateDTO;
import com.shop.model.admin.vo.AdminBannerVO;

import java.util.List;

/**
 * Banner服务接口
 * <p>
 * 定义Banner相关的业务方法，包括Banner列表查询、详情查询、新增、修改、删除等。
 * Banner就是首页轮播图，数量通常不多，所以列表不需要分页。
 * </p>
 */
public interface AdminBannerService {

    /**
     * 获取Banner列表
     * <p>
     * 查询所有Banner，按排序号升序排列。
     * Banner数量通常不多，不需要分页，直接返回全部列表。
     * </p>
     *
     * @return Banner列表
     */
    List<AdminBannerVO> getBannerList();

    /**
     * 根据ID查询Banner详情
     * <p>
     * 查询单个Banner的详细信息，找不到则抛出异常。
     * </p>
     *
     * @param id Banner ID
     * @return Banner详细信息
     */
    AdminBannerVO getBannerById(Long id);

    /**
     * 新增Banner
     * <p>
     * 创建一个新的Banner，比如上传一张促销活动图。
     * </p>
     *
     * @param dto 新增参数，包含标题、图片地址、跳转链接、排序号、状态等
     */
    void createBanner(AdminBannerCreateDTO dto);

    /**
     * 修改Banner信息
     * <p>
     * 修改Banner的基本信息，只更新传入的字段。
     * </p>
     *
     * @param id  Banner ID
     * @param dto 修改参数，包含标题、图片地址、跳转链接、排序号、状态等
     */
    void updateBanner(Long id, AdminBannerUpdateDTO dto);

    /**
     * 删除Banner
     * <p>
     * 逻辑删除Banner，删除后前端不再显示。
     * </p>
     *
     * @param id Banner ID
     */
    void deleteBanner(Long id);
}
