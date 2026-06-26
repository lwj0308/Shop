package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.user.entity.UserFavorite;
import com.shop.user.mapper.UserFavoriteMapper;
import com.shop.user.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 收藏服务实现类
 * <p>
 * 实现添加收藏、取消收藏、收藏列表等业务逻辑。
 * 同一商品不能重复收藏（数据库唯一索引保证）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    /** 收藏Mapper，操作user_favorite表 */
    private final UserFavoriteMapper favoriteMapper;

    /**
     * 添加收藏
     * <p>
     * 先检查是否已收藏，已收藏则提示"已收藏"，未收藏则添加。
     * </p>
     */
    @Override
    public void addFavorite(Long userId, Long productId) {
        // 检查是否已收藏
        Long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getProductId, productId)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "已收藏该商品");
        }

        // 添加收藏
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favoriteMapper.insert(favorite);

        log.info("添加收藏: userId={}, productId={}", userId, productId);
    }

    /**
     * 取消收藏
     * <p>
     * 根据用户ID和商品ID删除收藏记录。
     * </p>
     */
    @Override
    public void removeFavorite(Long userId, Long productId) {
        favoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getProductId, productId)
        );
        log.info("取消收藏: userId={}, productId={}", userId, productId);
    }

    /**
     * 获取收藏列表（分页）
     * <p>
     * 按收藏时间倒序排列，最新收藏的排在最前面。
     * </p>
     */
    @Override
    public PageResult<UserFavorite> getFavoriteList(Long userId, PageRequest pageRequest) {
        // 构建分页查询条件
        Page<UserFavorite> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .orderByDesc(UserFavorite::getCreateTime);

        // 执行查询
        Page<UserFavorite> result = favoriteMapper.selectPage(page, wrapper);

        // 封装分页结果
        PageResult<UserFavorite> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }
}
