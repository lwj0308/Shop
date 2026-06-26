package com.shop.admin.feign.fallback;

import com.shop.admin.feign.MerchantFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.merchant.dto.MerchantAuditDTO;
import com.shop.model.merchant.dto.WithdrawAuditDTO;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.merchant.vo.WithdrawOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商家服务Feign降级工厂
 * <p>
 * 当商家服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口：降级返回友好提示，不影响用户体验
 * - 写操作接口（审核、启禁用）：降级时抛出业务异常，因为这些都是关键操作
 * </p>
 */
@Slf4j
@Component
public class MerchantFeignClientFallbackFactory implements FallbackFactory<MerchantFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的MerchantFeignClient实例
     */
    @Override
    public MerchantFeignClient create(Throwable cause) {
        log.error("商家服务调用失败，触发降级", cause);
        return new MerchantFeignClient() {

            /**
             * 查询商家列表降级：返回"服务暂不可用"
             */
            @Override
            public Result<PageResult<MerchantVO>> listMerchants(int page, int size, Integer status, String keyword) {
                log.warn("查询商家列表降级: page={}, size={}, status={}, keyword={}", page, size, status, keyword);
                return Result.fail("商家服务暂不可用");
            }

            /**
             * 查询商家详情降级：返回"服务暂不可用"
             */
            @Override
            public Result<MerchantVO> getMerchantById(Long id) {
                log.warn("查询商家详情降级: id={}", id);
                return Result.fail("商家服务暂不可用");
            }

            /**
             * 审核商家降级：抛出业务异常
             * <p>
             * 审核是关键操作，如果商家服务挂了导致审核没成功，
             * 必须让管理员知道操作失败了。
             * </p>
             */
            @Override
            public Result<Void> auditMerchant(MerchantAuditDTO dto) {
                log.error("审核商家降级: merchantId={}", dto.getMerchantId());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 禁用商家降级：抛出业务异常
             */
            @Override
            public Result<Void> disableMerchant(Long id) {
                log.error("禁用商家降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 启用商家降级：抛出业务异常
             */
            @Override
            public Result<Void> enableMerchant(Long id) {
                log.error("启用商家降级: id={}", id);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 查询提现申请列表降级：返回"服务暂不可用"
             */
            @Override
            public Result<PageResult<WithdrawOrderVO>> adminGetWithdrawList(int page, int size, Integer status) {
                log.warn("查询提现申请列表降级: page={}, size={}, status={}", page, size, status);
                return Result.fail("商家服务暂不可用");
            }

            /**
             * 审核提现降级：抛出业务异常
             * <p>
             * 提现审核是关键操作，涉及资金变动，必须让管理员知道操作失败。
             * </p>
             */
            @Override
            public Result<Void> auditWithdraw(WithdrawAuditDTO dto) {
                log.error("审核提现降级: withdrawId={}", dto.getId());
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
        };
    }
}
