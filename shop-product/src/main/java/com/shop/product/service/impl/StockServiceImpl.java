package com.shop.product.service.impl;

import com.shop.product.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 库存服务实现类
 * <p>
 * 使用 Redis + Lua脚本 实现安全的库存扣减，核心保证两点：
 * 1. 不超卖：Lua脚本在Redis中原子执行，检查库存和扣减库存不会被其他命令打断
 * 2. 幂等性：同一个订单号不会重复扣减，通过Redis Set记录已处理的订单号
 * </p>
 * <p>
 * Lua脚本比数据库乐观锁好在哪？
 * - 数据库乐观锁：每次扣库存都要查数据库获取version，高并发下数据库压力大
 * - Redis Lua脚本：在内存中操作，速度极快，数据库压力小
 * - 而且Lua脚本在Redis中是原子执行的，天然不会超卖
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    /** Redis模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 库存key前缀：stock:sku:{skuId} 存储SKU的可用库存 */
    private static final String STOCK_KEY_PREFIX = "stock:sku:";

    /** 库存扣减记录key前缀：stock:deduct:{skuId} 是一个Set，存储已扣减的订单号 */
    private static final String DEDUCT_RECORD_KEY_PREFIX = "stock:deduct:";

    /** 库存回滚记录key前缀：stock:rollback:{skuId} 是一个Set，存储已回滚的订单号 */
    private static final String ROLLBACK_RECORD_KEY_PREFIX = "stock:rollback:";

    /**
     * 扣减库存的Lua脚本
     * <p>
     * 脚本逻辑（一步步来，很好理解）：
     * 1. 检查这个订单号是不是已经扣减过了（幂等校验）
     * 2. 检查库存够不够
     * 3. 扣减库存
     * 4. 记录这个订单号已经扣减过了
     * 5. 返回1表示成功，0表示失败
     * </p>
     * <p>
     * KEYS[1] = 库存key（stock:sku:{skuId}）
     * KEYS[2] = 扣减记录key（stock:deduct:{skuId}）
     * ARGV[1] = 扣减数量
     * ARGV[2] = 订单号
     * </p>
     */
    private static final String DEDUCT_STOCK_SCRIPT =
            "-- 检查订单号是否已处理过（幂等校验）\n" +
            "if redis.call('SISMEMBER', KEYS[2], ARGV[2]) == 1 then\n" +
            "    return 0  -- 已经扣减过了，直接返回失败\n" +
            "end\n" +
            "-- 获取当前库存\n" +
            "local stock = tonumber(redis.call('GET', KEYS[1]))\n" +
            "-- 库存不存在或不足\n" +
            "if stock == nil or stock < tonumber(ARGV[1]) then\n" +
            "    return 0  -- 库存不足\n" +
            "end\n" +
            "-- 扣减库存\n" +
            "redis.call('DECRBY', KEYS[1], ARGV[1])\n" +
            "-- 记录订单号，防止重复扣减\n" +
            "redis.call('SADD', KEYS[2], ARGV[2])\n" +
            "return 1  -- 扣减成功";

    /**
     * 回滚库存的Lua脚本
     * <p>
     * 脚本逻辑：
     * 1. 检查这个订单号是不是已经回滚过了（幂等校验）
     * 2. 检查这个订单号之前是否扣减过（只有扣减过的才能回滚）
     * 3. 加回库存
     * 4. 从扣减记录中移除订单号（允许重新扣减）
     * 5. 记录回滚记录
     * 6. 返回1表示成功，0表示失败
     * </p>
     * <p>
     * KEYS[1] = 库存key（stock:sku:{skuId}）
     * KEYS[2] = 扣减记录key（stock:deduct:{skuId}）
     * KEYS[3] = 回滚记录key（stock:rollback:{skuId}）
     * ARGV[1] = 回滚数量
     * ARGV[2] = 订单号
     * </p>
     */
    private static final String ADD_STOCK_SCRIPT =
            "-- 检查是否已经回滚过（幂等校验）\n" +
            "if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then\n" +
            "    return 0  -- 已经回滚过了\n" +
            "end\n" +
            "-- 检查是否之前扣减过（只有扣减过的才能回滚）\n" +
            "if redis.call('SISMEMBER', KEYS[2], ARGV[2]) == 0 then\n" +
            "    return 0  -- 没有扣减记录，不能回滚\n" +
            "end\n" +
            "-- 加回库存\n" +
            "redis.call('INCRBY', KEYS[1], ARGV[1])\n" +
            "-- 从扣减记录中移除（允许重新扣减）\n" +
            "redis.call('SREM', KEYS[2], ARGV[2])\n" +
            "-- 记录回滚记录\n" +
            "redis.call('SADD', KEYS[3], ARGV[2])\n" +
            "return 1  -- 回滚成功";

    /**
     * 扣减库存（Redis Lua脚本，原子操作）
     * <p>
     * 在Redis中原子性地完成：检查库存 → 扣减库存 → 记录订单号（幂等）
     * Lua脚本在Redis中是原子执行的，不会被其他命令打断，所以不会超卖。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @param orderNo  订单号（用于幂等去重）
     * @return true扣减成功，false库存不足或已扣减过
     */
    @Override
    public boolean deductStock(Long skuId, Integer quantity, String orderNo) {
        String stockKey = STOCK_KEY_PREFIX + skuId;
        String deductRecordKey = DEDUCT_RECORD_KEY_PREFIX + skuId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEDUCT_STOCK_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(
                script,
                java.util.List.of(stockKey, deductRecordKey),
                String.valueOf(quantity),
                orderNo
        );

        if (result != null && result == 1) {
            log.info("Redis扣减库存成功: skuId={}, quantity={}, orderNo={}", skuId, quantity, orderNo);
            return true;
        }

        log.warn("Redis扣减库存失败（库存不足或重复扣减）: skuId={}, quantity={}, orderNo={}", skuId, quantity, orderNo);
        return false;
    }

    /**
     * 回滚库存（取消订单时调用）
     * <p>
     * 把之前扣的库存加回去，同时从扣减记录中移除订单号，
     * 这样同一个订单号可以再次扣减（比如取消后重新下单）。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 回滚数量
     * @param orderNo  订单号
     */
    @Override
    public void addStock(Long skuId, Integer quantity, String orderNo) {
        String stockKey = STOCK_KEY_PREFIX + skuId;
        String deductRecordKey = DEDUCT_RECORD_KEY_PREFIX + skuId;
        String rollbackRecordKey = ROLLBACK_RECORD_KEY_PREFIX + skuId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ADD_STOCK_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(
                script,
                java.util.List.of(stockKey, deductRecordKey, rollbackRecordKey),
                String.valueOf(quantity),
                orderNo
        );

        if (result != null && result == 1) {
            log.info("Redis回滚库存成功: skuId={}, quantity={}, orderNo={}", skuId, quantity, orderNo);
        } else {
            log.warn("Redis回滚库存失败（无扣减记录或已回滚过）: skuId={}, quantity={}, orderNo={}", skuId, quantity, orderNo);
        }
    }

    /**
     * 初始化SKU库存到Redis
     * <p>
     * 服务启动时或商品发布时调用，把数据库中的库存数量同步到Redis。
     * 如果Redis中已有库存，不会覆盖（防止覆盖正在使用的库存数据）。
     * </p>
     *
     * @param skuId SKU ID
     * @param stock 库存数量
     */
    @Override
    public void initStock(Long skuId, Integer stock) {
        String stockKey = STOCK_KEY_PREFIX + skuId;
        // 只在key不存在时设置，防止覆盖正在使用的库存
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(stock));
        if (Boolean.TRUE.equals(success)) {
            log.info("初始化SKU库存到Redis: skuId={}, stock={}", skuId, stock);
        } else {
            log.debug("SKU库存已存在于Redis，跳过初始化: skuId={}", skuId);
        }
    }
}
