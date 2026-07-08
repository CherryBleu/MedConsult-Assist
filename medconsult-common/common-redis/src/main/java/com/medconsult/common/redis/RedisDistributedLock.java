package com.medconsult.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 基于 Redis 的分布式锁实现（架构文档 §7.1 / §7.2）。
 *
 * <p>获取：{@code SET key token NX PX lease}（原子，单命令）。
 * <p>释放：Lua 脚本 {@code if get==token then del}（原子校验+删除，防误删他人锁）。
 *
 * <p>owner token 存 ThreadLocal 供同线程 unlock 校验。多实例/多线程下，
 * 每个持锁线程持有独立 token，互不影响。
 */
public class RedisDistributedLock implements DistributedLock {

    /**
     * 释放锁的 Lua 脚本：仅当 key 的值等于 owner token 时才删除。
     * 避免这样的事故：A 持锁过期 → B 获新锁 → A 执行完 del 把 B 的锁删了。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end",
            Long.class);

    /** 每个锁键 → 当前线程持有的 token（ThreadLocal 不够，因 withLock 可能跨方法） */
    private final ConcurrentHashMap<String, String> ownerTokens = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;

    public RedisDistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(String key, Duration lease) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, lease);
        if (Boolean.TRUE.equals(ok)) {
            ownerTokens.put(key, token);
            return true;
        }
        return false;
    }

    @Override
    public void unlock(String key) {
        String token = ownerTokens.remove(key);
        if (token == null) {
            // 当前线程未持锁，忽略（防御）
            return;
        }
        redis.execute(UNLOCK_SCRIPT, List.of(key), token);
    }

    @Override
    public <T> T withLock(String key, Duration lease, Supplier<T> action) {
        if (!tryLock(key, lease)) {
            throw new LockNotAcquiredException(key);
        }
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }
}
