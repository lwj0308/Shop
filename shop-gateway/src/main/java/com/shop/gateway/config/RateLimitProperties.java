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
 * 对应Nacos中 shop-gateway.yml 的配置格式：
 * <pre>
 * gateway:
 *   rate-limit:
 *     enabled: true                    # 总开关
 *     default-ip-qps: 50               # 默认每个IP每秒最多50次
 *     default-path-qps: 200            # 默认每个接口每秒最多200次
 *     rules:                           # 自定义规则（按路径匹配，匹配到的优先用）
 *       - path-pattern: /api/user/auth/**   # 认证接口（容易被打）
 *         ip-qps: 5                          # 单IP每秒最多5次
 *         path-qps: 50                       # 接口总每秒最多50次
 *       - path-pattern: /api/seckill/**      # 秒杀接口（高并发）
 *         ip-qps: 3                          # 单IP每秒最多3次
 *         path-qps: 1000                     # 接口总每秒最多1000次
 * </pre>
 * </p>
 * <p>
 * 小白理解：限流就像游乐园的过山车，每小时最多接待500人。
 * 不同项目排队规则不一样：
 * - 普通过山车：每人每小时能玩5次（默认IP QPS=50）
 * - 热门过山车：每人每小时只能玩1次（自定义规则IP QPS=5）
 * 排队规则可以按"项目名"（接口路径）单独配置，没匹配到的就用默认值。
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
     */
    private long defaultIpQps = 50L;

    /**
     * 默认接口级别QPS：每个接口每秒允许的最大请求数
     * 没有匹配到自定义规则的接口，都用这个值
     */
    private long defaultPathQps = 200L;

    /**
     * 自定义限流规则列表
     * 按路径模式匹配，匹配到的接口用规则中配置的QPS
     * 排在前面的规则优先匹配
     */
    private List<Rule> rules = new ArrayList<>();

    /**
     * 单条限流规则
     * <p>
     * 给特定接口配置独立的限流值，比如：
     * - 登录接口限制严格（防爆破）：IP 5 QPS
     * - 秒杀接口限制宽松但IP严格（防刷单）：IP 3 QPS，接口 1000 QPS
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
         * IP级别QPS：该规则匹配的接口，单IP每秒允许的最大请求数
         */
        private long ipQps;

        /**
         * 接口级别QPS：该规则匹配的接口，总每秒允许的最大请求数
         */
        private long pathQps;
    }
}
