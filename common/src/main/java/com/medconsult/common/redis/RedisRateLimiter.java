package com.medconsult.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class RedisRateLimiter {
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1] - ARGV[2])
            local current = redis.call('ZCARD', KEYS[1])
            if current >= tonumber(ARGV[3]) then
              return current
            end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
            redis.call('EXPIRE', KEYS[1], math.ceil(ARGV[2] / 1000))
            return current + 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        Long current = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(now),
                String.valueOf(window.toMillis()),
                String.valueOf(maxRequests),
                now + ":" + UUID.randomUUID()
        );
        return current == null || current <= maxRequests;
    }
}
