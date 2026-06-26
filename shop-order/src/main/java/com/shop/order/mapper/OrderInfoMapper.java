package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.order.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单主表Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作order_info表。
 * 这里还额外定义了一些聚合统计查询，用于商家仪表盘和数据中心。
 * </p>
 */
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 统计商家今日销售额（只统计已付款订单 status >= 2）
     * <p>就是算一下这个商家今天总共卖了多少钱</p>
     *
     * @param merchantId 商家ID
     * @return 今日销售额（分），没有数据时返回0
     */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM order_info WHERE merchant_id = #{merchantId} AND status >= 2 AND DATE(create_time) = CURDATE() AND deleted = 0")
    BigDecimal sumTodaySales(@Param("merchantId") Long merchantId);

    /**
     * 统计商家今日订单数
     * <p>算一下这个商家今天接了多少单（包含所有状态）</p>
     *
     * @param merchantId 商家ID
     * @return 今日订单数
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE merchant_id = #{merchantId} AND DATE(create_time) = CURDATE() AND deleted = 0")
    Long countTodayOrders(@Param("merchantId") Long merchantId);

    /**
     * 统计商家待发货订单数（status = 2）
     * <p>算一下这个商家有多少订单等着发货</p>
     *
     * @param merchantId 商家ID
     * @return 待发货订单数
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE merchant_id = #{merchantId} AND status = 2 AND deleted = 0")
    Long countPendingShip(@Param("merchantId") Long merchantId);

    /**
     * 统计商家指定时间范围内的销售额（已付款订单）
     * <p>用于数据中心查看某段时间卖了多少钱</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @return 销售额（分），没有数据时返回0
     */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM order_info WHERE merchant_id = #{merchantId} AND status >= 2 AND create_time >= #{startDate} AND create_time < #{endDate} AND deleted = 0")
    BigDecimal sumSalesByRange(@Param("merchantId") Long merchantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * 统计商家指定时间范围内的订单数
     * <p>用于数据中心查看某段时间接了多少单</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @return 订单数
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE merchant_id = #{merchantId} AND create_time >= #{startDate} AND create_time < #{endDate} AND deleted = 0")
    Long countOrdersByRange(@Param("merchantId") Long merchantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * 统计商家指定时间范围内的退款订单数（status in 6,7）
     * <p>退款中的订单和已退款的订单都算，用于计算退款率</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @return 退款订单数
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE merchant_id = #{merchantId} AND status IN (6, 7) AND create_time >= #{startDate} AND create_time < #{endDate} AND deleted = 0")
    Long countRefundOrdersByRange(@Param("merchantId") Long merchantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * 查询商家最近N天的销售趋势（按日期分组）
     * <p>返回 List<Map>，每个 Map 包含 date 和 amount，用于画折线图</p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间（一般是 N 天前）
     * @return 每日销售额列表
     */
    @Select("SELECT DATE(create_time) as date, COALESCE(SUM(pay_amount), 0) as amount FROM order_info WHERE merchant_id = #{merchantId} AND status >= 2 AND create_time >= #{startDate} AND deleted = 0 GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> selectSalesTrend(@Param("merchantId") Long merchantId, @Param("startDate") LocalDateTime startDate);

    /**
     * 统计全平台今日销售额（只统计已付款订单 status >= 2）
     * <p>给管理后台仪表盘用，算一下全平台今天总共卖了多少钱</p>
     *
     * @return 今日销售额（分），没有数据时返回0
     */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM order_info WHERE status >= 2 AND DATE(create_time) = CURDATE() AND deleted = 0")
    BigDecimal sumTodaySalesAll();

    /**
     * 统计全平台今日订单数
     * <p>给管理后台仪表盘用，算一下全平台今天接了多少单</p>
     *
     * @return 今日订单数
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE DATE(create_time) = CURDATE() AND deleted = 0")
    Long countTodayOrdersAll();
}
