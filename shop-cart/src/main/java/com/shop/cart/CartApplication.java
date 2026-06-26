package com.shop.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 购物车服务启动类
 * <p>
 * 购物车服务负责：添加商品到购物车、修改数量、删除商品、清空购物车等功能。
 * 用户下单前先把商品加入购物车，确认后再提交订单。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.cart", "com.shop.common"})
@MapperScan("com.shop.cart.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shop.cart.feign")
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
