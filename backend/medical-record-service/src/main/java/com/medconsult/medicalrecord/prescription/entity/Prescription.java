package com.medconsult.medicalrecord.prescription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方主表（对应《修改建议》§2.1 表 1 prescription，从 medical_record 剥离独立成表）。
 *
 * <p>状态机（当前主流程跳过审方，历史 DRAFT/PENDING_REVIEW 仍兼容处理）：
 * <pre>
 * create ──▶ APPROVED ──pay──▶ PAID ──dispense──▶ DISPENSED ──complete──▶ COMPLETED
 *              │   ▲
 *              │   └── 历史 PENDING_REVIEW ──approve
 *              └────── cancel
 *
 * 历史兼容：DRAFT ──submit──▶ PENDING_REVIEW ──reject──▶ REJECTED
 * </pre>
 */
@Getter
@Setter
@TableName("prescription")
public class Prescription extends BaseEntity {

    /** 处方编号，如 RX202607060001（业务可读，对外暴露） */
    private String prescriptionNo;

    /** 关联病历 ID（BIGINT 主键，跨服务内 medical_record.id） */
    private Long recordId;

    /** 患者 ID（BIGINT，与病历同款哈希策略） */
    private Long patientId;

    /** 开方医生 ID（BIGINT） */
    private Long doctorId;

    /** 开方科室 ID（BIGINT，可空） */
    private Long departmentId;

    /** 状态机见类注释（DRAFT/PENDING_REVIEW/APPROVED/REJECTED/PAID/DISPENSED/COMPLETED/CANCELLED） */
    private String status;

    /** 审方药师 ID（review 时回填） */
    private Long pharmacyPharmacistId;

    /** 审方时间 */
    private LocalDateTime reviewedAt;

    /** 审方意见（approve/reject 均可填） */
    private String reviewComment;

    /** 驳回原因（reject 时填） */
    private String rejectReason;

    /** 处方总金额（开方时按明细 subtotal 累加） */
    private BigDecimal totalFee;

    /** 实付金额（pay 时填，本批不用） */
    private BigDecimal paidAmount;

    /** 支付单号（外部支付系统回传，pay 时回填，便于对账/退款） */
    private String paymentNo;

    /** 支付状态：UNPAID / PAID / REFUNDED（本批创建时初始 UNPAID） */
    private String paymentStatus;

    /** 处方来源：OUTPATIENT / INPATIENT / EMERGENCY */
    private String source;
}
