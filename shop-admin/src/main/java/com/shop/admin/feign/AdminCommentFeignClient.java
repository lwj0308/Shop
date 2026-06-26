package com.shop.admin.feign;

import com.shop.admin.feign.fallback.AdminCommentFeignClientFallbackFactory;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.product.vo.CommentVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 评价管理服务Feign客户端
 * <p>
 * shop-admin（管理后台）通过此客户端调用 shop-product 的评价管理端接口。
 * 管理员可以查看全平台评价列表、删除违规评价、回复用户评价。
 * </p>
 * <p>
 * 鉴权说明：FeignAuthConfig 会自动把管理员的 Sa-Token 透传给 shop-product，
 * shop-product 的 /product/comment/admin/** 路径已在 SaTokenConfig 白名单中放行（内部接口不鉴权），
 * 真正的鉴权由 shop-admin 的 @RequirePermission 注解完成。
 * </p>
 */
@FeignClient(name = "shop-product", path = "/product/comment", contextId = "adminComment", fallbackFactory = AdminCommentFeignClientFallbackFactory.class)
public interface AdminCommentFeignClient {

    /**
     * 分页查询全平台评价列表
     * <p>
     * 管理员查看所有商品的评价（不限商品ID），支持按评分类型筛选。
     * </p>
     *
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @param scoreType 评分类型（可选）：all=全部 good=好评(4-5分) medium=中评(3分) bad=差评(1-2分)
     * @return 分页评价列表（含用户信息和追评列表）
     */
    @GetMapping("/admin/list")
    Result<PageResult<CommentVO>> getAdminCommentList(@RequestParam("pageNum") int pageNum,
                                                      @RequestParam("pageSize") int pageSize,
                                                      @RequestParam(value = "scoreType", required = false) String scoreType);

    /**
     * 删除评价
     * <p>
     * 管理员删除任意一条评价（逻辑删除）。
     * </p>
     *
     * @param commentId 评价ID
     * @return 操作结果
     */
    @DeleteMapping("/admin/{commentId}")
    Result<Void> deleteComment(@PathVariable("commentId") Long commentId);

    /**
     * 管理员回复评价
     * <p>
     * 管理员对用户评价进行回复，可以回复任意评价。
     * </p>
     *
     * @param commentId 评价ID
     * @param reply     回复内容
     * @return 操作结果
     */
    @PostMapping("/admin/{commentId}/reply")
    Result<Void> adminReplyComment(@PathVariable("commentId") Long commentId,
                                   @RequestParam("reply") String reply);
}
