package com.medconsult.common.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * common-redis 组件测试：连真实 Redis 验证（架构文档 §7 / §9.2）。
 *
 * <p>验证点：
 * <ul>
 *   <li>DistributedLock: 互斥、withLock 执行后释放、租约过期自动释放</li>
 *   <li>RateLimiter: 滑动窗口阈值生效、窗口过后恢复</li>
 *   <li>SseEventBus: publish 后订阅者收到（Pub/Sub）</li>
 *   <li>RedisKey: 常量格式</li>
 * </ul>
 *
 * <p>Redis 由测试的 application.yml 配置（默认 localhost:16379）。
 * 启动方式：{@code docker run -d -p 16379:6379 redis:7-alpine}，
 * 或设环境变量 REDIS_HOST/REDIS_PORT 指向任意可达 Redis。
 */
@SpringBootTest
class RedisComponentTest {

    @Autowired
    DistributedLock lock;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    SseEventBus eventBus;

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate redis;

    @Test
    void distributedLock_mutualExclusion() {
        String key = "test:lock:mutex:" + System.nanoTime();
        assertTrue(lock.tryLock(key, Duration.ofSeconds(10)), "首次获取应成功");
        assertFalse(lock.tryLock(key, Duration.ofSeconds(10)), "未释放前再次获取应失败");
        lock.unlock(key);
        assertTrue(lock.tryLock(key, Duration.ofSeconds(10)), "释放后应可再次获取");
        lock.unlock(key);
    }

    @Test
    void distributedLock_withLock_executesAndReleases() {
        String key = "test:lock:with:" + System.nanoTime();
        String result = lock.withLock(key, Duration.ofSeconds(10), () -> "executed");
        assertEquals("executed", result);
        assertTrue(lock.tryLock(key, Duration.ofSeconds(10)), "执行完应已释放");
        lock.unlock(key);
    }

    @Test
    void distributedLock_withLock_throwsWhenNotAcquired() {
        String key = "test:lock:fail:" + System.nanoTime();
        assertTrue(lock.tryLock(key, Duration.ofSeconds(10)));
        assertThrows(DistributedLock.LockNotAcquiredException.class,
                () -> lock.withLock(key, Duration.ofSeconds(10), () -> "should not run"));
        lock.unlock(key);
    }

    @Test
    void distributedLock_leaseExpiryAutoReleases() throws InterruptedException {
        String key = "test:lock:lease:" + System.nanoTime();
        assertTrue(lock.tryLock(key, Duration.ofMillis(200)));
        Thread.sleep(400);
        assertTrue(lock.tryLock(key, Duration.ofSeconds(10)), "租约过期后应可被他人获取");
        lock.unlock(key);
    }

    @Test
    void rateLimiter_throttlesBeyondMax() {
        String key = "test:rate:" + System.nanoTime();
        int max = 3;
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (rateLimiter.acquire(key, max, Duration.ofSeconds(10))) {
                allowed++;
            }
        }
        assertEquals(max, allowed, "超过阈值的请求应被拒，刚好放行 max 个");
    }

    @Test
    void rateLimiter_recoversAfterWindow() throws InterruptedException {
        String key = "test:rate-recover:" + System.nanoTime();
        for (int i = 0; i < 3; i++) {
            assertTrue(rateLimiter.acquire(key, 3, Duration.ofMillis(300)));
        }
        assertFalse(rateLimiter.acquire(key, 3, Duration.ofMillis(300)), "窗口内应已满");
        Thread.sleep(400);
        assertTrue(rateLimiter.acquire(key, 3, Duration.ofMillis(300)), "新窗口应可放行");
    }

    @Test
    void sseEventBus_publishReachesSubscriber() throws InterruptedException {
        String channel = "test:sse:" + System.nanoTime();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        eventBus.subscribe(channel, msg -> {
            received.set(msg);
            latch.countDown();
        });

        // Pub/Sub 异步，订阅注册后给 Redis 一点传播时间
        Thread.sleep(100);
        eventBus.publish(channel, "hello-sse");

        assertTrue(latch.await(2, TimeUnit.SECONDS), "订阅者应在 2s 内收到消息");
        assertEquals("hello-sse", received.get());
    }

    @Test
    void redisKey_constantsWellFormed() {
        assertTrue(RedisKey.sseChannel(42L).endsWith(":42"));
        assertTrue(RedisKey.DRUG_STOCK_LOCK.startsWith("medconsult:lock:drug:"));
        assertTrue(RedisKey.RATELIMIT_AI.startsWith("medconsult:ratelimit:ai:"));
    }
}
