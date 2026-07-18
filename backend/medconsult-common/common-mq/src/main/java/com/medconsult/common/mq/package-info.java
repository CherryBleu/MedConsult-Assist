/**
 * common-mq：跨服务一致性基础设施（对应架构文档 §3.2 / §6）。
 *
 * <p>已实现：
 * <ul>
 *   <li>{@link com.medconsult.common.mq.LocalMessage} - 本地消息表实体（流水表，不继承 BaseEntity）</li>
 *   <li>{@link com.medconsult.common.mq.LocalMessageMapper} - Mapper</li>
 *   <li>{@link com.medconsult.common.mq.MqConstants} - Exchange/Queue/RoutingKey 常量（§4.2）</li>
 *   <li>{@link com.medconsult.common.mq.IdempotentConsumer} - 消费者幂等（Redis PROCESSING/DONE 状态机，§6.1）</li>
 *   <li>{@link com.medconsult.common.mq.MessageDispatcher} - 扫描 PENDING 投递（§6.3 不选主）</li>
 *   <li>{@link com.medconsult.common.mq.MedConsultMqAutoConfiguration} - 自动装配</li>
 * </ul>
 *
 * <p><b>可靠投递模式</b>（§6.1）：
 * <pre>
 *   [业务服务] Tx: 写业务表 + 写 local_message（同一本地事务）
 *              ─── commit ───▶
 *   [MessageDispatcher] 每 5s 扫 PENDING → 发 RabbitMQ → 置 CONFIRMED
 *   [消费者] @RabbitListener 收到 → IdempotentConsumer.executeOnce(messageNo, 业务)
 * </pre>
 *
 * <p><b>不选主</b>（§6.3 关键）：多实例同时扫描会重复投递，靠消费者幂等吸收。
 * 这比 ShedLock 选主更简单，且消费者幂等本就是必须项（防 MQ 自身重投）。
 */
package com.medconsult.common.mq;
