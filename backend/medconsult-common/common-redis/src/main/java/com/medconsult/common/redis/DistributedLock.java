package com.medconsult.common.redis;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式锁接口（架构文档 §3.2 / §7.1 / §7.2）。
 *
 * <p><b>业务并发禁止用 JVM 锁</b>——多实例下 JVM 锁只锁单个实例，跨实例失效。
 * 所有需要互斥的业务（号源抢占、库存扣减、支付幂等）必须走本接口的 Redis 实现。
 *
 * <p>实现保证：
 * <ul>
 *   <li>原子获取（SET NX PX）+ 原子释放（Lua 脚本校验 owner token 再删，防误删他人锁）</li>
 *   <li>owner token 为 UUID，只有持锁者能释放</li>
 *   <li>租约到期自动释放（防持有者崩溃死锁）</li>
 * </ul>
 *
 * <p><b>注意：本实现非可重入</b>（同一线程重复 acquire 会失败）。若业务需要可重入，
 * 后续可基于 Redis Hash + 计数器扩展。当前业务场景（抢号/库存/支付）不需要重入。
 */
public interface DistributedLock {

    /**
     * 尝试获取锁，成功返回 true。
     *
     * @param key     锁键（建议带 `lock:` 前缀，如 lock:drug:123:stock）
     * @param lease   持有租约（到期自动释放，防死锁）。建议 = 业务最长执行时间
     * @return true 获得锁；false 已被他人持有
     */
    boolean tryLock(String key, Duration lease);

    /**
     * 释放锁。仅当调用方持有该锁（owner token 匹配）时才真正删除。
     */
    void unlock(String key);

    /**
     * 获取锁后执行 supplier，无论成功/异常都确保释放。
     *
     * @param action  持锁期间执行的业务逻辑
     * @param <T>     返回类型
     * @return action 的返回值；未获锁则抛 {@link LockNotAcquiredException}
     */
    <T> T withLock(String key, Duration lease, Supplier<T> action);

    /**
     * 未获得锁时抛出，业务可捕获转为 409 CONFLICT（号源已被抢占）。
     */
    class LockNotAcquiredException extends RuntimeException {
        public LockNotAcquiredException(String key) {
            super("分布式锁获取失败: " + key);
        }
    }
}
