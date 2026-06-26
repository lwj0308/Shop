package com.shop.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.result.Result;
import com.shop.model.user.entity.User;
import com.shop.model.user.vo.UserBriefVO;
import com.shop.user.mapper.UserMapper;
import com.shop.user.service.FootprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户内部接口（供其他微服务通过 Feign 调用，不鉴权）
 * <p>
 * 供 shop-product 调用：
 * - 批量查询用户信息：评价列表展示时批量填充用户昵称和头像，避免对每个用户单独查询
 * - 记录浏览足迹：用户查看商品详情时，shop-product 通过 Feign 调用此处记录足迹
 * - 查询足迹分类：猜你喜欢推荐时，查询用户浏览过的商品分类，按分类查热销商品
 * </p>
 * <p>
 * 路径 /user/inner/** 在 SaTokenConfig 中加入白名单，不需要登录。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/inner")
@RequiredArgsConstructor
@Tag(name = "用户内部接口", description = "供其他微服务Feign调用，不鉴权")
public class UserInnerController {

    /** 用户Mapper，直接操作user表 */
    private final UserMapper userMapper;

    /** 浏览足迹服务，用于记录足迹和查询足迹分类 */
    private final FootprintService footprintService;

    /**
     * 批量查询用户简要信息
     * <p>
     * 供 shop-product 评价列表展示时使用：评价列表需要展示用户昵称和头像，
     * 一次性传入所有 userId，批量返回昵称和头像，避免对每个用户单独查询，减少RPC调用次数。
     * </p>
     * <p>
     * 只返回 userId、nickname、avatar 三个字段，不含手机号等敏感信息，保证安全。
     * </p>
     *
     * @param userIds 用户ID列表（多个用逗号分隔，比如 ?userIds=1,2,3）
     * @return 用户简要信息列表
     */
    @Operation(summary = "批量查询用户简要信息", description = "供其他微服务Feign调用，返回昵称和头像")
    @GetMapping("/batch")
    public Result<List<UserBriefVO>> batchGetUserInfo(@RequestParam List<Long> userIds) {
        // 参数校验：空列表直接返回空集合，避免SQL语法错误
        if (userIds == null || userIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 用 IN 查询一次性获取所有用户信息
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds)
                        .select(User::getId, User::getNickname, User::getAvatar)
        );

        // 实体转VO（只暴露必要的字段）
        List<UserBriefVO> voList = users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return Result.success(voList);
    }

    /**
     * 记录浏览足迹（供 shop-product Feign 调用）
     * <p>
     * 用户查看商品详情时，shop-product 调用此接口记录足迹。
     * 同一商品重复浏览只更新时间，不会产生重复记录。
     * categoryId 由 shop-product 传入（冗余存储，用于猜你喜欢按分类推荐）。
     * </p>
     *
     * @param userId     用户ID
     * @param productId  商品ID
     * @param categoryId 商品分类ID
     * @return 操作结果
     */
    @Operation(summary = "记录浏览足迹", description = "供shop-product调用，用户查看商品详情时记录足迹")
    @PostMapping("/footprint")
    public Result<Void> recordFootprint(@RequestParam Long userId,
                                        @RequestParam Long productId,
                                        @RequestParam Long categoryId) {
        footprintService.addFootprint(userId, productId, categoryId);
        return Result.success();
    }

    /**
     * 查询用户浏览过的商品分类ID列表（供 shop-product 猜你喜欢推荐使用）
     * <p>
     * shop-product 调用此接口拿到用户浏览过的分类，然后在这些分类下查热销商品作为推荐。
     * 返回去重后的分类ID列表。如果用户没有浏览记录，返回空列表（调用方降级为全站热销）。
     * </p>
     *
     * @param userId 用户ID
     * @return 去重后的分类ID列表
     */
    @Operation(summary = "查询用户足迹分类", description = "返回用户浏览过的商品分类ID列表，用于猜你喜欢推荐")
    @GetMapping("/footprint/categories")
    public Result<List<Long>> getFootprintCategories(@RequestParam Long userId) {
        List<Long> categoryIds = footprintService.getFootprintCategoryIds(userId);
        return Result.success(categoryIds);
    }

    /**
     * User实体转UserBriefVO
     * <p>只取出需要的字段：userId、nickname、avatar</p>
     *
     * @param user 用户实体
     * @return 用户简要信息VO
     */
    private UserBriefVO convertToVO(User user) {
        UserBriefVO vo = new UserBriefVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
