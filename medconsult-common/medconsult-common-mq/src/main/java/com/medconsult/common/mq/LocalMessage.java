package com.medconsult.common.mq;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 本地消息表实体（架构文档 §6.1 / §3.2）。
 *
 * <p><b>不继承 BaseEntity</b>——这是流水表（只追加 + 状态更新），无逻辑删除、无 deleted 字段
 * （《修改建议》§5.1：流水表 drug_stock_flow / audit_log / login_log / ai_call_log 同理）。
 * 自动填充仅 updated_at（每次重试刷新），created_at 由业务写入。
 *
 * <p>用途：业务写 + 消息入队在同一本地事务，事务提交后由 {@link MessageDispatcher}
 * 扫描 PENDING 投递 RabbitMQ，消费者以 {@link #messageNo} 做幂等去重。
 *
 * <p>状态机：PENDING → SENT → CONFIRMED；失败退避重试 SENT → SENT...，超上限 FAILED 进 DLQ。
 */
@Getter
@Setter
@TableName("local_message")
public class LocalMessage {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务唯一键：消费者幂等去重用（如 处方号-事件类型）。生产端须保证全局唯一。 */
    private String messageNo;

    /** 目标交换机 */
    private String exchange;

    /** 路由键 */
    private String routingKey;

    /** 消息载荷（JSON 字符串）。消费者反序列化用。 */
    private String payloadJson;

    /** 状态：PENDING / SENT / CONFIRMED / FAILED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（退避调度用） */
    private LocalDateTime nextRetryAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间（每次状态变更刷新） */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;

    // ===== 状态常量 =====
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 业务便捷构造：创建一条待投递消息。
     */
    public static LocalMessage of(String exchange, String messageNo, String routingKey, String payloadJson) {
        LocalMessage m = new LocalMessage();
        m.exchange = exchange;
        m.messageNo = messageNo;
        m.routingKey = routingKey;
        m.payloadJson = payloadJson;
        m.status = STATUS_PENDING;
        m.retryCount = 0;
        m.createdAt = LocalDateTime.now();
        return m;
    }
}
