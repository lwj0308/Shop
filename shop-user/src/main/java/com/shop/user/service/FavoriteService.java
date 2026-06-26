package com.shop.user.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.user.entity.UserFavorite;

/**
 * 收藏服务接口
 * <p>
 * 定义收藏相关的业务方法，包括添加收藏、取消收藏、收藏列表。
 * </p>
 */
public interface FavoriteService {

    /**
     * 添加收藏
     * <p>
     * 同一商品不能重复收藏，如果已经收藏过就提示"已收藏"。
     * </p>
     *
     * @param userId    用户ID
     * @param productId 商品ID
     */
    void addFavorite(Long userId, Long productId);

    /**
     * 取消收藏
     *
     * @param userId    用户ID
     * @param productId 商品ID
     */
    void removeFavorite(Long userId, Long productId);

    /**
     * 获取收藏列表（分页）
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @return 收藏分页结果
     */
    PageResult<UserFavorite> getFavoriteList(Long userId, PageRequest pageRequest);
}
