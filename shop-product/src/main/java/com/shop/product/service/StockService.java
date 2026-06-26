package com.shop.product.service;

/**
 * 库存服务
 * <p>
 * 封装库存扣减和回滚的核心逻辑，使用 Redis + Lua脚本 保证并发安全。
 * </p>
 * <p>
 * 为什么不用数据库乐观锁，而要用Redis Lua脚本？
 * - 数据库乐观锁：每次扣库存都要查一次数据库获取version，高并发下性能差
 * - Redis Lua脚本：在Redis中原子性执行，不需要查数据库，性能极高
 * - Lua脚本在Redis中是原子执行的，不会被其他命令打断，所以不会超卖
 * </p>
 * <p>
 * 库存扣减流程：
 * 1. 先在Redis中检查并扣减库存（Lua脚本原子操作）
 * 2. Redis扣减成功后，异步更新数据库库存
 * 3. 如果Redis扣减失败（库存不足），直接返回失败
 * </p>
 * <p>
 * 幂等性保证：
 * - 每次扣减/回滚都带上订单号
 * - Redis中记录已处理的订单号，同一个订单号不会重复操作
 * </p>
 */
public interface StockService {

    /**
     * 扣减库存（Redis Lua脚本，原子操作）
     * <p>
     * 在Redis中原子性地完成：检查库存 → 扣减库存 → 记录订单号（幂等）
     * 整个过程在Lua脚本中执行，不会被其他命令打断，保证不超卖。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @param orderNo  订单号（用于幂等去重，同一个订单号不会重复扣减）
     * @return true扣减成功，false库存不足或已扣减过
     */
    boolean deductStock(Long skuId, Integer quantity, String orderNo);

    /**
     * 回滚库存（取消订单时调用）
     * <p>
     * 把之前扣的库存加回去，同时删除幂等记录，
     * 这样同一个订单号可以再次扣减（比如取消后重新下单）。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 回滚数量
     * @param orderNo  订单号（用于幂等去重）
     */
    void addStock(Long skuId, Integer quantity, String orderNo);

    /**
     * 初始化SKU库存到Redis
     * <p>
     * 服务启动时或商品发布时调用，把数据库中的库存数量同步到Redis。
     * Redis中的库存是扣减的依据，必须和数据库保持一致。
     * </p>
     *
     * @param skuId SKU ID
     * @param stock 库存数量
     */
    void initStock(Long skuId, Integer stock);
}
