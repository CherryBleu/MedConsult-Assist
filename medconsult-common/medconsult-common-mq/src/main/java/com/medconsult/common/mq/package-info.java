/**
 * common-mq：跨服务一致性基础设施（待实现，对应架构文档 §3.2 / §6）。
 *
 * <p>计划内容：
 * <ul>
 *   <li>{@code LocalMessage} - 本地消息表实体，业务写 + 消息入队在同一本地事务</li>
 *   <li>{@code MessageDispatcher} - 扫描 PENDING 消息投递 RabbitMQ，退避重试，超限进 DLQ</li>
 *   <li>{@code IdempotentConsumer} - 消费者幂等基类（以 messageNo 为去重键 + Redis SETNX）</li>
 *   <li>Exchange/Queue/RoutingKey 常量定义（ai.imaging / notification / log / drug.event）</li>
 * </ul>
 *
 * <p>关键约束（架构文档 §6.3）：本地消息表扫描**不选主**，靠消费者幂等吸收重复发送。
 *
 * <p>本模块当前为占位。
 */
package com.medconsult.common.mq;
