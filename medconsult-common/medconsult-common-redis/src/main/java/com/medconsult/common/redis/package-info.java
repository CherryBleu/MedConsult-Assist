/**
 * common-redis：分布式并发与缓存基础设施（对应架构文档 §3.2 / §7 / §9.2）。
 *
 * <p>已实现：
 * <ul>
 *   <li>{@link com.medconsult.common.redis.DistributedLock} / {@link com.medconsult.common.redis.RedisDistributedLock} -
 *       SET NX PX + Lua 释放，owner token 防误删</li>
 *   <li>{@link com.medconsult.common.redis.RateLimiter} / {@link com.medconsult.common.redis.RedisRateLimiter} -
 *       zset 滑动窗口，多实例共享计数</li>
 *   <li>{@link com.medconsult.common.redis.SseEventBus} / {@link com.medconsult.common.redis.RedisSseEventBus} -
 *       Redis Pub/Sub，解决多实例 SSE 长连接实例亲和性（§9.2）</li>
 *   <li>{@link com.medconsult.common.redis.RedisKey} - 统一键命名常量（§7.1）</li>
 *   <li>{@link com.medconsult.common.redis.MedConsultRedisAutoConfiguration} - 自动装配</li>
 * </ul>
 *
 * <p><b>核心约束</b>（架构文档 §7.2）：业务并发<b>禁止用 JVM 锁</b>——多实例下 JVM 锁
 * 只锁单个实例，跨实例失效。所有需要互斥的业务（号源/库存/支付幂等）必须走本模块的
 * {@link com.medconsult.common.redis.DistributedLock}。
 */
package com.medconsult.common.redis;
