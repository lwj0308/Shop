package com.shop.admin.feign.fallback;

import com.shop.admin.feign.SeckillFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.vo.SeckillVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 秒杀活动服务Feign降级工厂
 * <p>
 * 当 shop-merchant 服务不可用时，走降级逻辑：
 * - 查询类接口：返回"服务暂不可用"，不影响管理员查看其他页面
 * - 写操作接口（创建、下架）：抛出业务异常，让管理员知道操作失败
 * </p>
 */
@Slf4j
@Component
public class SeckillFeignClientFallbackFactory implements FallbackFactory<SeckillFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的SeckillFeignClient实例
     */
    @Override
    public SeckillFeignClient create(Throwable cause) {
        log.error("秒杀活动服务调用失败，触发降级", cause);
        return new SeckillFeignClient() {

            /**
             * 创建平台秒杀活动降级：抛出业务异常
             * <p>创建是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Long> adminCreateSeckill(SeckillCreateDTO dto) {
                log.error("创建平台秒杀活动降级: productId={}, skuId={}", dto.getProductId(), dto.getSkuId());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 查询秒杀活动列表降级：返回友好提示
             */
            @Override
            public Result<PageResult<SeckillVO>> adminGetSeckillList(int pageNum, int pageSize, Integer status) {
                log.warn("查询秒杀活动列表降级: pageNum={}, pageSize={}, status={}", pageNum, pageSize, status);
                return Result.fail("秒杀活动服务暂不可用");
            }

            /**
             * 下架秒杀活动降级：抛出业务异常
             * <p>下架是关键操作，服务不可用时必须让管理员知道</p>
             */
            @Override
            public Result<Void> adminOfflineSeckill(Long seckillId) {
                log.error("下架秒杀活动降级: seckillId={}", seckillId);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
