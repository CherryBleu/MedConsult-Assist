package com.medconsult.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis zset 滑动窗口限流（架构文档 §7.1）。
 *
 * <p>原理：每个请求作为一个 zset 成员，score=当前时间戳（毫秒），member=唯一标识。
 * 每次先移除窗口外的过期成员，再统计当前窗口内数量，未超阈值则写入新成员。
 *
 * <p>用单个 Lua 脚本保证"清理+计数+写入"原子（否则并发下会漏判）。
 *
 * <p>多实例共享：所有实例读写同一 Redis key，计数天然全局一致。
 */
public class RedisRateLimiter implements RateLimiter {

    /**
     * 滑动窗口限流 Lua：
     * 1. ZREMRANGEBYSCORE 清理窗口外
     * 2. ZCARD 统计当前窗口内成员数
     * 3. 未超阈值则 ZADD 写入 + EXPIRE 续期，返回 1；否则返回 0
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local windowMs = tonumber(ARGV[2]) " +
            "local max = tonumber(ARGV[3]) " +
            "local member = ARGV[4] " +
            // 清理窗口外过期成员
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs) " +
            // 统计当前窗口内数量
            "local count = redis.call('ZCARD', key) " +
            "if count < max then " +
            "  redis.call('ZADD', key, now, member) " +
            // 续期窗口（毫秒转秒，向上取整）
            "  redis.call('PEXPIRE', key, windowMs) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean acquire(String key, int max, Duration window) {
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        // member 唯一：now + 随机后缀，避免同毫秒请求互相覆盖
        String member = now + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        Long allowed = redis.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(max),
                member);
        return allowed != null && allowed == 1L;
    }
}
