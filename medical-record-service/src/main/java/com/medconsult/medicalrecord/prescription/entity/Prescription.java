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
 * <p>状态机（8 态，本批第 1 批只实现前 4 态流转）：
 * <pre>
 * DRAFT ──submit──▶ PENDING_REVIEW ──approve──▶ APPROVED
 *                       │
 *                     reject
 *                       ▼
 *                   REJECTED
 *
 * （第 2 批补：APPROVED ──pay──▶ PAID ──dispense──▶ DISPENSED ──complete──▶ COMPLETED
 *            APPROVED/PAID/CANCELLED 退方分支）
 * </pre>
 *
 * <p>本批合法转移：DRAFT→PENDING_REVIEW（submit）、PENDING_REVIEW→APPROVED|REJECTED（review）。
 * 其他状态值（PAID/DISPENSED/COMPLETED/CANCELLED）作为字面量预留，但本批无对应接口触发。
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

    /** 支付状态：UNPAID / PAID / REFUNDED（本批创建时初始 UNPAID） */
    private String paymentStatus;

    /** 处方来源：OUTPATIENT / INPATIENT / EMERGENCY */
    private String source;
}
