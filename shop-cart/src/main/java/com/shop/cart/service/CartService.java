package com.shop.cart.service;

import com.shop.model.cart.dto.CartAddDTO;
import com.shop.model.cart.dto.CartCheckDTO;
import com.shop.model.cart.dto.CartUpdateDTO;
import com.shop.model.cart.vo.CartVO;

import java.util.List;

/**
 * 购物车服务接口
 * <p>
 * 定义购物车相关的业务方法，包括加购、修改、删除、勾选、清空等操作。
 * </p>
 */
public interface CartService {

    /**
     * 加入购物车
     * <p>
     * 如果同一个SKU已经在购物车里了，就把数量累加；
     * 如果是新SKU，就新增一条记录。
     * 每个用户购物车最多100个不同SKU。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    加入购物车参数（商品ID、SKU ID、数量）
     */
    void addToCart(Long userId, CartAddDTO dto);

    /**
     * 修改购物车项
     * <p>
     * 可以修改数量或勾选状态，传哪个就改哪个。
     * </p>
     *
     * @param userId     用户ID（确保只能改自己的购物车）
     * @param cartItemId 购物车项ID
     * @param dto        修改参数（数量、勾选状态）
     */
    void updateCartItem(Long userId, Long cartItemId, CartUpdateDTO dto);

    /**
     * 删除购物车项
     * <p>
     * 从购物车中移除某个商品。
     * </p>
     *
     * @param userId     用户ID（确保只能删自己的购物车）
     * @param cartItemId 购物车项ID
     */
    void deleteCartItem(Long userId, Long cartItemId);

    /**
     * 批量勾选/取消勾选
     * <p>
     * 用户勾选多个商品准备结算，或者取消勾选。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    勾选参数（购物车项ID列表、勾选状态）
     */
    void batchCheck(Long userId, CartCheckDTO dto);

    /**
     * 全选/取消全选
     * <p>
     * 一键勾选或取消勾选购物车里的所有商品。
     * </p>
     *
     * @param userId  用户ID
     * @param checked 是否全选（true全选，false取消全选）
     */
    void checkAll(Long userId, Boolean checked);

    /**
     * 获取购物车列表
     * <p>
     * 返回购物车中所有商品的详细信息，包括实时价格和库存状态。
     * 需要调用商品服务获取最新的商品信息。
     * </p>
     *
     * @param userId 用户ID
     * @return 购物车响应（含商品列表、总数量、选中总价、是否全选）
     */
    CartVO getCartList(Long userId);

    /**
     * 清空购物车
     * <p>
     * 删除用户购物车中的所有商品。
     * </p>
     *
     * @param userId 用户ID
     */
    void clearCart(Long userId);

    /**
     * 获取购物车商品数量
     * <p>
     * 用于Header角标显示，比如购物车图标上显示"3"表示有3件商品。
     * </p>
     *
     * @param userId 用户ID
     * @return 购物车商品总数量
     */
    Integer getCartCount(Long userId);

    /**
     * 根据SKU ID批量删除购物车项
     * <p>
     * 用户下单成功后调用，把已购买的商品从购物车中移除。
     * 传的是SKU ID列表而不是购物车项ID，因为订单里只有SKU ID。
     * </p>
     *
     * @param userId 用户ID
     * @param skuIds SKU ID列表
     */
    void deleteBySkuIds(Long userId, List<Long> skuIds);

    /**
     * 合并购物车
     * <p>
     * 用户未登录时在浏览器本地存了购物车数据，登录后需要把这些数据
     * 合并到服务端的购物车中。同一SKU数量累加，不会重复。
     * 合并时会逐项校验商品状态和库存，和加购逻辑一致。
     * </p>
     *
     * @param userId 用户ID
     * @param items  未登录时的购物车项列表（商品ID、SKU ID、数量）
     */
    void mergeCart(Long userId, List<CartAddDTO> items);
}
