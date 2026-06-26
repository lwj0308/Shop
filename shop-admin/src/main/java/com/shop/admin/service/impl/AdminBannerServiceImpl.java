package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.admin.mapper.AdminBannerMapper;
import com.shop.admin.service.AdminBannerService;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.admin.dto.AdminBannerCreateDTO;
import com.shop.model.admin.dto.AdminBannerUpdateDTO;
import com.shop.model.admin.entity.AdminBanner;
import com.shop.model.admin.vo.AdminBannerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Banner服务实现类
 * <p>
 * 实现Banner列表查询、详情查询、新增、修改、删除等核心业务逻辑。
 * Banner数量通常不多，列表不需要分页，直接返回全部数据并按排序号排列。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBannerServiceImpl implements AdminBannerService {

    /** Banner Mapper，操作admin_banner表 */
    private final AdminBannerMapper adminBannerMapper;

    /**
     * 获取Banner列表
     * <p>
     * 查询所有Banner，按排序号升序排列。
     * Banner数量通常不多，不需要分页，直接返回全部列表。
     * </p>
     */
    @Override
    public List<AdminBannerVO> getBannerList() {
        // 按排序号升序查询所有Banner
        List<AdminBanner> bannerList = adminBannerMapper.selectList(
                new LambdaQueryWrapper<AdminBanner>().orderByAsc(AdminBanner::getSort)
        );

        // 转换为VO列表返回
        return bannerList.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID查询Banner详情
     * <p>
     * 查找Banner，找不到则抛出ADMIN_BANNER_NOT_FOUND异常。
     * </p>
     */
    @Override
    public AdminBannerVO getBannerById(Long id) {
        AdminBanner banner = adminBannerMapper.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.ADMIN_BANNER_NOT_FOUND);
        }
        return convertToVO(banner);
    }

    /**
     * 新增Banner
     * <p>
     * 创建一条新的Banner记录，设置默认值后保存。
     * </p>
     */
    @Override
    public void createBanner(AdminBannerCreateDTO dto) {
        AdminBanner banner = new AdminBanner();
        banner.setTitle(dto.getTitle());
        banner.setImage(dto.getImage());
        banner.setLink(dto.getLink());
        // 排序号不填默认为0
        banner.setSort(dto.getSort() != null ? dto.getSort() : 0);
        // 状态不填默认为1（正常）
        banner.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        adminBannerMapper.insert(banner);

        log.info("新增Banner成功，Banner ID：{}，标题：{}", banner.getId(), dto.getTitle());
    }

    /**
     * 修改Banner信息
     * <p>
     * 1. 查找Banner，找不到则抛异常
     * 2. 只更新传入的非空字段
     * </p>
     */
    @Override
    public void updateBanner(Long id, AdminBannerUpdateDTO dto) {
        AdminBanner banner = adminBannerMapper.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.ADMIN_BANNER_NOT_FOUND);
        }

        // 只更新传入的非空字段
        if (dto.getTitle() != null) {
            banner.setTitle(dto.getTitle());
        }
        if (dto.getImage() != null) {
            banner.setImage(dto.getImage());
        }
        if (dto.getLink() != null) {
            banner.setLink(dto.getLink());
        }
        if (dto.getSort() != null) {
            banner.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            banner.setStatus(dto.getStatus());
        }
        adminBannerMapper.updateById(banner);

        log.info("修改Banner信息成功，Banner ID：{}", id);
    }

    /**
     * 删除Banner
     * <p>
     * 逻辑删除Banner，MyBatis-Plus的deleteById会自动将deleted字段设为1。
     * </p>
     */
    @Override
    public void deleteBanner(Long id) {
        AdminBanner banner = adminBannerMapper.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.ADMIN_BANNER_NOT_FOUND);
        }

        // 逻辑删除
        adminBannerMapper.deleteById(id);

        log.info("删除Banner成功，Banner ID：{}", id);
    }

    /**
     * 将AdminBanner实体转换为AdminBannerVO
     *
     * @param banner Banner实体
     * @return Banner VO
     */
    private AdminBannerVO convertToVO(AdminBanner banner) {
        AdminBannerVO vo = new AdminBannerVO();
        BeanUtils.copyProperties(banner, vo);
        return vo;
    }
}
