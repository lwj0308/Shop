package com.shop.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权白名单配置
 * <p>
 * 从Nacos配置中心读取白名单路径列表。
 * 白名单中的路径不需要登录就能访问，比如登录、注册、商品浏览等。
 * </p>
 * <p>
 * 对应Nacos中 shop-gateway.yml 的配置格式：
 * <pre>
 * gateway:
 *   whitelist:
 *     - /api/user/auth/**
 *   auth-whitelist:
 *     - /api/product/list
 * </pre>
 * </p>
 * <p>
 * 白名单分两种：
 * 1. whitelist（完全公开）：不需要登录就能访问，比如登录、注册接口
 * 2. auth-whitelist（仅限登录用户）：需要登录但不需要特定角色，比如商品浏览
 * </p>
 * <p>
 * 小白理解：白名单就像小区的门禁系统，有些地方（比如大门口、物业中心）
 * 不需要刷卡就能进，其他地方必须刷卡。这里的"刷卡"就是登录Token。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class WhitelistConfig {

    /**
     * 完全公开的白名单路径列表
     * 不需要登录就能访问，比如登录、注册、验证码等接口
     * 支持Ant风格路径匹配，比如 /api/user/auth/** 表示用户认证下所有路径
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * 仅限登录用户的白名单路径列表
     * 需要登录（有有效Token），但不需要特定角色
     * 比如商品浏览、搜索等接口，登录后就能访问
     * 支持Ant风格路径匹配
     */
    private List<String> authWhitelist = new ArrayList<>();
}
