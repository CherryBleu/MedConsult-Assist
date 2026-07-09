package com.medconsult.common.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表投递调度器（架构文档 §6.1 / §6.3）。
 *
 * <p>扫描 status=PENDING（或待重试的 SENT）的本地消息，投递到 RabbitMQ。
 *
 * <p><b>不选主</b>（§6.3 关键）：每个实例都跑本调度器，多实例同时扫描会重复投递，
 * 重复由 {@link IdempotentConsumer} 在消费端吸收。这比引入 ShedLock 选主更简单，
 * 且消费者幂等本就是必须项（防 MQ 自身重投）。
 *
 * <p><b>publisher confirm 可靠投递</b>：投递后状态置 SENT，等 RabbitMQ broker 的 confirm 回调
 * （见 {@link RabbitTemplate.ConfirmCallback}）ack 成功后才置 CONFIRMED。
 * 这样 broker 在投递后崩溃不会丢消息——broker 没 ack 的 SENT 消息会在下轮扫描里被重投。
 * <p>前提：各业务服务 application.yml 须配
 * {@code spring.rabbitmq.publisher-confirm-type: correlated}，否则 ConfirmCallback 不触发，
 * 回退为"投递后长期停留在 SENT"（消息不丢，但 CONFIRMED 状态推进不了）。
 *
 * <p>退避重试：失败的 SENT 消息按 nextRetryAt 重新投递，retryCount++，超 {@link #maxRetry}
 * 置 FAILED，进 DLQ（由 RabbitMQ 死信队列配置处理，本类只标记终态）。
 */
public class MessageDispatcher implements RabbitTemplate.ConfirmCallback {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    /** 单次扫描批量上限 */
    private static final int BATCH_SIZE = 100;

    @Autowired
    private LocalMessageMapper localMessageMapper;

    private RabbitTemplate rabbitTemplate;

    /** 最大重试次数。超限置 FAILED。默认 5（指数退避下约覆盖 5 次重试窗口）。 */
    @Value("${medconsult.mq.max-retry:5}")
    private int maxRetry;

    /**
     * publisher confirm 超时窗口（秒）：send 后等 broker confirm 的宽限时间。
     * <p>send 成功后消息置 SENT + nextRetryAt=now+本窗口。若窗口内 confirm 未回（broker 崩溃
     * 或网络分区），下轮 dispatch（5s 周期）扫到 nextRetryAt 已到期则重投。默认 30s，
     * 远大于正常 confirm 往返（毫秒级），避免误重投；可按部署环境调整。
     */
    @Value("${medconsult.mq.confirm-timeout-seconds:30}")
    private long confirmTimeoutSeconds;

    /**
     * 绑定 RabbitTemplate 并注册 confirm 回调。
     * <p>由 {@link MedConsultMqAutoConfiguration#messageDispatcher()} 装配时调用——
     * 取 dispatcher 依赖的 RabbitTemplate，同时把 dispatcher 自己注册为它的 ConfirmCallback。
     * 单向依赖（dispatcher 依赖 template），不构成循环。
     */
    @Autowired
    public void bindRabbitTemplate(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        // 注册 confirm 回调（AutoConfiguration 的 RabbitTemplate 不设 callback，由本类接管）
        rabbitTemplate.setConfirmCallback(this);
    }

    /**
     * 每 {@code medconsult.mq.dispatch-interval-ms}（默认 5000ms）扫描一次。
     */
    @Scheduled(fixedDelayString = "${medconsult.mq.dispatch-interval-ms:5000}")
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        // 取待投递：PENDING，或 SENT 待重试（nextRetryAt 到时）。
        // 注意：投递后处于"已 send 但未 confirm"的 SENT（nextRetryAt 未到）不会被重扫，
        // 避免对同一条消息重复 send。只有 nextRetryAt <= now（confirm 超时未回 / nack / send 失败）才重投。
        QueryWrapper<LocalMessage> qw = new QueryWrapper<>();
        qw.and(w -> w.eq("status", LocalMessage.STATUS_PENDING)
                    .or(sub -> sub.eq("status", LocalMessage.STATUS_SENT)
                                  .le("next_retry_at", now)))
          .last("LIMIT " + BATCH_SIZE);

        List<LocalMessage> pending = localMessageMapper.selectList(qw);
        if (pending.isEmpty()) {
            return;
        }
        for (LocalMessage msg : pending) {
            tryDispatch(msg);
        }
    }

    private void tryDispatch(LocalMessage msg) {
        // CorrelationData.id = LocalMessage.id，confirm 回调凭它回查更新状态
        CorrelationData cd = new CorrelationData(String.valueOf(msg.getId()));
        try {
            rabbitTemplate.send(msg.getExchange(), msg.getRoutingKey(),
                    org.springframework.amqp.core.MessageBuilder
                            .withBody(msg.getPayloadJson().getBytes())
                            .setHeader("messageNo", msg.getMessageNo())
                            .build(),
                    cd);
            // send 成功（已写入 broker TCP 缓冲）→ CAS 置 SENT，等 confirm 回调确认。
            // CAS 条件 status IN (PENDING,SENT)：若并发已把消息推进到 CONFIRMED/FAILED 终态，
            // CAS 返回 0，不覆盖终态（避免把已确认消息打回 SENT 导致无谓重投）。
            // nextRetryAt 设为 confirm 超时窗口后，窗口内未 confirm 则下轮重投。
            int updated = localMessageMapper.casUpdateStatus(
                    msg.getId(),
                    java.util.List.of(LocalMessage.STATUS_PENDING, LocalMessage.STATUS_SENT),
                    LocalMessage.STATUS_SENT,
                    LocalDateTime.now().plusSeconds(confirmTimeoutSeconds));
            if (updated == 0) {
                log.debug("消息已被并发推进到终态，跳过重投标记: {}", msg.getMessageNo());
            } else {
                log.debug("消息已投递待确认: {} -> {}/{}", msg.getMessageNo(), msg.getExchange(), msg.getRoutingKey());
            }
        } catch (Exception e) {
            // send 本身抛异常（连不上 broker / 通道关闭）→ 退避重试
            log.warn("消息投递失败（将重试）: {} retryCount={}", msg.getMessageNo(), msg.getRetryCount(), e);
            handleRetry(msg.getId());
        }
    }

    // ===== publisher confirm 回调 =====
    //
    // 并发安全：confirm() 在 RabbitMQ 的 IO 线程执行，dispatch() 在 @Scheduled 线程执行，
    // 两者可能并发更新同一条 LocalMessage。本类所有状态变更都用 CAS（状态条件 + retry_count
    // SQL 原子自增），消除"丢失更新"：终态（CONFIRMED/FAILED）不会被并发改回，
    // retry_count 自增互不覆盖。详见 LocalMessageMapper.casUpdateStatus / casRetry。

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (correlationData == null || correlationData.getId() == null) {
            // 无关联数据（非本 dispatcher 投递的消息），忽略
            return;
        }
        Long msgId;
        try {
            msgId = Long.valueOf(correlationData.getId());
        } catch (NumberFormatException e) {
            return;
        }
        if (ack) {
            // broker 确认收到并落盘 → CAS 置 CONFIRMED（终态）。
            // 仅 PENDING/SENT 可流转到 CONFIRMED；已是终态则 CAS 返回 0（幂等，无副作用）。
            localMessageMapper.casUpdateStatus(
                    msgId,
                    java.util.List.of(LocalMessage.STATUS_PENDING, LocalMessage.STATUS_SENT),
                    LocalMessage.STATUS_CONFIRMED,
                    null);
            log.debug("消息已确认: msgId={}", msgId);
        } else {
            // broker nack（如交换机不存在 / 内部错误）→ 原子自增 retry_count，下轮重投
            log.warn("消息被 broker 拒绝（nack），将重试: msgId={} cause={}", msgId, cause);
            handleRetry(msgId);
        }
    }

    // ===== 私有助手 =====

    /**
     * 处理重试（CAS 原子）：retry_count SQL 自增 +1，超 maxRetry 则置 FAILED 终态。
     * <p>用 SQL 原子自增（retry_count = retry_count + 1）而非读后写，并发下各自自增互不覆盖。
     * <p>两步：(1) 原子自增 + 置 SENT（暂用最小退避占位）；(2) 读回真实 retry_count，
     * 若超限则 CAS 推进 FAILED，否则按真实 count 修正退避 nextRetryAt。
     *
     * @param msgId 消息主键
     */
    private void handleRetry(Long msgId) {
        java.util.List<String> active = java.util.List.of(LocalMessage.STATUS_PENDING, LocalMessage.STATUS_SENT);
        // (1) 原子自增 retry_count +1 并置 SENT（暂用最小退避，下一步按真实 count 修正）
        int updated = localMessageMapper.casRetry(msgId, active, LocalMessage.STATUS_SENT,
                LocalDateTime.now().plusSeconds(nextRetryBackoff(1)));
        if (updated == 0) {
            // 已被并发推进到终态，无需重试
            return;
        }
        // (2) 读回自增后的 retry_count：超限置 FAILED，否则修正退避时间
        LocalMessage after = localMessageMapper.selectById(msgId);
        if (after == null || after.getRetryCount() == null) {
            return;
        }
        int rc = after.getRetryCount();
        if (rc >= maxRetry) {
            // 超限 → CAS 推进 FAILED（仅 SENT 可流转，防并发误判）
            localMessageMapper.casUpdateStatus(
                    msgId, java.util.List.of(LocalMessage.STATUS_SENT), LocalMessage.STATUS_FAILED, null);
            log.warn("消息重试超限置 FAILED: msgId={} retryCount={}", msgId, rc);
        } else {
            // 修正退避时间为 2^rc 秒（占位的最小值换成真实指数退避）
            localMessageMapper.casUpdateStatus(
                    msgId, java.util.List.of(LocalMessage.STATUS_SENT), LocalMessage.STATUS_SENT,
                    LocalDateTime.now().plusSeconds(nextRetryBackoff(rc)));
        }
    }

    /** 指数退避秒数：2^retry（2,4,8,16,32...） */
    private static long nextRetryBackoff(int retryCount) {
        return 1L << retryCount;
    }
}
