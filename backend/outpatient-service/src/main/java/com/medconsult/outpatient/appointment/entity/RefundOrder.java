package com.medconsult.outpatient.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 退款单实体（对应《修改建议》§6.2 refund_order 表）。
 *
 * <p>记录每笔预约退款。金额来源 appointment.paid_amount（兜底 fee）。
 * 当前阶段仅全退（refund_type=FULL，同步置 SUCCESS）；表结构预留 refund_type/refund_amount
 * 为未来部分退/异步对账留口子。
 */
@Getter
@Setter
@TableName("refund_order")
public class RefundOrder extends BaseEntity {

    /** 退款单号（业务可读，如 R + 雪花 base36） */
    private String refundNo;

    /** 关联预约 ID */
    private Long appointmentId;

    /** 关联预约编号（冗余，便于检索） */
    private String appointmentNo;

    /** 原支付单号（追溯） */
    private String paymentNo;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款类型：FULL / PARTIAL（当前仅 FULL） */
    private String refundType;

    /** 退款状态：SUCCESS / PROCESSING / FAILED（当前同步全退即 SUCCESS） */
    private String status;

    /** 退款原因 */
    private String reason;

    /** 操作人类型：PATIENT / DOCTOR / ADMIN */
    private String operatorType;

    /** 操作人 ID */
    private Long operatorId;
}
