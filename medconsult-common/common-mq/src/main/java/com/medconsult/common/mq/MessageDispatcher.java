package com.medconsult.common.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 投递成功标记 SENT，等待 publisher confirm 回调后置 CONFIRMED（简化版：投递即 CONFIRMED）。
 *
 * <p><b>不选主</b>（§6.3 关键）：每个实例都跑本调度器，多实例同时扫描会重复投递，
 * 重复由 {@link IdempotentConsumer} 在消费端吸收。这比引入 ShedLock 选主更简单，
 * 且消费者幂等本就是必须项（防 MQ 自身重投）。
 *
 * <p>退避重试：失败的 SENT 消息按 nextRetryAt 重新投递，retryCount++，超 {@link #maxRetry}
 * 置 FAILED，进 DLQ（由 RabbitMQ 死信队列配置处理，本类只标记终态）。
 */
public class MessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    /** 单次扫描批量上限 */
    private static final int BATCH_SIZE = 100;

    @Autowired
    private LocalMessageMapper localMessageMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /** 最大重试次数。超限置 FAILED。默认 5（指数退避下约覆盖 5 次重试窗口）。 */
    @Value("${medconsult.mq.max-retry:5}")
    private int maxRetry;

    /**
     * 每 {@code medconsult.mq.dispatch-interval-ms}（默认 5000ms）扫描一次。
     */
    @Scheduled(fixedDelayString = "${medconsult.mq.dispatch-interval-ms:5000}")
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        // 取待投递：PENDING，或 SENT 待重试（nextRetryAt 到时）
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
        try {
            rabbitTemplate.send(msg.getExchange(), msg.getRoutingKey(),
                    org.springframework.amqp.core.MessageBuilder
                            .withBody(msg.getPayloadJson().getBytes())
                            .setHeader("messageNo", msg.getMessageNo())
                            .build());
            // 投递成功 → CONFIRMED（简化版；生产可启用 publisher confirm 异步置 CONFIRMED）
            msg.setStatus(LocalMessage.STATUS_CONFIRMED);
            msg.setUpdatedAt(LocalDateTime.now());
            localMessageMapper.updateById(msg);
            log.debug("消息已投递: {} -> {}/{}", msg.getMessageNo(), msg.getExchange(), msg.getRoutingKey());
        } catch (Exception e) {
            log.warn("消息投递失败（将重试）: {} retryCount={}", msg.getMessageNo(), msg.getRetryCount(), e);
            msg.setRetryCount(msg.getRetryCount() + 1);
            if (msg.getRetryCount() >= maxRetry) {
                msg.setStatus(LocalMessage.STATUS_FAILED);
            } else {
                msg.setStatus(LocalMessage.STATUS_SENT);
            }
            // 指数退避：2^retry 秒后重试
            long backoffSec = (1L << msg.getRetryCount()); // 2,4,8,16,32...
            msg.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSec));
            msg.setUpdatedAt(LocalDateTime.now());
            localMessageMapper.updateById(msg);
        }
    }
}
