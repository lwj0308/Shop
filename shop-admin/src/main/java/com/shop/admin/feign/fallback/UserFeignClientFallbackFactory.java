package com.shop.admin.feign.fallback;

import com.shop.admin.feign.UserFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.user.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务Feign降级工厂
 * <p>
 * 当用户服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口（listUsers、getUserById）：降级返回友好提示，不影响用户体验
 * - 写操作接口（disableUser、enableUser）：降级时抛出业务异常，因为启禁用是关键操作，不能静默失败
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
             * 查询用户列表降级：返回"服务暂不可用"
             * <p>
             * 查询类接口降级不抛异常，返回友好提示。
             * 因为查询失败不会影响数据一致性。
             * </p>
             */
            @Override
            public Result<PageResult<UserVO>> listUsers(int page, int size, Integer status, String keyword) {
                log.warn("查询用户列表降级: page={}, size={}, status={}, keyword={}", page, size, status, keyword);
                return Result.fail("用户服务暂不可用");
            }

            /**
             * 查询用户详情降级：返回"服务暂不可用"
             */
            @Override
            public Result<UserVO> getUserById(Long userId) {
                log.warn("查询用户详情降级: userId={}", userId);
                return Result.fail("用户服务暂不可用");
            }

            /**
             * 禁用用户降级：抛出业务异常
             * <p>
             * 关键操作！禁用用户是管理操作，如果用户服务挂了导致禁用没成功，
             * 必须让管理员知道操作失败了，不能静默返回成功。
             * </p>
             */
            @Override
            public Result<Void> disableUser(Long userId) {
                log.error("禁用用户降级: userId={}", userId);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 启用用户降级：抛出业务异常
             * <p>
             * 同禁用用户，关键操作不能静默失败。
             * </p>
             */
            @Override
            public Result<Void> enableUser(Long userId) {
                log.error("启用用户降级: userId={}", userId);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
