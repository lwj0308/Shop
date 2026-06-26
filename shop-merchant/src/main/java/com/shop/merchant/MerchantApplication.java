package com.shop.merchant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商家服务启动类
 * <p>
 * 商家服务负责：商家入驻申请、审核、店铺信息管理等功能。
 * 商家入驻后才能发布商品、处理订单。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.merchant", "com.shop.common"})
@MapperScan("com.shop.merchant.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shop.merchant.feign")
public class MerchantApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}
