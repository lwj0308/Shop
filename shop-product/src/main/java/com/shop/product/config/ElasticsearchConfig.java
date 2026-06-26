package com.shop.product.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch配置类
 * <p>
 * 配置ES客户端连接，使用Elasticsearch Java Client（新版本API）。
 * 连接地址从application.yml中读取，默认localhost:9200。
 * </p>
 */
@Configuration
public class ElasticsearchConfig {

    /** ES服务器地址 */
    @Value("${elasticsearch.host:localhost}")
    private String host;

    /** ES服务器端口 */
    @Value("${elasticsearch.port:9200}")
    private int port;

    /** ES协议，一般是http */
    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    /**
     * 创建ES低层RestClient
     * <p>
     * 这是ES的底层HTTP客户端，负责和ES服务器通信。
     * </p>
     *
     * @return RestClient实例
     */
    @Bean
    public RestClient restClient() {
        return RestClient.builder(new HttpHost(host, port, scheme)).build();
    }

    /**
     * 创建ES传输层
     * <p>
     * 使用Jackson做JSON序列化/反序列化，
     * 比ES自带的更灵活，支持更多Java类型。
     * </p>
     *
     * @param restClient ES低层客户端
     * @return RestClientTransport实例
     */
    @Bean
    public RestClientTransport restClientTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    /**
     * 创建ES高层客户端
     * <p>
     * 这是我们业务代码中用的客户端，提供了类型安全的API。
     * 比如搜索、索引、聚合等操作都通过这个客户端完成。
     * </p>
     *
     * @param transport ES传输层
     * @return ElasticsearchClient实例
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
