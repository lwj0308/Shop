package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.user.entity.UserFootprint;
import com.shop.user.mapper.UserFootprintMapper;
import com.shop.user.service.FootprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 浏览足迹服务实现类
 * <p>
 * 实现添加浏览足迹和足迹列表等业务逻辑。
 * 同一商品重复浏览会更新时间，不会产生重复记录。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootprintServiceImpl implements FootprintService {

    /** 浏览足迹Mapper，操作user_footprint表 */
    private final UserFootprintMapper footprintMapper;

    /**
     * 添加浏览足迹
     * <p>
     * 如果用户已经浏览过这个商品，就更新浏览时间和分类；
     * 如果没有浏览过，就新增一条记录。
     * 这样足迹列表按最近浏览时间排序，方便用户找到最近看过的商品。
     * </p>
     */
    @Override
    public void addFootprint(Long userId, Long productId, Long categoryId) {
        // 查询是否已有该商品的足迹
        UserFootprint existing = footprintMapper.selectOne(
                new LambdaQueryWrapper<UserFootprint>()
                        .eq(UserFootprint::getUserId, userId)
                        .eq(UserFootprint::getProductId, productId)
        );

        if (existing != null) {
            // 已有足迹，更新时间和分类（商品可能换了分类，刷新为最新）
            existing.setCreateTime(LocalDateTime.now());
            existing.setCategoryId(categoryId);
            footprintMapper.updateById(existing);
        } else {
            // 没有足迹，新增一条
            UserFootprint footprint = new UserFootprint();
            footprint.setUserId(userId);
            footprint.setProductId(productId);
            footprint.setCategoryId(categoryId);
            footprintMapper.insert(footprint);
        }

        log.info("添加浏览足迹: userId={}, productId={}, categoryId={}", userId, productId, categoryId);
    }

    /**
     * 获取浏览足迹列表（分页）
     * <p>
     * 按浏览时间倒序排列，最近浏览的排在最前面。
     * </p>
     */
    @Override
    public PageResult<UserFootprint> getFootprintList(Long userId, PageRequest pageRequest) {
        // 构建分页查询条件
        Page<UserFootprint> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<UserFootprint> wrapper = new LambdaQueryWrapper<UserFootprint>()
                .eq(UserFootprint::getUserId, userId)
                .orderByDesc(UserFootprint::getCreateTime);

        // 执行查询
        Page<UserFootprint> result = footprintMapper.selectPage(page, wrapper);

        // 封装分页结果
        PageResult<UserFootprint> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    /**
     * 获取用户浏览过的商品分类ID列表（去重）
     * <p>
     * 用于"猜你喜欢"推荐：拿到用户浏览过的分类后，
     * shop-product 再去这些分类下查询热销商品作为推荐结果。
     * 用 SQL 的 GROUP BY 去重，避免返回重复的分类ID。
     * </p>
     */
    @Override
    public List<Long> getFootprintCategoryIds(Long userId) {
        // 只查 categoryId 字段，并按分类分组去重
        List<UserFootprint> list = footprintMapper.selectList(
                new LambdaQueryWrapper<UserFootprint>()
                        .select(UserFootprint::getCategoryId)
                        .eq(UserFootprint::getUserId, userId)
                        .isNotNull(UserFootprint::getCategoryId)
                        .groupBy(UserFootprint::getCategoryId)
        );

        // 提取 categoryId，过滤掉 null（理论上已通过 isNotNull 过滤，双重保险）
        return list.stream()
                .map(UserFootprint::getCategoryId)
                .collect(Collectors.toList());
    }
}
