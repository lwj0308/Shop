package com.shop.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 * <p>
 * API网关是整个微服务系统的统一入口，所有前端请求都先到网关，
 * 网关再根据请求路径转发到对应的微服务。
 * </p>
 * <p>
 * 网关的主要职责：
 * 1. 路由转发：根据URL把请求转发到对应的微服务
 * 2. 负载均衡：同一个服务有多个实例时，自动选择一个
 * 3. 鉴权过滤：检查用户是否登录，没登录的请求直接拒绝
 * </p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
