package com.medconsult.outpatient.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Schema(description = "创建预约请求")
    public static class CreateRequest {
        /** 患者编号 patient_no（如 P202607060001） */
        @NotBlank(message = "患者编号不能为空")
        @Schema(description = "患者编号") private String patientId;
        /** 排班编号 schedule_no（如 S202607080001） */
        @NotBlank(message = "排班编号不能为空")
        @Schema(description = "排班编号") private String scheduleId;
        @Schema(description = "就诊原因") private String visitReason;
        /** 预约来源：MOBILE_APP / OFFICE_WINDOW / SELF_SERVICE（不传默认 MOBILE_APP） */
        @Pattern(regexp = "^$|^(MOBILE_APP|OFFICE_WINDOW|SELF_SERVICE)$",
                message = "预约来源须为 MOBILE_APP / OFFICE_WINDOW / SELF_SERVICE")
        @Schema(description = "来源：MOBILE_APP / OFFICE_WINDOW / SELF_SERVICE") private String source;
    }

    /** §2.5.1 创建响应 */
    @Schema(description = "创建预约响应")
    public record CreateResponse(
            @Schema(description = "预约编号") String appointmentId,     // appointment_no
            @Schema(description = "就诊序号") int queueNo,
            @Schema(description = "挂号费") BigDecimal fee,
            @Schema(description = "支付状态：UNPAID / PAID / REFUNDING / REFUNDED") String paymentStatus,
            @Schema(description = "预约状态：BOOKED / CANCELLED / CHECKED_IN / IN_PROGRESS / COMPLETED / NO_SHOW") String appointmentStatus
    ) {}

    // ===== §2.5.2 详情 =====

    /** §2.5.2 详情响应（含患者名/医生名/科室名，业务层组装） */
    @Schema(description = "预约详情响应")
    public record DetailResponse(
            @Schema(description = "预约编号") String appointmentId,     // appointment_no
            @Schema(description = "患者姓名") String patientName,
            @Schema(description = "医生姓名") String doctorName,
            @Schema(description = "科室名称") String departmentName,
            @Schema(description = "就诊日期") LocalDate appointmentDate,
            @Schema(description = "时段") String period,
            @Schema(description = "就诊序号") int queueNo,
            @Schema(description = "挂号费") BigDecimal fee,
            @Schema(description = "支付状态") String paymentStatus,
            @Schema(description = "预约状态") String appointmentStatus,
            @Schema(description = "就诊原因") String visitReason,
            @Schema(description = "取消原因") String cancelReason,
            @Schema(description = "创建时间") java.time.OffsetDateTime createdAt
    ) {}

    // ===== §2.5.3 列表 =====

    /** §2.5.3 预约列表 item */
    @Schema(description = "预约列表项")
    public record ListItem(
            @Schema(description = "预约编号") String appointmentId,     // appointment_no
            @Schema(description = "患者编号") String patientNo,         // patient_no（冗余，便于识别患者）
            @Schema(description = "科室名称") String departmentName,
            @Schema(description = "医生姓名") String doctorName,
            @Schema(description = "就诊日期") LocalDate appointmentDate,
            @Schema(description = "时段：MORNING/AFTERNOON/EVENING") String period,
            @Schema(description = "就诊序号") Integer queueNo,
            @Schema(description = "挂号费") BigDecimal fee,
            @Schema(description = "支付状态：UNPAID/PAID/REFUNDING/REFUNDED") String paymentStatus,
            @Schema(description = "预约状态") String appointmentStatus,
            @Schema(description = "就诊原因") String visitReason
    ) {}

    // ===== §2.5.4 取消 =====

    @Data
    @Schema(description = "取消预约请求")
    public static class CancelRequest {
        @Schema(description = "取消原因") private String cancelReason;
        /** 取消操作人类型：PATIENT / DOCTOR / ADMIN */
        @Schema(description = "操作人类型") private String operatorType;
    }

    /** §2.5.4 取消响应 */
    @Schema(description = "取消预约响应")
    public record CancelResponse(
            @Schema(description = "预约编号") String appointmentId,     // appointment_no
            @Schema(description = "预约状态") String appointmentStatus,
            @Schema(description = "释放号源数") int releasedQuota
    ) {}

    // ===== §2.5.5 支付状态 =====

    @Data
    @Schema(description = "更新支付状态请求")
    public static class PaymentUpdateRequest {
        /** UNPAID / PAID / REFUNDING / REFUNDED */
        @NotBlank(message = "支付状态不能为空")
        @Pattern(regexp = "^(UNPAID|PAID|REFUNDING|REFUNDED)$",
                message = "支付状态须为 UNPAID / PAID / REFUNDING / REFUNDED")
        @Schema(description = "支付状态：UNPAID / PAID / REFUNDING / REFUNDED") private String paymentStatus;
        @Schema(description = "支付流水号") private String paymentNo;
        @Schema(description = "支付金额") private BigDecimal paidAmount;
    }

    /** §2.5.5 支付状态更新响应 */
    @Schema(description = "更新支付状态响应")
    public record PaymentResponse(
            @Schema(description = "预约编号") String appointmentId,     // appointment_no
            @Schema(description = "支付状态") String paymentStatus
    ) {}

    // ===== §2.5.6 就诊状态 =====

    @Data
    @Schema(description = "更新就诊状态请求")
    public static class StatusUpdateRequest {
        /** BOOKED / CANCELLED / CHECKED_IN / IN_PROGRESS / COMPLETED / NO_SHOW */
        @NotBlank(message = "预约状态不能为空")
        @Pattern(regexp = "^(BOOKED|CANCELLED|CHECKED_IN|IN_PROGRESS|COMPLETED|NO_SHOW)$",
                message = "预约状态须为 BOOKED / CANCELLED / CHECKED_IN / IN_PROGRESS / COMPLETED / NO_SHOW")
        @Schema(description = "目标状态：BOOKED / CANCELLED / CHECKED_IN / IN_PROGRESS / COMPLETED / NO_SHOW") private String appointmentStatus;
        @Schema(description = "备注") private String remark;
    }

    /** §2.5.6 就诊状态更新响应 */
    @Schema(description = "更新就诊状态响应")
    public record StatusResponse(
            @Schema(description = "预约编号") String appointmentId,     // appointment_no
            @Schema(description = "当前状态") String appointmentStatus
    ) {}

    // ===== §6.2 退款（POST /appointments/{id}/refund）=====

    /** 退款请求 */
    @Data
    @Schema(description = "退款请求")
    public static class RefundRequest {
        @Size(max = 255, message = "退款原因不能超过 255 字")
        @Schema(description = "退款原因")
        private String reason;

        @Schema(description = "操作人类型：PATIENT/DOCTOR/ADMIN（默认 PATIENT）")
        private String operatorType;
    }

    /** 退款响应 */
    @Schema(description = "退款响应")
    public record RefundResponse(
            @Schema(description = "退款单号") String refundNo,
            @Schema(description = "预约编号") String appointmentId,     // appointment_no
            @Schema(description = "退款金额") java.math.BigDecimal refundAmount,
            @Schema(description = "支付状态") String paymentStatus      // REFUNDED
    ) {}
}
