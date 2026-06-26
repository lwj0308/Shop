package com.shop.admin.feign.fallback;

import com.shop.admin.feign.PromotionFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.promotion.dto.PromotionCreateDTO;
import com.shop.model.promotion.vo.PromotionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 满减活动服务Feign降级工厂
 * <p>
 * 当 shop-merchant 服务不可用时，走降级逻辑：
 * - 查询类接口：返回"服务暂不可用"，不影响管理员查看其他页面
 * - 写操作接口（创建、下架）：抛出业务异常，让管理员知道操作失败
 * </p>
 */
@Slf4j
@Component
public class PromotionFeignClientFallbackFactory implements FallbackFactory<PromotionFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的PromotionFeignClient实例
     */
    @Override
    public PromotionFeignClient create(Throwable cause) {
        log.error("满减活动服务调用失败，触发降级", cause);
        return new PromotionFeignClient() {

            /**
             * 创建平台满减活动降级：抛出业务异常
             * <p>创建是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Long> adminCreatePromotion(PromotionCreateDTO dto) {
                log.error("创建平台满减活动降级: promotionName={}", dto.getName());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 查询满减活动列表降级：返回友好提示
             */
            @Override
            public Result<PageResult<PromotionVO>> adminGetPromotionList(int pageNum, int pageSize, Integer status) {
                log.warn("查询满减活动列表降级: pageNum={}, pageSize={}, status={}", pageNum, pageSize, status);
                return Result.fail("满减活动服务暂不可用");
            }

            /**
             * 下架满减活动降级：抛出业务异常
             * <p>下架是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Void> adminOfflinePromotion(Long promotionId) {
                log.error("下架满减活动降级: promotionId={}", promotionId);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
