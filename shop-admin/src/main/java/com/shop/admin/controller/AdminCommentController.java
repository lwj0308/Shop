package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.AdminCommentFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.product.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 评价管理控制器
 * <p>
 * 管理后台对商品评价的管理接口，包括查询全平台评价列表、删除违规评价、管理员回复评价。
 * 所有接口都需要管理员拥有对应的权限才能访问（超级管理员自动放行）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/comment")
@Tag(name = "评价管理", description = "管理后台对商品评价的管理接口")
@RequiredArgsConstructor
public class AdminCommentController {

    /** 评价管理服务Feign客户端，远程调用商品服务 */
    private final AdminCommentFeignClient adminCommentFeignClient;

    /**
     * 分页查询全平台评价列表
     * <p>
     * 管理员查看所有商品的评价（不限商品ID），支持按评分类型筛选。
     * 需要 comment:list 权限。
     * </p>
     *
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @param scoreType 评分类型（可选）：all=全部 good=好评(4-5分) medium=中评(3分) bad=差评(1-2分)
     * @return 分页评价列表（含用户信息和追评列表）
     */
    @Operation(summary = "查询评价列表", description = "分页查询全平台评价列表，支持评分类型筛选")
    @GetMapping("/list")
    @RequirePermission("comment:list")
    @OperationLog(module = "评价管理", type = OperationType.QUERY, description = "查询评价列表")
    public Result<PageResult<CommentVO>> listComments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "评分类型：all全部 good好评 medium中评 bad差评") @RequestParam(required = false) String scoreType) {
        return adminCommentFeignClient.getAdminCommentList(pageNum, pageSize, scoreType);
    }

    /**
     * 删除评价
     * <p>
     * 管理员删除任意一条违规评价（逻辑删除，不会真的从数据库删掉）。
     * 需要 comment:delete 权限。
     * </p>
     *
     * @param commentId 评价ID
     * @return 操作结果
     */
    @Operation(summary = "删除评价", description = "管理员删除违规评价（逻辑删除）")
    @DeleteMapping("/{commentId}")
    @RequirePermission("comment:delete")
    @OperationLog(module = "评价管理", type = OperationType.DELETE, description = "删除评价：#commentId")
    public Result<Void> deleteComment(@Parameter(description = "评价ID") @PathVariable Long commentId) {
        return adminCommentFeignClient.deleteComment(commentId);
    }

    /**
     * 管理员回复评价
     * <p>
     * 管理员对用户评价进行回复，可以回复任意评价。
     * 需要 comment:reply 权限。
     * </p>
     *
     * @param commentId 评价ID
     * @param reply     回复内容
     * @return 操作结果
     */
    @Operation(summary = "管理员回复评价", description = "管理员对用户评价进行回复")
    @PostMapping("/{commentId}/reply")
    @RequirePermission("comment:reply")
    @OperationLog(module = "评价管理", type = OperationType.UPDATE, description = "管理员回复评价：#commentId")
    public Result<Void> replyComment(
            @Parameter(description = "评价ID") @PathVariable Long commentId,
            @Parameter(description = "回复内容") @RequestParam String reply) {
        return adminCommentFeignClient.adminReplyComment(commentId, reply);
    }
}
