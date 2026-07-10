package com.medconsult.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.medconsult.common.redis.SseChannelProperties;

@Configuration
public class RedisPubSubConfig {
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    public SseChannelProperties sseChannelProperties(AiProperties properties) {
        return () -> properties.redis() == null ? "medical:" : properties.redis().keyPrefix();
    }
}
