package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品SPU Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力。
 * 额外提供了上下架的更新方法。
 * </p>
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 更新商品状态（上架/下架）
     * <p>
     * 直接用SQL更新状态字段，比先查再改更高效。
     * </p>
     *
     * @param id     商品ID
     * @param status 状态：0下架 1上架
     * @return 影响行数
     */
    @Update("UPDATE product SET status = #{status}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 浏览量+1（用户查看商品详情时调用）
     * <p>
     * 用 SQL 自增避免并发问题，比"先查再加1再存"更安全高效。
     * </p>
     *
     * @param id 商品ID
     * @return 影响行数
     */
    @Update("UPDATE product SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrViewCount(@Param("id") Long id);

    /**
     * 销量累加（用户下单成功后调用，按购买数量累加）
     * <p>
     * 用 SQL 自增避免并发问题。
     * </p>
     *
     * @param id       商品ID
     * @param quantity 购买数量
     * @return 影响行数
     */
    @Update("UPDATE product SET sales = sales + #{quantity} WHERE id = #{id} AND deleted = 0")
    int incrSales(@Param("id") Long id, @Param("quantity") Integer quantity);
}
