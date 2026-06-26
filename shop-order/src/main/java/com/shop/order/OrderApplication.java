package com.shop.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类
 * <p>
 * 订单服务负责：创建订单、订单状态管理（待付款→已付款→已发货→已完成）、
 * 取消订单等功能。是电商交易的核心环节。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.order", "com.shop.common"})
@MapperScan("com.shop.order.mapper")
@EnableDiscoveryClient
@EnableFeignClients
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
