package com.shop.merchant.feign;

import com.shop.common.result.Result;
import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单服务Feign降级工厂（商家统计专用）
 * <p>
 * 当订单服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - 所有统计接口都是查询类，降级统一返回"统计服务暂不可用"
 * - 不抛异常（统计是辅助功能，不能因为统计服务挂了影响商家主流程）
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
        log.error("订单统计服务调用失败，触发降级", cause);
        return new OrderFeignClient() {

            /** 工作台统计降级：返回"统计服务暂不可用" */
            @Override
            public Result<MerchantDashboardStatsVO> getDashboardStats(Long merchantId) {
                log.warn("工作台统计降级: merchantId={}", merchantId);
                return Result.fail("统计服务暂不可用");
            }

            /** 销售趋势降级：返回"统计服务暂不可用" */
            @Override
            public Result<List<SalesTrendItemVO>> getSalesTrend(Long merchantId, int days) {
                log.warn("销售趋势降级: merchantId={}, days={}", merchantId, days);
                return Result.fail("统计服务暂不可用");
            }

            /** 数据中心概览降级：返回"统计服务暂不可用" */
            @Override
            public Result<MerchantDataOverviewVO> getDataOverview(Long merchantId, String startDate, String endDate) {
                log.warn("数据中心概览降级: merchantId={}, startDate={}, endDate={}", merchantId, startDate, endDate);
                return Result.fail("统计服务暂不可用");
            }

            /** 商品销量排行降级：返回"统计服务暂不可用" */
            @Override
            public Result<List<ProductRankItemVO>> getProductRank(Long merchantId, String startDate, String endDate, int limit) {
                log.warn("商品销量排行降级: merchantId={}, startDate={}, endDate={}, limit={}", merchantId, startDate, endDate, limit);
                return Result.fail("统计服务暂不可用");
            }
        };
    }
}
