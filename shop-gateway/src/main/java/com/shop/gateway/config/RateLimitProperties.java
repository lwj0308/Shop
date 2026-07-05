package com.shop.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 限流配置类
 * <p>
 * 从Nacos配置中心读取限流规则。支持全局默认限流 + 按接口路径自定义限流。
 * 限流维度有两个：
 * 1. IP级别：限制单个IP每秒请求数（防止单机刷接口）
 * 2. 接口级别：限制单个接口每秒总请求数（保护后端服务不被打崩）
 * </p>
 * <p>
 * 算法选型（RL-01 阶段引入，按场景选择最优算法）：
 * - sliding-window（ZSet 精确滑动窗口）：低频高精度场景，如认证 5 QPS、评论 3 QPS
 *   优点：精度高（毫秒级）；缺点：每请求存一条 ZSET 记录，高频接口内存爆炸
 * - token-bucket（令牌桶）：高频接口，如秒杀、下单、商品浏览
 *   优点：内存 O(1)（只存 tokens + last_refill 两个字段），允许合理突发；工业界网关标准
 * </p>
 * <p>
 * 对应Nacos中 shop-gateway.yml 的配置格式：
 * <pre>
 * gateway:
 *   rate-limit:
 *     enabled: true                    # 总开关
 *     default-ip-qps: 50               # 默认每个IP每秒最多50次（sliding-window算法的QPS）
 *     default-path-qps: 200            # 默认每个接口每秒最多200次
 *     default-algorithm: token-bucket  # 默认算法（高频场景居多，默认令牌桶）
 *     rules:                           # 自定义规则（按路径匹配，匹配到的优先用）
 *       - path-pattern: /api/user/auth/**   # 认证接口（低频高精度）
 *         algorithm: sliding-window
 *         ip-qps: 5                          # 单IP每秒最多5次
 *         path-qps: 50                       # 接口总每秒最多50次
 *       - path-pattern: /api/seckill/grab/**  # 秒杀接口（高频突发）
 *         algorithm: token-bucket
 *         ip-qps: 3                          # 令牌生成速率（令牌/秒）
 *         capacity: 5                        # 桶容量（最大突发量）
 * </pre>
 * </p>
 * <p>
 * 小白理解：限流就像游乐园的过山车，每小时最多接待500人。
 * 不同项目排队规则不一样：
 * - 普通过山车：每人每小时能玩5次（默认IP QPS=50）
 * - 热门过山车：每人每小时只能玩1次（自定义规则IP QPS=5）
 * 排队规则可以按"项目名"（接口路径）单独配置，没匹配到的就用默认值。
 * </p>
 * <p>
 * 算法选型小白版：
 * - sliding-window 像保安每次都翻看过去1秒的来访登记本，精确但人多时翻本子慢
 * - token-bucket 像自动售票机，按固定速率发号码牌，桶里有多少牌就放多少人，允许短时爆发
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /**
     * 限流总开关
     * true=启用限流，false=关闭限流（所有请求都放行）
     * 默认开启，可以在Nacos中随时关闭用于调试
     */
    private boolean enabled = true;

    /**
     * 默认IP级别QPS：每个IP每秒允许的最大请求数
     * 没有匹配到自定义规则的接口，都用这个值
     * 对 sliding-window 算法：每秒最大请求数
     * 对 token-bucket 算法：令牌生成速率（令牌/秒）
     */
    private long defaultIpQps = 50L;

    /**
     * 默认接口级别QPS：每个接口每秒允许的最大请求数
     * 没有匹配到自定义规则的接口，都用这个值
     * 对 sliding-window 算法：每秒最大请求数
     * 对 token-bucket 算法：令牌生成速率（令牌/秒）
     */
    private long defaultPathQps = 200L;

    /**
     * 默认限流算法：token-bucket / sliding-window
     * 默认用令牌桶（高频场景多，允许突发流量）
     */
    private String defaultAlgorithm = "token-bucket";

    /**
     * 默认令牌桶容量（仅 token-bucket 算法生效）
     * 桶容量 = 突发上限，一般设为 rate 的 2 倍，允许短时突发
     */
    private long defaultCapacity = 100L;

    /**
     * 自定义限流规则列表
     * 按路径模式匹配，匹配到的接口用规则中配置的QPS
     * 排在前面的规则优先匹配
     */
    private List<Rule> rules = new ArrayList<>();

    /**
     * 单条限流规则
     * <p>
     * 给特定接口配置独立的限流值和算法，比如：
     * - 登录接口限制严格（防爆破）：sliding-window，IP 5 QPS
     * - 秒杀接口限制宽松但IP严格（防刷单）：token-bucket，IP 3 QPS（速率），桶容量 5
     * </p>
     */
    @Data
    public static class Rule {

        /**
         * 路径匹配模式（Ant风格）
         * 比如 /api/user/auth/** 匹配 /api/user/auth/login、/api/user/auth/register 等
         */
        private String pathPattern;

        /**
         * 限流算法：sliding-window（ZSet 精确滑动窗口）或 token-bucket（令牌桶）
         * 不配置时使用 default-algorithm
         */
        private String algorithm;

        /**
         * IP级别QPS：该规则匹配的接口，单IP每秒允许的最大请求数
         * 对 sliding-window：每秒最大请求数
         * 对 token-bucket：令牌生成速率（令牌/秒）
         */
        private long ipQps;

        /**
         * 接口级别QPS：该规则匹配的接口，总每秒允许的最大请求数
         * 对 sliding-window：每秒最大请求数
         * 对 token-bucket：令牌生成速率（令牌/秒）
         */
        private long pathQps;

        /**
         * 令牌桶容量（仅 token-bucket 算法生效）
         * 桶容量 = 突发上限，未配置时默认取 ip-qps/path-qps 的 2 倍
         */
        private Long capacity;

        /**
         * 用户级别QPS：登录用户每秒允许的最大请求数（RL-10 引入）
         * <p>
         * 仅对登录用户生效。未登录用户仍用 IP 维度限流。
         * 防止用户用多IP绕过IP限流刷接口（比如用代理池秒杀）。
         * </p>
         * <p>
         * 小白理解：IP限流就像"每个门牌号每小时只能进5次"，
         * 但有人有多个门牌号（代理IP）就能进很多次。
         * 用户限流就是"每个人每小时只能进5次"，不管你从哪个门进。
         * </p>
         * 未配置时默认为0，表示不启用用户维度限流（兼容旧配置）
         */
        private long userQps;
    }
}
