package com.shop.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品服务启动类
 * <p>
 * 商品服务负责：商品发布、商品上下架、分类管理、库存管理等功能。
 * 是电商平台的核心服务，用户浏览和搜索商品都依赖这个服务。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.product", "com.shop.common"})
@MapperScan("com.shop.product.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shop.product.feign")
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
