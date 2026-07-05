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
 * <p>
 * RL-01 阶段引入令牌桶脚本，配合原有滑动窗口脚本：
 * - 滑动窗口：低频高精度（认证、评论），每请求存一条 ZSET 记录
 * - 令牌桶：高频接口（秒杀、下单、商品浏览），只存 2 个字段，内存 O(1)
 * </p>
 */
@Configuration
public class RateLimitScriptConfig {

    /**
     * 滑动窗口限流Lua脚本（ZSet 精确滑动窗口）
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
     * 适用场景：低频高精度（认证 5 QPS、评论 3 QPS），每秒请求数少，ZSET 内存可接受。
     * 高频接口（50+ QPS）改用令牌桶，避免 ZSET 内存爆炸。
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
     * 令牌桶限流Lua脚本（token bucket）
     * <p>
     * 算法原理：
     * - 桶里放令牌，按固定速率（rate 令牌/秒）补充
     * - 桶容量（capacity）限制最大突发量
     * - 每个请求消耗1个令牌，没令牌就拒绝
     * </p>
     * <p>
     * Redis存储结构（Hash，内存 O(1)）：
     * - field "tokens"：当前剩余令牌数（浮点数）
     * - field "last_refill"：上次补充令牌的时间戳（毫秒）
     * </p>
     * <p>
     * 执行步骤（全部在Redis单线程内执行，保证原子性）：
     * 1. 读取当前 tokens 和 last_refill（首次请求时初始化为满桶）
     * 2. 计算时间差 Δt = now - last_refill
     * 3. 补充令牌：tokens = min(capacity, tokens + Δt × rate / 1000)
     * 4. 判断：tokens >= 1 → 消耗1令牌放行；tokens < 1 → 拒绝
     * 5. 更新 tokens 和 last_refill 到 Hash
     * 6. 设置 Key 过期时间（避免冷 Key 堆积）
     * </p>
     * <p>
     * 适用场景：高频接口（秒杀、下单、商品浏览），允许合理突发。
     * 例：rate=3, capacity=5 → 瞬时最多5个请求通过，之后按3个/秒速率补充
     * </p>
     * <p>
     * 参数说明：
     * - KEYS[1]：限流Key（如 rate_limit:ip:192.168.1.1）
     * - ARGV[1]：当前时间戳（毫秒）
     * - ARGV[2]：令牌生成速率（rate，令牌/秒）
     * - ARGV[3]：桶容量（capacity，最大突发量）
     * </p>
     * <p>
     * 返回值：1=放行，0=被限流
     * </p>
     * <p>
     * 小白理解：令牌桶就像自动售票机
     * - 售票机按固定速率吐号码牌（rate）
     * - 机器里最多存 capacity 张牌（满了就不再吐）
     * - 每个游客进来要拿1张牌，没牌就得等
     * - 短时来很多人时，机器里的牌被拿光，后面的人只能等下一张
     * - 不像滑动窗口死板，令牌桶允许"短时爆发"（先把存牌拿光）
     * </p>
     */
    private static final String TOKEN_BUCKET_SCRIPT_TEXT =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local rate = tonumber(ARGV[2])\n" +
            "local capacity = tonumber(ARGV[3])\n" +
            "\n" +
            "-- 读取当前令牌数和上次补充时间（首次请求时初始化为满桶）\n" +
            "local tokens = tonumber(redis.call('HGET', key, 'tokens'))\n" +
            "local lastRefill = tonumber(redis.call('HGET', key, 'last_refill'))\n" +
            "if tokens == nil then\n" +
            "    tokens = capacity\n" +
            "    lastRefill = now\n" +
            "end\n" +
            "\n" +
            "-- 计算时间差并补充令牌（按速率 rate 令牌/秒 = rate/1000 令牌/毫秒）\n" +
            "local delta = now - lastRefill\n" +
            "if delta > 0 then\n" +
            "    local refill = delta * rate / 1000.0\n" +
            "    tokens = math.min(capacity, tokens + refill)\n" +
            "    lastRefill = now\n" +
            "end\n" +
            "\n" +
            "-- 判断是否放行：令牌足够则消耗1个放行，否则拒绝\n" +
            "local allowed = 0\n" +
            "if tokens >= 1 then\n" +
            "    tokens = tokens - 1\n" +
            "    allowed = 1\n" +
            "end\n" +
            "\n" +
            "-- 更新令牌数和上次补充时间到 Hash\n" +
            "redis.call('HSET', key, 'tokens', tokens, 'last_refill', lastRefill)\n" +
            "\n" +
            "-- 设置 Key 过期时间（桶容量/速率 + 10秒缓冲，避免冷 Key 堆积）\n" +
            "-- 例：capacity=5, rate=3 → 5/3≈1.67秒 + 10秒缓冲 = 11.67秒\n" +
            "local ttl = math.ceil(capacity / rate * 1000) + 10000\n" +
            "redis.call('PEXPIRE', key, ttl)\n" +
            "\n" +
            "return allowed";

    /**
     * 注册滑动窗口限流脚本Bean
     * <p>
     * 返回Long类型：1=放行，0=被限流
     * 用DefaultRedisScript而不是RedisScript接口，
     * 因为DefaultRedisScript实现了equals/hashCode，Spring会用SHA1缓存
     * </p>
     *
     * @return 滑动窗口限流脚本
     */
    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RATE_LIMIT_SCRIPT_TEXT);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 注册令牌桶限流脚本Bean
     * <p>
     * 返回Long类型：1=放行，0=被限流
     * Bean名称区别于滑动窗口脚本，注入时按名称区分
     * </p>
     *
     * @return 令牌桶限流脚本
     */
    @Bean
    public DefaultRedisScript<Long> tokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(TOKEN_BUCKET_SCRIPT_TEXT);
        script.setResultType(Long.class);
        return script;
    }
}
