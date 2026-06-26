package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品SKU Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力。
 * 额外提供了乐观锁扣减库存和回滚库存的方法，
 * 防止并发场景下超卖。
 * </p>
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    /**
     * 乐观锁扣减库存
     * <p>
     * 使用version字段实现乐观锁：只有当version值和传入的一致时才更新，
     * 更新成功后version自动+1。同时检查库存是否足够，防止超卖。
     * 两个人同时买最后一件商品，只有一个人能成功。
     * </p>
     *
     * @param id       SKU ID
     * @param quantity 扣减数量
     * @param version  当前版本号
     * @return 影响行数（1成功，0失败说明版本号变了或库存不够）
     */
    @Update("UPDATE product_sku SET stock = stock - #{quantity}, version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND version = #{version} AND stock >= #{quantity} AND deleted = 0")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("version") Integer version);

    /**
     * 回滚库存（取消订单时调用）
     * <p>
     * 订单取消后需要把扣掉的库存加回去。
     * 这里不需要乐观锁，因为回滚库存不会导致超卖问题。
     * </p>
     *
     * @param id       SKU ID
     * @param quantity 回滚数量
     * @return 影响行数
     */
    @Update("UPDATE product_sku SET stock = stock + #{quantity}, version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0")
    int addStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
