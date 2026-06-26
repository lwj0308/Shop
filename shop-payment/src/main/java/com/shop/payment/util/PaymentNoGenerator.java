package com.shop.payment.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 支付单号生成器
 * <p>
 * 使用雪花算法生成唯一的支付单号。
 * 雪花算法的优点：生成的ID是趋势递增的，而且不依赖数据库，
 * 在分布式环境下也不会重复。
 * </p>
 * <p>
 * 生成的支付单号格式：PAY + 雪花ID，比如 PAY1234567890123456789
 * 加上PAY前缀是为了方便一眼看出这是支付单号，不是订单号或其他编号。
 * </p>
 */
public class PaymentNoGenerator {

    /** 支付单号前缀，方便识别 */
    private static final String PREFIX = "PAY";

    /** 雪花算法实例（workerId=1, datacenterId=1，单机部署够用了） */
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    /**
     * 生成支付单号
     * <p>
     * 每次调用都会生成一个唯一的支付单号，格式为 PAY + 雪花ID。
     * 雪花算法保证在分布式环境下也不会重复，可以放心使用。
     * </p>
     *
     * @return 唯一的支付单号，比如 PAY1234567890123456789
     */
    public static String generate() {
        return PREFIX + SNOWFLAKE.nextIdStr();
    }
}
