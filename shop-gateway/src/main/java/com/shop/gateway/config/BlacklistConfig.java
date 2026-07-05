package com.shop.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑白名单配置类（RL-11 引入）
 * <p>
 * 从 Nacos 配置中心读取静态黑白名单（初始名单）。
 * 动态黑白名单存储在 Redis Set 中，由 BlacklistFilter 实时查询。
 * </p>
 * <p>
 * 黑白名单优先级：黑名单 > 白名单。
 * 如果一个 IP 同时在黑名单和白名单中，黑名单优先生效（直接 403 拒绝）。
 * </p>
 * <p>
 * 小白理解：
 * - 黑名单就像"通缉令"，名单上的人直接抓起来（403拒绝），不允许进入
 * - 白名单就像"VIP通道"，名单上的人不需要排队（跳过限流），直接进入
 * - 如果一个人既是通缉犯又是VIP，优先按通缉犯处理（安全第一）
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.blacklist")
public class BlacklistConfig {

    /**
     * 静态 IP 黑名单（初始名单，运行时通过 Redis Set 动态管理）
     * <p>
     * 配置在 Nacos 中的 IP 会永久拉黑（除非从配置中移除）。
     * 运行时通过 Redis 动态拉黑的 IP 有过期时间（默认1小时）。
     * </p>
     */
    private List<String> ipBlacklist = new ArrayList<>();

    /**
     * 静态用户 ID 黑名单
     * <p>
     * 被拉黑的用户ID，即使用其他IP登录也会被拦截。
     * </p>
     */
    private List<String> userBlacklist = new ArrayList<>();

    /**
     * 静态 IP 白名单
     * <p>
     * 白名单IP跳过限流检查，适合内部服务器、监控探针等。
     * </p>
     */
    private List<String> ipWhitelist = new ArrayList<>();

    /**
     * 静态用户 ID 白名单
     * <p>
     * 白名单用户跳过限流检查，适合内部测试账号、管理员等。
     * </p>
     */
    private List<String> userWhitelist = new ArrayList<>();

    /**
     * 自动拉黑阈值：触发限流超过此次数后自动拉黑
     * <p>
     * 比如 threshold=10，表示某个IP/用户触发限流10次后，自动拉黑1小时。
     * 设为0表示禁用自动拉黑功能。
     * </p>
     */
    private int autoBlockThreshold = 10;

    /**
     * 自动拉黑时长（秒），默认1小时
     */
    private long autoBlockDuration = 3600L;
}
