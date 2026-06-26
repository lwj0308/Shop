package com.shop.admin.feign.fallback;

import com.shop.admin.feign.OrderFeignClient;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.order.vo.AdminTodayStatsVO;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 订单服务Feign降级工厂
 * <p>
 * 当订单服务不可用时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 查询类接口（listOrders、getOrderDetail）：降级返回友好提示
 * - 写操作接口（deliverOrder）：降级时抛出业务异常，发货是关键操作
 * </p>
 */
@Slf4j
@Component
public class OrderFeignClientFallbackFactory implements FallbackFactory<OrderFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的OrderFeignClient实例
     */
    @Override
    public OrderFeignClient create(Throwable cause) {
        log.error("订单服务调用失败，触发降级", cause);
        return new OrderFeignClient() {

            /**
             * 查询订单列表降级：返回"服务暂不可用"
             */
            @Override
            public Result<PageResult<OrderVO>> listOrders(int page, int size, Integer status, String orderNo) {
                log.warn("查询订单列表降级: page={}, size={}, status={}, orderNo={}", page, size, status, orderNo);
                return Result.fail("订单服务暂不可用");
            }

            /**
             * 查询订单详情降级：返回"服务暂不可用"
             */
            @Override
            public Result<OrderDetailVO> getOrderDetail(Long id) {
                log.warn("查询订单详情降级: id={}", id);
                return Result.fail("订单服务暂不可用");
            }

            /**
             * 发货降级：抛出业务异常
             * <p>
             * 发货是关键操作，如果订单服务挂了导致发货没成功，
             * 必须让管理员知道操作失败了，不能静默返回成功。
             * </p>
             */
            @Override
            public Result<Void> deliverOrder(Long id, String logisticsNo, String logisticsCompany) {
                log.error("发货降级: id={}, logisticsNo={}, logisticsCompany={}", id, logisticsNo, logisticsCompany);
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }

            /**
             * 今日统计降级：返回0值
             * <p>仪表盘展示用，服务不可用时返回0，不影响其他指标展示</p>
             */
            @Override
            public Result<AdminTodayStatsVO> getTodayStats() {
                log.warn("获取今日统计降级: 返回0值");
                AdminTodayStatsVO vo = new AdminTodayStatsVO();
                vo.setTodayOrderCount(0L);
                vo.setTodaySalesAmount(BigDecimal.ZERO);
                return Result.success(vo);
            }
        };
    }
}
