package com.medconsult.outpatient.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预约挂号表（对应《数据库设计文档》§2.6 appointment 表，含《修改建议》§5.1 补充字段）。
 *
 * <p>保存患者预约记录，管理支付状态、签到状态和就诊状态流转
 * （《需求文档》§4.1.3）。
 *
 * <p>状态机（接口文档 §5）：
 * <ul>
 *   <li>appointmentStatus: BOOKED → CHECKED_IN → IN_PROGRESS → COMPLETED；或 → NO_SHOW；或 → CANCELLED</li>
 *   <li>paymentStatus: UNPAID → PAID → REFUNDING → REFUNDED</li>
 * </ul>
 */
@Getter
@Setter
@TableName("appointment")
public class Appointment extends BaseEntity {

    /** 预约编号，如 A202607060001（业务可读，对外暴露） */
    private String appointmentNo;

    /** 患者 ID（BIGINT 主键） */
    private Long patientId;

    /** 患者编号（冗余自 patient-service 的 patient_no，便于按业务编号查询/过滤） */
    private String patientNo;

    /** 医生 ID（BIGINT 主键） */
    private Long doctorId;

    /** 科室 ID（BIGINT 主键） */
    private Long departmentId;

    /** 排班 ID（BIGINT 主键） */
    private Long scheduleId;

    /** 预约日期（冗余自排班，便于查询） */
    private LocalDate appointmentDate;

    /** 预约时段（冗余自排班） */
    private String period;

    /** 就诊序号（该排班内递增） */
    private Integer queueNo;

    /** 挂号费（冗余自排班） */
    private BigDecimal fee;

    /** 支付状态：UNPAID / PAID / REFUNDING / REFUNDED */
    private String paymentStatus;

    /** 预约状态：BOOKED / CANCELLED / CHECKED_IN / IN_PROGRESS / COMPLETED / NO_SHOW */
    private String appointmentStatus;

    /** 取消原因 */
    private String cancelReason;

    /** 就诊原因 */
    private String visitReason;

    /** 预约来源：MOBILE_APP / OFFICE_WINDOW / SELF_SERVICE（《修改建议》§5.1 补） */
    private String source;

    /** 取消操作人类型：PATIENT / DOCTOR / ADMIN（《修改建议》§5.1 补） */
    private String cancelOperatorType;

    /** 取消操作人 ID（《修改建议》§5.1 补） */
    private Long cancelOperatorId;

    /** 支付单号（《修改建议》§5.1 补） */
    private String paymentNo;

    /** 实付金额（《修改建议》§5.1 补） */
    private BigDecimal paidAmount;
}
