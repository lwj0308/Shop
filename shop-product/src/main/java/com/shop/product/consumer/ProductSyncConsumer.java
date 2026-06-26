package com.shop.product.consumer;

import com.shop.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 商品同步ES消费者
 * <p>
 * 监听RocketMQ的 topic_product_sync 主题，
 * 当商品创建/更新/上下架时，生产者会发送消息到这个主题，
 * 消费者收到消息后，将商品数据同步到Elasticsearch。
 * </p>
 * <p>
 * 使用RocketMQ实现异步同步的好处：
 * 1. 商品主流程（数据库操作）不需要等ES同步完成，响应更快
 * 2. ES同步失败不影响商品主流程，可以重试
 * 3. 解耦，商品服务和搜索服务可以独立部署
 * </p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "topic_product_sync",           // 监听的主题
        consumerGroup = "product-sync-consumer-group"  // 消费者组名
)
@RequiredArgsConstructor
public class ProductSyncConsumer implements RocketMQListener<String> {

    /** 搜索服务，用来同步商品数据到ES */
    private final ProductSearchService productSearchService;

    /**
     * 消费消息
     * <p>
     * 收到商品ID后，调用搜索服务将商品数据同步到ES。
     * 消息内容就是商品ID的字符串形式。
     * </p>
     *
     * @param productId 商品ID
     */
    @Override
    public void onMessage(String productId) {
        try {
            log.info("收到商品同步ES消息: productId={}", productId);
            productSearchService.syncProductToES(Long.parseLong(productId));
            log.info("商品同步ES成功: productId={}", productId);
        } catch (Exception e) {
            log.error("商品同步ES失败: productId={}", productId, e);
            // 抛出异常会触发RocketMQ重试机制
            throw new RuntimeException("商品同步ES失败", e);
        }
    }
}
