package com.medconsult.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 基于 Redis 的分布式锁实现（架构文档 §7.1 / §7.2）。
 *
 * <p>获取：{@code SET key token NX PX lease}（原子，单命令）。
 * <p>释放：Lua 脚本 {@code if get==token then del}（原子校验+删除，防误删他人锁）。
 *
 * <p><b>owner token 绑定到线程（ThreadLocal）</b>。
 * <p>早期版本曾用全局共享的 {@code ConcurrentHashMap<lockKey, token>} 记录持锁者，但这有缺陷：
 * 租约过期后另一线程获取同一锁键会<b>覆盖</b> map 中的 token，原持锁者 unlock 时拿到的
 * 是别人的 token，Lua 校验 {@code get(key)==otherToken} 反而匹配并<b>误删了新锁</b>，
 * 彻底破坏 owner-token 防误删语义。
 * <p>改用 {@link ThreadLocal} 持有 {@code Map<lockKey, token>}：每个线程只能读到自己写入的
 * token，不会被别的线程覆盖；租约过期被他人抢锁时，原线程 unlock 拿到的是<b>自己的旧 token</b>，
 * Lua 校验 {@code get(key)==selfOldToken} 不匹配（Redis 里已是新 token），不会误删。
 *
 * <p><b>注意</b>：业务并发禁止用 JVM 锁（多实例失效），本类所有互斥（号源/库存/支付）都走 Redis。
 * 本类非可重入（同线程重复 tryLock 同一 key 会失败）；如需重入，后续基于 Hash+计数器扩展。
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

    /**
     * 每个线程持有的「锁键 → 本次获取的 token」映射。ThreadLocal 保证线程隔离：
     * 不同线程的 token 互不可见，杜绝共享 Map 下的 token 覆盖问题。
     * 用 Map 支持同线程持有多把不同 key 的锁（如先锁号源再锁处方，key 不同）。
     */
    private static final ThreadLocal<Map<String, String>> HELD = ThreadLocal.withInitial(HashMap::new);

    private final StringRedisTemplate redis;

    public RedisDistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(String key, Duration lease) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, lease);
        if (Boolean.TRUE.equals(ok)) {
            HELD.get().put(key, token);
            return true;
        }
        return false;
    }

    @Override
    public void unlock(String key) {
        // 取出<b>本线程</b>写入的 token（ThreadLocal 隔离，不会被其他线程覆盖）。
        String token = HELD.get().remove(key);
        if (token == null) {
            // 本线程未持锁（或已被 unlock 过），忽略（防御性空操作）
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
