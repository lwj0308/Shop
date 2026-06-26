package com.shop.admin.feign.fallback;

import com.shop.admin.feign.RefundFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.order.dto.RefundAuditDTO;
import com.shop.model.order.vo.RefundVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 退款服务Feign降级工厂
 * <p>
 * 当订单服务不可用时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口（listRefunds）：降级返回友好提示
 * - 写操作接口（auditRefund）：降级时抛出业务异常，审核是关键操作
 * </p>
 */
@Slf4j
@Component
public class RefundFeignClientFallbackFactory implements FallbackFactory<RefundFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的RefundFeignClient实例
     */
    @Override
    public RefundFeignClient create(Throwable cause) {
        log.error("退款服务调用失败，触发降级", cause);
        return new RefundFeignClient() {

            /**
             * 查询退款列表降级：返回"服务暂不可用"
             */
            @Override
            public Result<PageResult<RefundVO>> listRefunds(int page, int size, Integer status) {
                log.warn("查询退款列表降级: page={}, size={}, status={}", page, size, status);
                return Result.fail("订单服务暂不可用");
            }

            /**
             * 审核退款降级：抛出业务异常
             * <p>
             * 审核是关键操作，如果订单服务挂了导致审核没成功，
             * 必须让管理员知道操作失败了。
             * </p>
             */
            @Override
            public Result<Void> auditRefund(RefundAuditDTO dto) {
                log.error("审核退款降级: refundId={}", dto.getRefundId());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
