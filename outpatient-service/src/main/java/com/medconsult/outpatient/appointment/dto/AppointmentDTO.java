package com.medconsult.outpatient.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预约挂号相关请求/响应 DTO（对齐《接口文档》§2.5）。
 *
 * <p>{@code appointmentId} 实为 {@code appointment_no}（业务可读编号）。
 * {@code patientId}/{@code scheduleId} 接口文档示例传业务编号串，service 层解析为 BIGINT 主键。
 */
public class AppointmentDTO {

    // ===== §2.5.1 创建预约 =====

    @Data
    public static class CreateRequest {
        /** 患者编号 patient_no（如 P202607060001） */
        @NotBlank(message = "患者编号不能为空")
        private String patientId;
        /** 排班编号 schedule_no（如 S202607080001） */
        @NotBlank(message = "排班编号不能为空")
        private String scheduleId;
        private String visitReason;
        /** 预约来源：MOBILE_APP / OFFICE_WINDOW / SELF_SERVICE（不传默认 MOBILE_APP） */
        @Pattern(regexp = "^$|^(MOBILE_APP|OFFICE_WINDOW|SELF_SERVICE)$",
                message = "预约来源须为 MOBILE_APP / OFFICE_WINDOW / SELF_SERVICE")
        private String source;
    }

    /** §2.5.1 创建响应 */
    public record CreateResponse(
            String appointmentId,     // appointment_no
            int queueNo,
            BigDecimal fee,
            String paymentStatus,
            String appointmentStatus
    ) {}

    // ===== §2.5.2 详情 =====

    /** §2.5.2 详情响应（含患者名/医生名/科室名，业务层组装） */
    public record DetailResponse(
            String appointmentId,     // appointment_no
            String patientName,
            String doctorName,
            String departmentName,
            LocalDate appointmentDate,
            String period,
            int queueNo,
            String paymentStatus,
            String appointmentStatus
    ) {}

    // ===== §2.5.3 列表 =====

    /** §2.5.3 预约列表 item */
    public record ListItem(
            String appointmentId,     // appointment_no
            String departmentName,
            String doctorName,
            LocalDate appointmentDate,
            String appointmentStatus
    ) {}

    // ===== §2.5.4 取消 =====

    @Data
    public static class CancelRequest {
        private String cancelReason;
        /** 取消操作人类型：PATIENT / DOCTOR / ADMIN */
        private String operatorType;
    }

    /** §2.5.4 取消响应 */
    public record CancelResponse(
            String appointmentId,     // appointment_no
            String appointmentStatus,
            int releasedQuota
    ) {}

    // ===== §2.5.5 支付状态 =====

    @Data
    public static class PaymentUpdateRequest {
        /** UNPAID / PAID / REFUNDING / REFUNDED */
        @NotBlank(message = "支付状态不能为空")
        @Pattern(regexp = "^(UNPAID|PAID|REFUNDING|REFUNDED)$",
                message = "支付状态须为 UNPAID / PAID / REFUNDING / REFUNDED")
        private String paymentStatus;
        private String paymentNo;
        private BigDecimal paidAmount;
    }

    /** §2.5.5 支付状态更新响应 */
    public record PaymentResponse(
            String appointmentId,     // appointment_no
            String paymentStatus
    ) {}

    // ===== §2.5.6 就诊状态 =====

    @Data
    public static class StatusUpdateRequest {
        /** BOOKED / CANCELLED / CHECKED_IN / IN_PROGRESS / COMPLETED / NO_SHOW */
        @NotBlank(message = "预约状态不能为空")
        @Pattern(regexp = "^(BOOKED|CANCELLED|CHECKED_IN|IN_PROGRESS|COMPLETED|NO_SHOW)$",
                message = "预约状态须为 BOOKED / CANCELLED / CHECKED_IN / IN_PROGRESS / COMPLETED / NO_SHOW")
        private String appointmentStatus;
        private String remark;
    }

    /** §2.5.6 就诊状态更新响应 */
    public record StatusResponse(
            String appointmentId,     // appointment_no
            String appointmentStatus
    ) {}
}
