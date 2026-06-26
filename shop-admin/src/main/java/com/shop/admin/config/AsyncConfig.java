package com.shop.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * Spring的@Async注解默认使用SimpleAsyncTaskExecutor，
 * 每次执行任务都创建新线程，没有上限，高并发下可能创建大量线程导致OOM。
 * 这个配置类自定义了一个线程池，限制线程数量，防止资源耗尽。
 * </p>
 * <p>
 * 线程池参数说明：
 * - 核心线程数5：平时保持5个线程待命，随时可以执行任务
 * - 最大线程数20：高峰期最多20个线程同时工作
 * - 队列容量100：任务太多时，先排队等，最多排100个
 * - 拒绝策略：队列满了后，由调用者线程自己执行（不丢弃任务）
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 自定义异步任务线程池
     * <p>
     * 给@Async注解指定线程池，避免使用默认的无限制线程池。
     * 用法：@Async("adminAsyncExecutor")
     * </p>
     *
     * @return 线程池执行器
     */
    @Bean("adminAsyncExecutor")
    public Executor adminAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：平时保持这么多线程待命
        executor.setCorePoolSize(5);
        // 最大线程数：高峰期最多这么多线程同时工作
        executor.setMaxPoolSize(20);
        // 队列容量：任务太多时先排队，最多排这么多
        executor.setQueueCapacity(100);
        // 线程名前缀：方便在日志中识别是哪个线程池的线程
        executor.setThreadNamePrefix("admin-async-");
        // 拒绝策略：队列满了后，由提交任务的线程自己执行（不丢弃任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
