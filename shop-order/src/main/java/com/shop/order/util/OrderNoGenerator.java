package com.shop.order.util;

import com.shop.order.config.SnowflakeIdConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 订单号生成工具类
 * <p>
 * 使用雪花算法生成唯一的订单号。订单号是给用户看的，不是数据库的主键。
 * 订单号的特点：
 * 1. 全局唯一：不会有两个订单的订单号一样
 * 2. 趋势递增：大致按时间递增，方便排序
 * 3. 不连续：不能通过订单号推算出订单数量，保护商业机密
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    /** 雪花算法ID生成器，由SnowflakeIdConfig配置类创建 */
    private final SnowflakeIdConfig.SnowflakeIdWorker snowflakeIdWorker;

    /**
     * 生成订单号
     * <p>
     * 直接用雪花算法生成一个Long型数字，转成字符串就是订单号。
     * 格式示例：1829384756102345678
     * </p>
     *
     * @return 唯一的订单号字符串
     */
    public String generate() {
        return String.valueOf(snowflakeIdWorker.nextId());
    }

    /**
     * 生成退款单号
     * <p>
     * 退款单号和订单号用同一个雪花算法生成器，因为雪花算法本身就能保证唯一性。
     * 如果想区分订单号和退款单号，可以在前面加前缀，比如"RF"开头表示退款。
     * </p>
     *
     * @return 唯一的退款单号字符串
     */
    public String generateRefundNo() {
        return "RF" + snowflakeIdWorker.nextId();
    }
}
