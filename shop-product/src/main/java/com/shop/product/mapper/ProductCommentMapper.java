package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.model.product.entity.ProductComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品评价Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作product_comment表。
 * 这里额外定义了按店铺查询评价的方法，给商家端评价管理用。
 * </p>
 */
@Mapper
public interface ProductCommentMapper extends BaseMapper<ProductComment> {

    /**
     * 按店铺ID分页查询评价（JOIN product 表获取商品名称）
     * <p>
     * 商家在评价管理页面看到的所有评价，都是针对自己店铺商品的。
     * 通过 JOIN product 表，用 product.shop_id 过滤，同时拿到商品名称。
     * 用 &lt;script&gt; 标签支持动态SQL：hasReply为null查全部，true只看已回复，false只看未回复。
     * </p>
     *
     * @param page     分页对象（MyBatis-Plus会自动处理）
     * @param shopId   店铺ID
     * @param hasReply 是否已回复：true只看已回复，false只看未回复，null看全部
     * @return 评价分页列表（每条包含商品名称）
     */
    @Select("<script>" +
            "SELECT c.*, p.name as product_name FROM product_comment c " +
            "LEFT JOIN product p ON c.product_id = p.id " +
            "WHERE p.shop_id = #{shopId} AND c.deleted = 0 " +
            "<if test='hasReply != null and hasReply == true'>" +
            " AND c.reply IS NOT NULL AND c.reply != '' " +
            "</if>" +
            "<if test='hasReply != null and hasReply == false'>" +
            " AND (c.reply IS NULL OR c.reply = '') " +
            "</if>" +
            " ORDER BY c.create_time DESC" +
            "</script>")
    IPage<ProductComment> selectCommentsByShopId(IPage<ProductComment> page,
                                                   @Param("shopId") Long shopId,
                                                   @Param("hasReply") Boolean hasReply);

    /**
     * 根据评价ID查询评价（含商品名称）
     * <p>商家回复评价前，先查出来校验这条评价是否属于自己店铺的商品</p>
     *
     * @param commentId 评价ID
     * @return 评价信息（含商品名称）
     */
    @Select("SELECT c.*, p.name as product_name, p.shop_id as shop_id FROM product_comment c " +
            "LEFT JOIN product p ON c.product_id = p.id " +
            "WHERE c.id = #{commentId} AND c.deleted = 0")
    ProductComment selectCommentWithShopById(@Param("commentId") Long commentId);
}
