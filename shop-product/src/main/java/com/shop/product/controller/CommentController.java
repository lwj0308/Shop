package com.shop.product.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.product.dto.CommentAppendDTO;
import com.shop.model.product.dto.CommentDTO;
import com.shop.model.product.dto.CommentReplyDTO;
import com.shop.model.product.vo.CommentVO;
import com.shop.product.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 商品评价控制器
 * <p>
 * 处理添加评价、评价列表、商家回复评价、追评等接口。
 * 添加评价和追评需要用户登录，评价列表公开访问。
 * 商家回复评价和按店铺查询评价是内部接口，供 shop-merchant 通过 Feign 调用。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/product/comment")
@RequiredArgsConstructor
@Tag(name = "商品评价", description = "添加评价、评价列表、商家回复评价、追评")
public class CommentController {

    /** 评价服务 */
    private final CommentService commentService;

    // ==================== 用户端接口（需要登录） ====================

    /**
     * 添加评价（初始评价）
     * <p>
     * 用户购买商品后发表评价，需要登录。
     * 会校验订单归属当前用户，并防止重复评价。
     * </p>
     *
     * @param dto 评价参数（productId、orderId、orderItemId、content、images、score、isAnonymous）
     * @return 操作结果
     */
    @PostMapping
    @SaCheckLogin
    @Operation(summary = "添加评价", description = "用户购买商品后发表评价，需要登录，校验订单归属+防重复")
    public Result<Void> addComment(@Validated @RequestBody CommentDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        commentService.addComment(userId, dto);
        return Result.success("评价成功", null);
    }

    /**
     * 追评（在初始评价之后追加评价）
     * <p>
     * 用户在初始评价之后，可以追加一条追评。追评关联到初始评价（通过 parentId）。
     * 需要登录，会校验初始评价存在且属于当前用户，并防止重复追评。
     * </p>
     *
     * @param dto 追评参数（parentId、content、images）
     * @return 操作结果
     */
    @PostMapping("/append")
    @SaCheckLogin
    @Operation(summary = "追评", description = "用户在初始评价之后追加评价，需要登录，校验归属+防重复追评")
    public Result<Void> appendComment(@RequestBody @Valid CommentAppendDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        commentService.appendComment(userId, dto);
        return Result.success("追评成功", null);
    }

    // ==================== C端接口（公开访问） ====================

    /**
     * 评价列表
     * <p>
     * 获取商品的评价列表，分页返回，公开访问。
     * 支持按评分类型筛选（全部/好评/中评/差评），返回结果包含用户昵称头像和追评列表。
     * </p>
     *
     * @param productId   商品ID
     * @param scoreType   评分类型筛选（可选）：all=全部 good=好评(4-5分) medium=中评(3分) bad=差评(1-2分)，默认all
     * @param pageRequest 分页参数
     * @return 分页评价列表（含用户信息和追评列表）
     */
    @GetMapping("/list")
    @Operation(summary = "评价列表", description = "获取商品的评价列表，分页返回，支持评分筛选，公开访问")
    public Result<PageResult<CommentVO>> getCommentList(
            @Parameter(description = "商品ID") @RequestParam Long productId,
            @Parameter(description = "评分类型：all全部 good好评 medium中评 bad差评，默认all") @RequestParam(required = false, defaultValue = "all") String scoreType,
            PageRequest pageRequest) {
        return Result.success(commentService.getCommentList(productId, scoreType, pageRequest));
    }

    // ==================== 商家端内部接口（不鉴权，供 shop-merchant Feign 调用） ====================

