package com.shop.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * <p>
 * 配置Redis的序列化方式，默认的JDK序列化会在Redis中存储乱码，
 * 改成JSON序列化后，在Redis中可以直接看到数据内容，方便调试。
 * </p>
 * <p>
 * 注意：StringRedisTemplate保持默认的String序列化，
 * 因为库存扣减的Lua脚本需要用String类型操作。
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * 配置RedisTemplate的序列化方式
     * <p>
     * key用String序列化，value用JSON序列化。
     * 这样在Redis中存储的数据是人类可读的，方便排查问题。
     * </p>
     *
     * @param connectionFactory Redis连接工厂
     * @return 配置好的RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key使用String序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value使用JSON序列化（存储时自动转JSON，读取时自动转回Java对象）
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
