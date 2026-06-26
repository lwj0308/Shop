package com.shop.user.controller;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.user.entity.UserFavorite;
import com.shop.user.service.FavoriteService;
import com.shop.user.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 收藏控制器
 * <p>
 * 处理添加收藏、取消收藏、收藏列表等接口。
 * 这些接口都需要登录后才能访问（由SaTokenConfig拦截器校验）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/favorite")
@RequiredArgsConstructor
@Tag(name = "收藏管理", description = "添加/取消收藏、收藏列表")
public class FavoriteController {

    /** 收藏服务 */
    private final FavoriteService favoriteService;

    /**
     * 添加收藏
     *
     * @param productId 商品ID
     * @return 操作结果
     */
    @PostMapping("/{productId}")
    @Operation(summary = "添加收藏", description = "收藏指定商品，不能重复收藏")
    public Result<Void> addFavorite(@PathVariable Long productId) {
        Long userId = UserContext.getUserId();
        favoriteService.addFavorite(userId, productId);
        return Result.success("收藏成功", null);
    }

    /**
     * 取消收藏
     *
     * @param productId 商品ID
     * @return 操作结果
     */
    @DeleteMapping("/{productId}")
    @Operation(summary = "取消收藏", description = "取消收藏指定商品")
    public Result<Void> removeFavorite(@PathVariable Long productId) {
        Long userId = UserContext.getUserId();
        favoriteService.removeFavorite(userId, productId);
        return Result.success("取消收藏成功", null);
    }

    /**
     * 获取收藏列表（分页）
     * <p>
     * 按收藏时间倒序排列，最新收藏的排在最前面。
     * </p>
     *
     * @param pageRequest 分页参数（pageNum, pageSize）
     * @return 收藏分页结果
     */
    @GetMapping("/list")
    @Operation(summary = "收藏列表", description = "分页获取当前用户的收藏列表，按收藏时间倒序")
    public Result<PageResult<UserFavorite>> getFavoriteList(@Validated PageRequest pageRequest) {
        Long userId = UserContext.getUserId();
        PageResult<UserFavorite> pageResult = favoriteService.getFavoriteList(userId, pageRequest);
        return Result.success(pageResult);
    }
}