    /**
     * 按店铺查询评价列表（内部接口）
     * <p>
     * 商家在评价管理页面查看自己店铺商品收到的所有评价。
     * 这个接口不鉴权，由 shop-merchant 鉴权后通过 Feign 调用。
     * </p>
     *
     * @param shopId      店铺ID
     * @param hasReply    是否已回复：true只看已回复，false只看未回复，不传看全部
     * @param pageRequest 分页参数
     * @return 分页评价列表（含商品名称）
     */
    @GetMapping("/shop/list")
    @Operation(summary = "按店铺查询评价列表", description = "商家端内部接口，按店铺ID分页查询评价")
    public Result<PageResult<CommentVO>> getCommentListByShopId(
            @Parameter(description = "店铺ID") @RequestParam Long shopId,
            @Parameter(description = "是否已回复：true已回复，false未回复，不传全部") @RequestParam(required = false) Boolean hasReply,
            PageRequest pageRequest) {
        return Result.success(commentService.getCommentListByShopId(shopId, hasReply, pageRequest));
    }

    /**
     * 商家回复评价（内部接口）
     * <p>
     * 商家对用户的评价进行回复。
     * 这个接口不鉴权，由 shop-merchant 鉴权后通过 Feign 调用。
     * </p>
     *
     * @param shopId 店铺ID（用于校验评价归属）
     * @param dto    回复参数（评价ID + 回复内容）
     * @return 操作结果
     */
    @PostMapping("/reply")
    @Operation(summary = "商家回复评价", description = "商家端内部接口，回复用户评价")
    public Result<Void> replyComment(
            @Parameter(description = "店铺ID") @RequestParam Long shopId,
            @Validated @RequestBody CommentReplyDTO dto) {
        commentService.replyComment(shopId, dto);
        return Result.success("回复成功", null);
    }

    // ==================== 管理端内部接口（不鉴权，供 shop-admin Feign 调用） ====================

    /**
     * 管理端-评价列表（内部接口）
     * <p>
     * 管理员查看全平台所有商品的评价，不限商品ID。
     * 这个接口不鉴权，由 shop-admin 鉴权后通过 Feign 调用。
     * </p>
     *
     * @param scoreType   评分类型筛选（可选）：all=全部 good=好评(4-5分) medium=中评(3分) bad=差评(1-2分)，默认all
     * @param pageRequest 分页参数
     * @return 分页评价列表（含用户信息和追评列表）
     */
    @GetMapping("/admin/list")
    @Operation(summary = "管理端-评价列表", description = "管理端内部接口，查询全平台评价列表")
    public Result<PageResult<CommentVO>> getAdminCommentList(
            @Parameter(description = "评分类型：all全部 good好评 medium中评 bad差评，默认all") @RequestParam(required = false, defaultValue = "all") String scoreType,
            PageRequest pageRequest) {
        return Result.success(commentService.getAdminCommentList(scoreType, pageRequest));
    }

    /**
     * 管理端-删除评价（内部接口）
     * <p>
     * 管理员删除任意一条评价（逻辑删除）。
     * 这个接口不鉴权，由 shop-admin 鉴权后通过 Feign 调用。
     * </p>
     *
     * @param commentId 评价ID
     * @return 操作结果
     */
    @DeleteMapping("/admin/{commentId}")
    @Operation(summary = "管理端-删除评价", description = "管理端内部接口，逻辑删除评价")
    public Result<Void> deleteComment(@Parameter(description = "评价ID") @PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return Result.success("删除成功", null);
    }

    /**
     * 管理端-管理员回复评价（内部接口）
     * <p>
     * 管理员对用户评价进行回复，可以回复任意评价（不需要校验店铺归属）。
     * 这个接口不鉴权，由 shop-admin 鉴权后通过 Feign 调用。
     * </p>
     *
     * @param commentId 评价ID
     * @param reply     回复内容
     * @return 操作结果
     */
    @PostMapping("/admin/{commentId}/reply")
    @Operation(summary = "管理端-管理员回复评价", description = "管理端内部接口，管理员回复用户评价")
    public Result<Void> adminReplyComment(
            @Parameter(description = "评价ID") @PathVariable Long commentId,
            @Parameter(description = "回复内容") @RequestParam String reply) {
        commentService.adminReplyComment(commentId, reply);
        return Result.success("回复成功", null);
    }
}
