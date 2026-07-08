package com.medconsult.common.redis;

import java.time.Duration;

/**
 * 限流器接口（架构文档 §3.2 / §7.1）。
 *
 * <p>基于 Redis 滑动窗口（zset），<b>多实例共享计数</b>——
 * 这点至关重要：单实例限流会让总流量 = 实例数 × 阈值，成本失控（AI 接口烧钱）。
 *
 * <p>典型场景：AI 接口限流 {@code ratelimit:ai:{userId}}（§7.1）。
 */
public interface RateLimiter {

    /**
     * 尝试获取一个许可。
     *
     * @param key     限流键（如 ratelimit:ai:user:1001）
     * @param max     窗口内最大请求数
     * @param window  时间窗口
     * @return true 允许；false 超过阈值被拒
     */
    boolean acquire(String key, int max, Duration window);
}
