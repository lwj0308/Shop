package com.shop.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 支付服务启动类
 * <p>
 * 支付服务负责：发起支付、处理支付回调、退款等功能。
 * 对接第三方支付平台（如支付宝、微信支付），处理资金流转。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.payment", "com.shop.common"})
@MapperScan("com.shop.payment.mapper")
@EnableDiscoveryClient
@EnableFeignClients
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
