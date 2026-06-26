package com.shop.product.feign.fallback;

import com.shop.common.result.Result;
import com.shop.model.user.vo.UserBriefVO;
import com.shop.product.feign.UserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 用户服务Feign降级工厂
 * <p>
 * 当用户服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - batchGetUserInfo（批量查询用户信息）：降级返回空列表，调用方拿到空列表后会跳过用户信息填充，
 *   评价列表仍能展示（用户昵称和头像为空），不影响主流程。
 * - recordFootprint（记录足迹）：降级仅记日志，不影响商品查看主流程（足迹没记上而已）。
 * - getFootprintCategories（查足迹分类）：降级返回空列表，调用方降级为全站热销推荐。
 * </p>
 * <p>
 * 简单理解：就像你打电话问同事问题，同事没接电话（服务挂了），
 * 你就先按"不知道"处理（返回空列表），事情还得继续往下做，不能因为一个电话卡住。
 * </p>
 */
@Slf4j
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的UserFeignClient实例
     */
    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("用户服务调用失败，触发降级", cause);
        return new UserFeignClient() {

            /**
             * 批量查询用户信息降级：返回空列表
             * <p>
             * 查询类降级返回空列表，调用方（CommentServiceImpl）会判断空列表并跳过用户信息填充，
             * 保证评价列表查询主流程不被用户服务故障阻塞。
             * </p>
             *
             * @param userIds 用户ID列表
             * @return 降级返回空列表
             */
            @Override
            public Result<List<UserBriefVO>> batchGetUserInfo(List<Long> userIds) {
                log.warn("批量查询用户信息降级: userIds={}", userIds);
                return Result.success(Collections.emptyList());
            }

            /**
             * 记录浏览足迹降级：仅记日志，返回成功
             * <p>
             * 足迹记录是非关键操作（弱依赖），失败了不影响用户查看商品。
             * 就像你在商店看商品，监控坏了没拍到，但不影响你购物。
             * </p>
             *
             * @param userId     用户ID
             * @param productId  商品ID
             * @param categoryId 商品分类ID
             * @return 降级返回成功（不抛异常，不影响主流程）
             */
            @Override
            public Result<Void> recordFootprint(Long userId, Long productId, Long categoryId) {
                log.warn("记录浏览足迹降级（用户服务不可用）: userId={}, productId={}", userId, productId);
                return Result.success();
            }

            /**
             * 查询用户足迹分类降级：返回空列表
             * <p>
             * 返回空列表后，调用方（猜你喜欢）会降级为全站热销推荐，
             * 保证用户即使没有足迹数据也能看到推荐商品。
             * </p>
             *
             * @param userId 用户ID
             * @return 降级返回空列表
             */
            @Override
            public Result<List<Long>> getFootprintCategories(Long userId) {
                log.warn("查询用户足迹分类降级: userId={}", userId);
                return Result.success(Collections.emptyList());
            }
        };
    }
}
