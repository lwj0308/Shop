package com.shop.user.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.user.entity.UserFootprint;

import java.util.List;

/**
 * 浏览足迹服务接口
 * <p>
 * 定义浏览足迹相关的业务方法，包括添加足迹和足迹列表。
 * </p>
 */
public interface FootprintService {

    /**
     * 添加浏览足迹
     * <p>
     * 同一商品不重复添加，如果已经浏览过就更新浏览时间。
     * 这样可以保证足迹列表按最近浏览时间排序。
     * </p>
     *
     * @param userId     用户ID
     * @param productId  商品ID
     * @param categoryId 商品分类ID（冗余存储，用于猜你喜欢按分类推荐）
     */
    void addFootprint(Long userId, Long productId, Long categoryId);

    /**
     * 获取浏览足迹列表（分页）
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @return 足迹分页结果
     */
    PageResult<UserFootprint> getFootprintList(Long userId, PageRequest pageRequest);

    /**
     * 获取用户浏览过的商品分类ID列表（去重）
     * <p>
     * 用于"猜你喜欢"推荐：根据用户浏览过的商品分类，
     * 查询这些分类下的热销商品作为推荐结果。
     * </p>
     *
     * @param userId 用户ID
     * @return 去重后的分类ID列表
     */
    List<Long> getFootprintCategoryIds(Long userId);
}
