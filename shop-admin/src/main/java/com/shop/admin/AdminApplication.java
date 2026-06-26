package com.shop.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 管理后台服务启动类
 * <p>
 * 管理后台服务负责：管理员登录认证、RBAC权限管理、业务数据管理（用户/商家/商品/订单）、安全审计等功能。
 * 通过Feign调用其他微服务获取业务数据，管理后台本身只存储管理员和权限相关数据。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.admin", "com.shop.common"})
@MapperScan("com.shop.admin.mapper")
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
