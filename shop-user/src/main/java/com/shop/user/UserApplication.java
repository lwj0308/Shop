package com.shop.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 * <p>
 * 用户服务负责：注册、登录、用户信息管理、消息通知、用户优惠券等功能。
 * 是电商平台最基础的服务，其他服务都需要依赖用户信息。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.user", "com.shop.common"})
@MapperScan("com.shop.user.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shop.user.feign")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
