package com.shop.admin.feign.fallback;

import com.shop.admin.feign.AdminCommentFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.vo.CommentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 评价管理服务Feign降级工厂
 * <p>
 * 当 shop-product 服务不可用时，走降级逻辑：
 * - 查询类接口（评价列表）：返回"服务暂不可用"，不影响管理员查看其他页面
 * - 写操作接口（删除、回复）：抛出业务异常，让管理员知道操作失败
 * </p>
 */
@Slf4j
@Component
public class AdminCommentFeignClientFallbackFactory implements FallbackFactory<AdminCommentFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的AdminCommentFeignClient实例
     */
    @Override
    public AdminCommentFeignClient create(Throwable cause) {
        log.error("评价管理服务调用失败，触发降级", cause);
        return new AdminCommentFeignClient() {

            /**
             * 查询评价列表降级：返回友好提示
             */
            @Override
            public Result<PageResult<CommentVO>> getAdminCommentList(int pageNum, int pageSize, String scoreType) {
                log.warn("查询评价列表降级: pageNum={}, pageSize={}, scoreType={}", pageNum, pageSize, scoreType);
                return Result.fail("评价服务暂不可用");
            }

            /**
             * 删除评价降级：抛出业务异常
             * <p>删除是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Void> deleteComment(Long commentId) {
                log.error("删除评价降级: commentId={}", commentId);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 管理员回复评价降级：抛出业务异常
             * <p>回复是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Void> adminReplyComment(Long commentId, String reply) {
                log.error("管理员回复评价降级: commentId={}", commentId);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
