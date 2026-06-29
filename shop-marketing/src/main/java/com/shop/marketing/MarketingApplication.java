package com.shop.marketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 营销服务启动类
 * <p>
 * 营销服务负责：优惠券模板的创建/查询/上下架、满减活动的创建/查询/下架、满减优惠金额计算等功能。
 * 从 shop-merchant 模块拆分而来，专注于营销活动相关的业务。
 * </p>
 * <p>
 * 小白说明：这个类是营销服务的"总开关"，main方法运行后整个微服务就启动了。
 * - @SpringBootApplication：告诉Spring这是Spring Boot应用，并扫描 com.shop.marketing 和 com.shop.common 包
 * - @MapperScan：告诉MyBatis-Plus去 com.shop.marketing.mapper 包下找数据库操作接口
 * - @EnableDiscoveryClient：把服务注册到Nacos，让其他服务能找到我们
 * - @EnableFeignClients：开启Feign功能，可以调用其他微服务（比如查商家信息要调 shop-merchant）
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.marketing", "com.shop.common"})
@MapperScan("com.shop.marketing.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shop.marketing.feign")
public class MarketingApplication {

    /**
     * 程序入口方法，启动Spring Boot应用
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
