package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.vo.ProductRankItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单明细Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作order_item表。
 * 这里还额外定义了商品销量排行查询，用于商家数据中心。
 * </p>
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 查询商家指定时间范围内的商品销量排行
     * <p>
     * 关联 order_info 表获取 merchant_id，按商品分组统计销量和销售额，
     * 最后按销量从高到低排序，取前 N 名。
     * </p>
     *
     * @param merchantId 商家ID
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @param limit      取前几名
     * @return 商品销量排行列表
     */
    @Select("SELECT oi.product_name as productName, SUM(oi.quantity) as salesCount, SUM(oi.subtotal) as salesAmount " +
            "FROM order_item oi " +
            "INNER JOIN order_info o ON oi.order_id = o.id " +
            "WHERE o.merchant_id = #{merchantId} AND o.status >= 2 AND o.create_time >= #{startDate} AND o.create_time < #{endDate} AND o.deleted = 0 " +
            "GROUP BY oi.product_id, oi.product_name " +
            "ORDER BY salesCount DESC " +
            "LIMIT #{limit}")
    List<ProductRankItemVO> selectProductRank(@Param("merchantId") Long merchantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("limit") int limit);
}
