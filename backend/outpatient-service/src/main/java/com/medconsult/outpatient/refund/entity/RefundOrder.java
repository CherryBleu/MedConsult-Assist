package com.medconsult.outpatient.refund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 挂号退款单。
 *
 * <p>当前仅接入 MOCK 渠道；appointment_id 唯一约束作为幂等最终兜底。
 */
@Getter
@Setter
@TableName("refund_order")
public class RefundOrder extends BaseEntity {

    /** 退款单号，如 Rxxxx（业务可读，对外暴露）。 */
    private String refundNo;

    /** 预约主键 ID。 */
    private Long appointmentId;

    /** 预约编号。 */
    private String appointmentNo;

    /** 患者主键 ID。 */
    private Long patientId;

    /** 退款金额。 */
    private BigDecimal refundAmount;

    /** 退款提供方：MOCK / WECHAT / ALIPAY。 */
    private String provider;

    /** 退款方式：ORIGINAL / MANUAL。 */
    private String channel;

    /** 客户端幂等键。 */
    private String idempotencyKey;

    /** 退款原因。 */
    private String reason;

    /** 失败原因。 */
    private String failureReason;

    /** 退款状态：PROCESSING / SUCCEEDED / FAILED。 */
    private String status;

    /** 发起时间。 */
    private LocalDateTime requestedAt;

    /** 处理时间。 */
    private LocalDateTime processedAt;

    /** 成功时间。 */
    private LocalDateTime succeededAt;
}
