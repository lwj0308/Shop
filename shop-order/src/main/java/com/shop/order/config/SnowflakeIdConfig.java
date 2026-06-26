package com.shop.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法ID生成器配置
 * <p>
 * 配置一个全局唯一的雪花算法ID生成器，用来生成订单号。
 * 雪花算法生成的ID是有序的、唯一的、不依赖数据库的，
 * 非常适合做订单号，即使以后分库分表也不会冲突。
 * </p>
 */
@Configuration
public class SnowflakeIdConfig {

    /**
     * 创建雪花算法ID生成器
     * <p>
     * workerId和datacenterId需要保证每个服务实例不一样，
     * 否则可能会生成重复的ID。在单机部署时用默认值就行，
     * 集群部署时可以通过环境变量或配置中心来设置不同的值。
     * </p>
     *
     * @return 雪花算法ID生成器
     */
    @Bean
    public SnowflakeIdWorker snowflakeIdWorker() {
        // workerId：机器ID（0~31），datacenterId：数据中心ID（0~31）
        // 实际生产环境应该从配置中心获取，这里先用默认值
        long workerId = Long.parseLong(System.getProperty("snowflake.workerId", "1"));
        long datacenterId = Long.parseLong(System.getProperty("snowflake.datacenterId", "1"));
        return new SnowflakeIdWorker(workerId, datacenterId);
    }

    /**
     * 雪花算法ID生成器
     * <p>
     * 雪花算法生成的ID是一个64位的Long型数字，结构如下：
     * 1位符号位（永远是0） + 41位时间戳 + 10位机器ID + 12位序列号
     * 每毫秒可以生成4096个不同的ID，完全够用了。
     * </p>
     */
    public static class SnowflakeIdWorker {

        /** 起始时间戳（2024-01-01 00:00:00），用这个作为基准计算时间戳 */
        private final long twepoch = 1704038400000L;

        /** 机器ID占用的位数（5位，最大31） */
        private final long workerIdBits = 5L;

        /** 数据中心ID占用的位数（5位，最大31） */
        private final long datacenterIdBits = 5L;

        /** 机器ID最大值（31） */
        private final long maxWorkerId = ~(-1L << workerIdBits);

        /** 数据中心ID最大值（31） */
        private final long maxDatacenterId = ~(-1L << datacenterIdBits);

        /** 序列号占用的位数（12位，最大4095） */
        private final long sequenceBits = 12L;

        /** 机器ID左移12位（序列号的位数） */
        private final long workerIdShift = sequenceBits;

        /** 数据中心ID左移17位（序列号位数 + 机器ID位数） */
        private final long datacenterIdShift = sequenceBits + workerIdBits;

        /** 时间戳左移22位（序列号位数 + 机器ID位数 + 数据中心ID位数） */
        private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

        /** 序列号掩码（4095），用来保证序列号不超过12位 */
        private final long sequenceMask = ~(-1L << sequenceBits);

        /** 机器ID（0~31） */
        private final long workerId;

        /** 数据中心ID（0~31） */
        private final long datacenterId;

        /** 序列号（同一毫秒内的计数器） */
        private long sequence = 0L;

        /** 上次生成ID的时间戳，用来判断是否是同一毫秒 */
        private long lastTimestamp = -1L;

        /**
         * 构造方法
         *
         * @param workerId     机器ID（0~31）
         * @param datacenterId 数据中心ID（0~31）
         */
        public SnowflakeIdWorker(long workerId, long datacenterId) {
            if (workerId > maxWorkerId || workerId < 0) {
                throw new IllegalArgumentException("workerId超出范围，必须在0~31之间");
            }
            if (datacenterId > maxDatacenterId || datacenterId < 0) {
                throw new IllegalArgumentException("datacenterId超出范围，必须在0~31之间");
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        /**
         * 生成下一个ID（线程安全）
         * <p>
         * 同一毫秒内，通过递增序列号来保证ID唯一；
         * 不同毫秒内，序列号重置为0。
         * 如果时钟回拨（当前时间比上次时间还早），就抛异常。
         * </p>
         *
         * @return 唯一的Long型ID
         */
        public synchronized long nextId() {
            long timestamp = System.currentTimeMillis();

            // 时钟回拨检测：如果当前时间比上次还早，说明系统时钟被调慢了
            if (timestamp < lastTimestamp) {
                throw new RuntimeException("时钟回拨，拒绝生成ID");
            }

            // 同一毫秒内：递增序列号
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & sequenceMask;
                // 序列号溢出（同一毫秒生成了超过4096个ID），等下一毫秒
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                // 不同毫秒：序列号重置为0
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            // 拼装ID：时间戳 | 数据中心ID | 机器ID | 序列号
            return ((timestamp - twepoch) << timestampLeftShift)
                    | (datacenterId << datacenterIdShift)
                    | (workerId << workerIdShift)
                    | sequence;
        }

        /**
         * 等到下一毫秒
         * <p>
         * 当同一毫秒的序列号用完了，就等到下一毫秒再生成ID。
         * </p>
         *
         * @param lastTimestamp 上次生成ID的时间戳
         * @return 下一毫秒的时间戳
         */
        private long tilNextMillis(long lastTimestamp) {
            long timestamp = System.currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
            return timestamp;
        }
    }
}
