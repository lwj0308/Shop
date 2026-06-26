package com.shop.order.service.impl;

import com.shop.model.order.vo.MerchantDashboardStatsVO;
import com.shop.model.order.vo.MerchantDataOverviewVO;
import com.shop.model.order.vo.ProductRankItemVO;
import com.shop.model.order.vo.SalesTrendItemVO;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.service.MerchantStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商家统计服务实现类
 * <p>
 * 实现商家仪表盘和数据中心所需的统计数据查询。
 * 主要是调用 Mapper 的聚合查询方法，把结果组装成 VO 返回给前端。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantStatsServiceImpl implements MerchantStatsService {

    /** 订单主表Mapper（用于查询订单统计数据） */
    private final OrderInfoMapper orderInfoMapper;

    /** 订单明细Mapper（用于查询商品销量排行） */
    private final OrderItemMapper orderItemMapper;

    /**
     * 获取商家仪表盘统计（今日销售额、今日订单数、待发货数）
     * <p>
     * 调用三个统计方法，把结果组装成一个 VO 返回。
     * 这三个数据是商家打开工作台第一眼看到的。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 仪表盘统计数据
     */
    @Override
    public MerchantDashboardStatsVO getDashboardStats(Long merchantId) {
        log.info("查询商家仪表盘统计: merchantId={}", merchantId);

        MerchantDashboardStatsVO vo = new MerchantDashboardStatsVO();

        // 第1步：查询今日销售额（只统计已付款订单）
        BigDecimal todaySales = orderInfoMapper.sumTodaySales(merchantId);
        vo.setTodaySales(todaySales != null ? todaySales : BigDecimal.ZERO);

        // 第2步：查询今日订单数
        Long todayOrders = orderInfoMapper.countTodayOrders(merchantId);
        vo.setTodayOrders(todayOrders != null ? todayOrders : 0L);

        // 第3步：查询待发货订单数
        Long pendingShip = orderInfoMapper.countPendingShip(merchantId);
        vo.setPendingShip(pendingShip != null ? pendingShip : 0L);

        return vo;
    }

    /**
     * 获取商家销售趋势（最近N天每日销售额）
     * <p>
     * 计算出 N 天前的日期作为起点，查询每日销售额，
     * 然后把数据库返回的 Map 列表转换成 VO 列表。
     * 注意：数据库返回的 date 可能是 java.sql.Date，需要转成 String。
     * </p>
     *
     * @param merchantId 商家ID
     * @param days       最近几天
     * @return 每日销售额列表
     */
    @Override
    public List<SalesTrendItemVO> getSalesTrend(Long merchantId, int days) {
        log.info("查询商家销售趋势: merchantId={}, days={}", merchantId, days);

        // 第1步：计算起始日期（今天往前推 days 天）
        LocalDateTime startDate = LocalDate.now().minusDays(days).atStartOfDay();

        // 第2步：查询数据库，返回的是 List<Map>，每个 Map 包含 date 和 amount
        List<Map<String, Object>> list = orderInfoMapper.selectSalesTrend(merchantId, startDate);

        // 第3步：把 Map 转换成 VO
        List<SalesTrendItemVO> result = new ArrayList<>();
        for (Map<String, Object> row : list) {
            SalesTrendItemVO item = new SalesTrendItemVO();
            // 数据库返回的 date 可能是 java.sql.Date，统一转成字符串
            Object dateObj = row.get("date");
            item.setDate(dateObj != null ? dateObj.toString() : "");
            // amount 可能是 BigDecimal 或其他类型，统一转成 BigDecimal
            Object amountObj = row.get("amount");
            item.setAmount(toBigDecimal(amountObj));
            result.add(item);
        }

        return result;
    }

    /**
     * 获取商家数据中心概览（指定时间范围内的统计）
     * <p>
     * 查询总销售额、总订单数、退款订单数，然后计算客单价和退款率。
     * 客单价 = 总销售额 / 总订单数
     * 退款率 = 退款订单数 / 总订单数 * 100
     * 注意要处理除零情况，避免抛异常。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @return 数据中心概览
     */
    @Override
    public MerchantDataOverviewVO getDataOverview(Long merchantId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("查询商家数据中心概览: merchantId={}, startDate={}, endDate={}", merchantId, startDate, endDate);

        MerchantDataOverviewVO vo = new MerchantDataOverviewVO();

        // 第1步：查询总销售额（已付款订单）
        BigDecimal totalSales = orderInfoMapper.sumSalesByRange(merchantId, startDate, endDate);
        vo.setTotalSales(totalSales != null ? totalSales : BigDecimal.ZERO);

        // 第2步：查询总订单数
        Long totalOrders = orderInfoMapper.countOrdersByRange(merchantId, startDate, endDate);
        long orders = totalOrders != null ? totalOrders : 0L;
        vo.setTotalOrders(orders);

        // 第3步：计算客单价 = 总销售额 / 总订单数（注意除零）
        if (orders > 0) {
            vo.setAvgOrderAmount(vo.getTotalSales().divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP));
        } else {
            vo.setAvgOrderAmount(BigDecimal.ZERO);
        }

        // 第4步：查询退款订单数并计算退款率
        Long refundOrders = orderInfoMapper.countRefundOrdersByRange(merchantId, startDate, endDate);
        long refunds = refundOrders != null ? refundOrders : 0L;
        if (orders > 0) {
            // 退款率 = 退款订单数 / 总订单数 * 100，保留2位小数
            vo.setRefundRate(BigDecimal.valueOf(refunds)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP));
        } else {
            vo.setRefundRate(BigDecimal.ZERO);
        }

        return vo;
    }

    /**
     * 获取商家商品销量排行
     * <p>直接调用 OrderItemMapper 的查询方法，返回销量Top商品</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @param limit      取前几名
     * @return 商品销量排行列表
     */
    @Override
    public List<ProductRankItemVO> getProductRank(Long merchantId, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        log.info("查询商家商品销量排行: merchantId={}, startDate={}, endDate={}, limit={}", merchantId, startDate, endDate, limit);
        return orderItemMapper.selectProductRank(merchantId, startDate, endDate, limit);
    }

    // ==================== 私有方法 ====================

    /**
     * 把任意对象转成 BigDecimal
     * <p>
     * 数据库聚合查询返回的金额可能是 BigDecimal、Long、Integer 等类型，
     * 这里统一转成 BigDecimal，方便处理。
     * </p>
     *
     * @param obj 原始对象
     * @return BigDecimal，为空时返回0
     */
    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) {
            return BigDecimal.ZERO;
        }
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        return new BigDecimal(obj.toString());
    }
}
