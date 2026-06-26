package com.shop.admin.service.impl;

import com.shop.admin.feign.MerchantFeignClient;
import com.shop.admin.feign.OrderFeignClient;
import com.shop.admin.feign.UserFeignClient;
import com.shop.admin.service.DashboardService;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.admin.vo.DashboardVO;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.order.vo.AdminTodayStatsVO;
import com.shop.model.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 仪表盘服务实现类
 * <p>
 * 通过Feign调用各个微服务获取数据，聚合后返回给管理后台首页。
 * 如果某个服务调用失败，使用降级值（0），保证仪表盘不会因为某个服务挂了就完全不可用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 用户服务Feign客户端，用来获取用户数据 */
    private final UserFeignClient userFeignClient;

    /** 商家服务Feign客户端，用来获取商家数据 */
    private final MerchantFeignClient merchantFeignClient;

    /** 订单服务Feign客户端，用来获取订单数据 */
    private final OrderFeignClient orderFeignClient;

    /**
     * 获取仪表盘概览数据
     * <p>
     * 分别调用用户服务、商家服务、订单服务获取数据，
     * 如果某个服务调用失败，使用降级值0，不影响其他数据的展示。
     * </p>
     *
     * @return 仪表盘数据
     */
    @Override
    public DashboardVO getDashboardOverview() {
        DashboardVO vo = new DashboardVO();

        // 获取今日新增用户数：调用用户服务查询第一页，取total作为总数
        // 如果调用失败，降级为0
        try {
            Result<PageResult<UserVO>> userResult = userFeignClient.listUsers(1, 1, null, null);
            if (userResult != null && userResult.isSuccess() && userResult.getData() != null) {
                vo.setTodayNewUserCount(userResult.getData().getTotal());
            } else {
                vo.setTodayNewUserCount(0L);
            }
        } catch (Exception e) {
            log.warn("获取今日新增用户数失败，使用降级值0", e);
            vo.setTodayNewUserCount(0L);
        }

        // 获取在线商家数：调用商家服务查询状态为1（已通过）的商家，取total
        // 如果调用失败，降级为0
        try {
            Result<PageResult<MerchantVO>> merchantResult = merchantFeignClient.listMerchants(1, 1, 1, null);
            if (merchantResult != null && merchantResult.isSuccess() && merchantResult.getData() != null) {
                vo.setOnlineMerchantCount(merchantResult.getData().getTotal());
            } else {
                vo.setOnlineMerchantCount(0L);
            }
        } catch (Exception e) {
            log.warn("获取在线商家数失败，使用降级值0", e);
            vo.setOnlineMerchantCount(0L);
        }

        // 获取今日订单数和今日销售额：调用订单服务的统计接口
        // 如果调用失败，走 FallbackFactory 降级返回0值，不影响其他指标展示
        try {
            Result<AdminTodayStatsVO> orderStatsResult = orderFeignClient.getTodayStats();
            if (orderStatsResult != null && orderStatsResult.isSuccess() && orderStatsResult.getData() != null) {
                AdminTodayStatsVO stats = orderStatsResult.getData();
                vo.setTodayOrderCount(stats.getTodayOrderCount() != null ? stats.getTodayOrderCount() : 0L);
                vo.setTodaySalesAmount(stats.getTodaySalesAmount() != null ? stats.getTodaySalesAmount() : BigDecimal.ZERO);
            } else {
                vo.setTodayOrderCount(0L);
                vo.setTodaySalesAmount(BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.warn("获取今日订单统计失败，使用降级值0", e);
            vo.setTodayOrderCount(0L);
            vo.setTodaySalesAmount(BigDecimal.ZERO);
        }

        return vo;
    }
}
