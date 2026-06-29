package com.shop.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 秒杀服务启动类
 * <p>
 * 秒杀服务负责：秒杀活动的创建、下架、查询，以及 Redis 库存预热。
 * 用户抢购时通过 Lua 脚本原子扣减 Redis 库存，防止高并发下超卖。
 * 秒杀服务本身不提供登录接口，登录由 shop-merchant 处理；
 * 但秒杀服务需要验证商家身份（商家端创建秒杀活动），所以保留 Sa-Token 拦截器。
 * </p>
 * <p>
 * 小白讲解：这个类是秒杀服务的入口，main 方法跑起来后，
 * Spring 会自动扫描 com.shop.seckill 和 com.shop.common 包下的代码，
 * 把秒杀活动相关的 Controller、Service、Mapper 都加载进来。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.shop.seckill", "com.shop.common"})
@MapperScan("com.shop.seckill.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shop.seckill.feign")
public class SeckillApplication {

    /**
     * 程序入口方法，启动 Spring Boot 应用
     * <p>小白讲解：跟所有 Java 程序一样，从这里开始运行，把整个秒杀服务拉起来</p>
     *
     * @param args 启动参数，一般用不到
     */
    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}
