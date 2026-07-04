package com.shop.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 限流Lua脚本配置
 * <p>
 * 把Lua脚本封装成Bean，启动时加载一次，后续每次限流都复用这个Bean。
 * Spring Data Redis会自动用EVALSHA命令（用脚本的SHA1摘要）执行，
 * 比每次都发送完整脚本省流量，性能更好。
 * </p>
 * <p>
 * 小白理解：Lua脚本就像一份"操作清单"，告诉Redis怎么判断限流。
 * 如果每次请求都把整份清单发给Redis，浪费网络带宽。
 * 所以我们启动时把清单"登记"到Redis，后续只发"清单编号"（SHA1）就行。
 * </p>
 */
@Configuration
public class RateLimitScriptConfig {

    /**
     * 滑动窗口限流Lua脚本
     * <p>
     * 算法：用Redis的Sorted Set（有序集合）存储请求时间戳
     * - member = 唯一请求ID（避免重复）
     * - score = 请求时间戳（毫秒）
     * </p>
     * <p>
     * 执行步骤（全部在Redis单线程内执行，保证原子性）：
     * 1. ZREMRANGEBYSCORE：移除窗口外的旧记录（滑动窗口的关键）
     * 2. ZCARD：统计当前窗口内的请求数
     * 3. 判断是否超限：超限返回0，未超限继续
     * 4. ZADD：添加本次请求记录
     * 5. PEXPIRE：设置Key过期时间（避免冷数据堆积）
     * 6. 返回1（放行）
     * </p>
     * <p>
     * 为什么不用固定窗口？固定窗口有"临界点"问题：
     * 比如限制每秒100次，用户在0.99秒发100次 + 1.01秒发100次 = 0.02秒内200次，
     * 但固定窗口检测不到。滑动窗口统计"过去1秒内"的请求数，能避免这个问题。
     * </p>
     * <p>
     * 参数说明：
     * - KEYS[1]：限流Key（如 rate_limit:ip:192.168.1.1）
     * - ARGV[1]：当前时间戳（毫秒）
     * - ARGV[2]：窗口大小（毫秒，固定1000ms=1秒）
     * - ARGV[3]：最大请求数（QPS限制）
     * - ARGV[4]：唯一请求ID（ZADD的member，用UUID+纳秒避免冲突）
     * </p>
     * <p>
     * 返回值：1=放行，0=被限流
     * </p>
     */
    private static final String RATE_LIMIT_SCRIPT_TEXT =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local uid = ARGV[4]\n" +
            "\n" +
            "-- 计算窗口起始时间（now - window = 1秒前）\n" +
            "local minScore = now - window\n" +
            "\n" +
            "-- 第1步：移除窗口外的旧记录（滑动窗口的核心）\n" +
            "redis.call('ZREMRANGEBYSCORE', key, '-inf', minScore)\n" +
            "\n" +
            "-- 第2步：统计当前窗口内的请求数\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "\n" +
            "-- 第3步：判断是否超限\n" +
            "if count >= limit then\n" +
            "    return 0\n" +
            "end\n" +
            "\n" +
            "-- 第4步：未超限，添加本次请求记录\n" +
            "redis.call('ZADD', key, now, uid)\n" +
            "\n" +
            "-- 第5步：设置Key过期时间（窗口大小+1秒缓冲，避免冷数据堆积）\n" +
            "redis.call('PEXPIRE', key, window + 1000)\n" +
            "\n" +
            "return 1";

    /**
     * 注册限流脚本Bean
     * <p>
     * 返回Long类型：1=放行，0=被限流
     * 用DefaultRedisScript而不是RedisScript接口，
     * 因为DefaultRedisScript实现了equals/hashCode，Spring会用SHA1缓存
     * </p>
     *
     * @return 限流脚本
     */
    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RATE_LIMIT_SCRIPT_TEXT);
        script.setResultType(Long.class);
        return script;
    }
}
