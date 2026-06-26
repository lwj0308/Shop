package com.shop.admin.feign.fallback;

import com.shop.admin.feign.CouponFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponCreateDTO;
import com.shop.model.coupon.vo.CouponVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 优惠券服务Feign降级工厂
 * <p>
 * 当 shop-merchant 服务不可用时，走降级逻辑：
 * - 查询类接口：返回"服务暂不可用"，不影响管理员查看其他页面
 * - 写操作接口（创建、下架）：抛出业务异常，让管理员知道操作失败
 * </p>
 */
@Slf4j
@Component
public class CouponFeignClientFallbackFactory implements FallbackFactory<CouponFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的CouponFeignClient实例
     */
    @Override
    public CouponFeignClient create(Throwable cause) {
        log.error("优惠券服务调用失败，触发降级", cause);
        return new CouponFeignClient() {

            /**
             * 创建平台券降级：抛出业务异常
             * <p>创建是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Long> adminCreateCoupon(CouponCreateDTO dto) {
                log.error("创建平台券降级: couponName={}", dto.getName());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 查询优惠券列表降级：返回友好提示
             */
            @Override
            public Result<PageResult<CouponVO>> adminGetCouponList(int pageNum, int pageSize, Integer status, Integer type) {
                log.warn("查询优惠券列表降级: pageNum={}, pageSize={}, status={}, type={}", pageNum, pageSize, status, type);
                return Result.fail("优惠券服务暂不可用");
            }

            /**
             * 下架优惠券降级：抛出业务异常
             * <p>下架是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Void> adminOfflineCoupon(Long couponId) {
                log.error("下架优惠券降级: couponId={}", couponId);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
