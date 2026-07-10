package com.medconsult.common.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * common-redis 自动装配（架构文档 §3.2 / §7 / §9.2）。
 *
 * <p>业务服务引入本模块后：
 * <ul>
 *   <li>{@link DistributedLock}（Redis 实现，Lua 释放）</li>
 *   <li>{@link RateLimiter}（Redis 滑动窗口，多实例共享）</li>
 *   <li>{@link SseEventBus}（Redis Pub/Sub，§9.2 多实例 SSE 广播）</li>
 *   <li>{@link RedisMessageListenerContainer}（Pub/Sub 监听容器，供 SseEventBus 注册订阅）</li>
 * </ul>
 *
 * <p>前提：业务服务须配置 spring.data.redis.*（host/port/password）。
 * Spring Boot 的 Redis 自动配置<b>不</b>默认创建 RedisMessageListenerContainer，
 * 故本模块显式声明（§9.2 SSE 广播依赖它）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class MedConsultRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(StringRedisTemplate redis) {
        return new RedisDistributedLock(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(StringRedisTemplate redis) {
        return new RedisRateLimiter(redis);
    }

    /**
     * Pub/Sub 监听容器。SseEventBus 用它注册/取消订阅 channel。
     * 必须显式声明——Spring Boot 默认不创建此 bean。
     *
     * <p><b>taskExecutor</b>：显式设为有界线程池。不设的话 Spring 默认用 SimpleAsyncTaskExecutor，
     * 它对每个消息回调 new 一个线程（无上限），在多 channel / 高频 Pub/Sub 下会无限制创建线程，
     * 造成资源泄漏 / OOM。这里用核心 2 / 最大 8 的池，够 SSE 广播场景用。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("redis-sub-");
        executor.initialize();
        container.setTaskExecutor(executor);
        return container;
    }

    @Bean
    @ConditionalOnMissingBean
    public SseEventBus sseEventBus(StringRedisTemplate redis, RedisMessageListenerContainer container) {
        return new RedisSseEventBus(redis, container);
    }
}
