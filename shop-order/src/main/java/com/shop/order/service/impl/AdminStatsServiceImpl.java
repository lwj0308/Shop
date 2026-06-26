package com.shop.order.service.impl;

import com.shop.model.order.vo.AdminTodayStatsVO;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 管理端统计服务实现
 * <p>
 * 查询全平台订单数据，聚合后返回给管理后台仪表盘。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    /** 订单Mapper，用来查询订单数据 */
    private final OrderInfoMapper orderInfoMapper;

    /**
     * 获取全平台今日统计
     * <p>分别查询今日订单数和今日销售额，组装成VO返回</p>
     *
     * @return 今日统计数据
     */
    @Override
    public AdminTodayStatsVO getTodayStats() {
        AdminTodayStatsVO vo = new AdminTodayStatsVO();
        // 查询今日订单数
        Long todayOrderCount = orderInfoMapper.countTodayOrdersAll();
        vo.setTodayOrderCount(todayOrderCount != null ? todayOrderCount : 0L);

        // 查询今日销售额
        BigDecimal todaySales = orderInfoMapper.sumTodaySalesAll();
        vo.setTodaySalesAmount(todaySales != null ? todaySales : BigDecimal.ZERO);

        log.info("管理端今日统计: orderCount={}, salesAmount={}", vo.getTodayOrderCount(), vo.getTodaySalesAmount());
        return vo;
    }
}
