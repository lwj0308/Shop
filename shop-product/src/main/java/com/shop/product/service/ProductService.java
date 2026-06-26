package com.shop.product.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.dto.StockDeductItemDTO;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.product.vo.ProductVO;

import java.util.List;
import java.util.Map;

/**
 * 商品服务接口
 * <p>
 * 定义商品相关的业务方法，包括发布、编辑、上下架、详情、列表、库存操作等。
 * 实现类在 ProductServiceImpl 中，具体逻辑去看那里。
 * </p>
 */
public interface ProductService {

    /**
     * 发布商品
     * <p>
     * 同时创建SPU+规格+SKU，需要事务保证数据一致性。
     * </p>
     *
     * @param shopId 店铺ID
     * @param dto    商品发布参数
     * @return 商品ID
     */
    Long createProduct(Long shopId, ProductCreateDTO dto);

    /**
     * 编辑商品
     * <p>
     * 商家只能编辑自己店铺的商品，传入 shopId 做归属校验。
     * </p>
     *
     * @param productId 商品ID
     * @param dto       商品编辑参数
     * @param shopId    当前商家店铺ID（用于归属校验）
     */
    void updateProduct(Long productId, ProductUpdateDTO dto, Long shopId);

    /**
     * 上架商品
     * <p>
     * 商家只能上架自己店铺的商品，传入 shopId 做归属校验。
     * </p>
     *
     * @param productId 商品ID
     * @param shopId    当前商家店铺ID（用于归属校验）
     */
    void onShelf(Long productId, Long shopId);

    /**
     * 下架商品
     * <p>
     * 商家只能下架自己店铺的商品，传入 shopId 做归属校验。
     * </p>
     *
     * @param productId 商品ID
     * @param shopId    当前商家店铺ID（用于归属校验）
     */
    void offShelf(Long productId, Long shopId);

    /**
     * 商品详情（含SKU、规格、评价摘要）
     *
     * @param productId 商品ID
     * @return 商品详情
     */
    ProductDetailVO getProductDetail(Long productId);

    /**
     * 商品列表（分页）
     *
     * @param categoryId 分类ID（可选）
     * @param pageRequest 分页参数
     * @return 分页商品列表
     */
    PageResult<ProductVO> getProductList(Long categoryId, PageRequest pageRequest);

    /**
     * 扣减库存（乐观锁，供订单服务Feign调用）
     * <p>
     * 使用乐观锁防止超卖：两个人同时买最后一件商品，只有一个人能成功。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @return 是否成功
     */
    boolean deductStock(Long skuId, Integer quantity);

    /**
     * 回滚库存（取消订单时调用）
     *
     * @param skuId    SKU ID
     * @param quantity 回滚数量
     */
    void addStock(Long skuId, Integer quantity);

    /**
     * 扣减库存（带幂等，供订单服务Feign调用）
     * <p>
     * 使用Redis分布式锁 + Lua脚本保证并发安全。
     * 同一个订单号不会重复扣减，保证幂等性。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @param orderNo  订单号（用于幂等去重）
     * @return 是否成功
     */
    boolean deductStockWithIdempotent(Long skuId, Integer quantity, String orderNo);

    /**
     * 回滚库存（带幂等，取消订单时调用）
     * <p>
     * 同一个订单号不会重复回滚，保证幂等性。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 回滚数量
     * @param orderNo  订单号（用于幂等去重）
     */
    void addStockWithIdempotent(Long skuId, Integer quantity, String orderNo);

    /**
     * 根据SKU ID获取SKU信息（供Feign调用）
     *
     * @param skuId SKU ID
     * @return SKU信息
     */
    ProductSkuVO getSkuById(Long skuId);

    /**
     * 热销推荐（按销量降序）
     * <p>
     * 查询销量最高的商品，用于首页"热销推荐"区域。
     * </p>
     *
     * @param limit 返回数量
     * @return 热销商品列表
     */
    List<ProductVO> getHotProducts(int limit);

    /**
     * 新品推荐（按创建时间降序）
     * <p>
     * 查询最新上架的商品，用于首页"新品推荐"区域。
     * </p>
     *
     * @param limit 返回数量
     * @return 新品商品列表
     */
    List<ProductVO> getNewProducts(int limit);

    /**
     * 相关推荐（同分类排除当前商品，按销量降序）
     * <p>
     * 查询与当前商品同分类的其他商品，用于商品详情页"看了又看"区域。
     * </p>
     *
     * @param productId 当前商品ID
     * @param limit     返回数量
     * @return 相关商品列表
     */
    List<ProductVO> getRelatedProducts(Long productId, int limit);

    /**
     * 猜你喜欢（基于足迹分类查热销，无足迹降级为全站热销）
     * <p>
     * 先查用户浏览过的商品分类，在这些分类下查热销商品。
     * 如果用户没有足迹（未登录或没浏览记录），降级为全站热销推荐。
     * </p>
     *
     * @param userId 用户ID
     * @param limit  返回数量
     * @return 猜你喜欢商品列表
     */
    List<ProductVO> getGuessProducts(Long userId, int limit);

    /**
     * 记录用户浏览（累加浏览量 + 记录足迹）
     * <p>
     * 用户查看商品详情时调用：
     * - 累加商品的浏览量（view_count + 1）
     * - 如果用户已登录，通过 Feign 调用 shop-user 记录浏览足迹（弱依赖，失败不影响主流程）
     * </p>
     *
     * @param productId 商品ID
     * @param userId    用户ID（未登录传 null，不记录足迹）
     */
    void recordView(Long productId, Long userId);

    /**
     * 销量累加（下单成功后调用，供 Feign 调用）
     *
     * @param productId 商品ID
     * @param quantity  购买数量
     */
    void incrSales(Long productId, Integer quantity);

    /**
     * 批量获取SKU信息（供 Feign 调用）
     * <p>
     * 一次查询多个 SKU，避免循环 N 次远程调用（N+1 问题）。
     * 小白理解：原来买 3 件商品要查 3 次数据库，现在用 IN 查询一次查出来。
     * </p>
     *
     * @param skuIds SKU ID列表
     * @return SKU 信息列表
     */
    List<ProductSkuVO> batchGetSkuByIds(List<Long> skuIds);

    /**
     * 批量扣减库存（带补偿回退，供 Feign 调用）
     * <p>
     * 一次处理多个 SKU 的扣减，任一失败则回滚已扣减的。
     * 补偿回退逻辑在 shop-product 内部完成，shop-order 只需调用一次。
     * </p>
     *
     * @param items   扣减项列表（SKU ID + 数量）
     * @param orderNo 订单号（用于幂等去重）
     * @return 是否全部扣减成功
     */
    boolean batchDeductStock(List<StockDeductItemDTO> items, String orderNo);

    /**
     * 批量销量累加（下单成功后调用，供 Feign 调用）
     * <p>
     * 一次累加多个商品的销量，避免循环 N 次远程调用。
     * </p>
     *
     * @param productQuantities key=商品ID， value=累加数量
     */
    void incrSalesBatch(Map<Long, Integer> productQuantities);
}
