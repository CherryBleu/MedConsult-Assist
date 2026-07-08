package com.medconsult.outpatient.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 医生排班相关请求/响应 DTO（对齐《接口文档》§2.4）。
 *
 * <p>{@code scheduleId} 实为 {@code schedule_no}（业务可读编号），与接口文档字段命名一致。
 */
public class ScheduleDTO {

    // ===== §2.4.1 创建排班 =====

    @Data
    public static class CreateRequest {
        /** 医生编号 doctor_no */
        @NotBlank(message = "医生编号不能为空")
        private String doctorId;
        /** 科室编号 department_no */
        @NotBlank(message = "科室编号不能为空")
        private String departmentId;
        @NotNull(message = "出诊日期不能为空")
        private LocalDate scheduleDate;
        /** MORNING / AFTERNOON / EVENING / FULL_DAY */
        @NotBlank(message = "时段不能为空")
        @Pattern(regexp = "^(MORNING|AFTERNOON|EVENING|FULL_DAY)$",
                message = "时段须为 MORNING / AFTERNOON / EVENING / FULL_DAY")
        private String period;
        private LocalTime startTime;
        private LocalTime endTime;
        @NotNull(message = "总号源不能为空")
        private Integer totalQuota;
        private BigDecimal registrationFee;
    }

    /** §2.4.1 创建响应 */
    public record CreateResponse(
            String scheduleId,        // schedule_no
            int remainingQuota,
            String status
    ) {}

    // ===== §2.4.2 列表 =====

    /** §2.4.2 排班列表 item（含医生名/科室名，业务层组装） */
    public record ListItem(
            String scheduleId,        // schedule_no
            String doctorName,
            String departmentName,
            LocalDate scheduleDate,
            String period,
            int totalQuota,
            int bookedQuota,
            int remainingQuota,
            String status
    ) {}

    // ===== §2.4.3 可预约号源 =====

    /** §2.4.3 可预约号源 item */
    public record AvailableItem(
            String scheduleId,        // schedule_no
            String doctorId,          // doctor_no
            String doctorName,
            String period,
            int remainingQuota,
            BigDecimal registrationFee
    ) {}

    // ===== §2.4.4 状态变更 =====

    @Data
    public static class StatusUpdateRequest {
        /** AVAILABLE / FULL / SUSPENDED / CANCELLED */
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "^(AVAILABLE|FULL|SUSPENDED|CANCELLED)$",
                message = "排班状态须为 AVAILABLE / FULL / SUSPENDED / CANCELLED")
        private String status;
        private String reason;
    }

    /** §2.4.4 状态变更响应 */
    public record StatusResponse(
            String scheduleId,        // schedule_no
            String status,
            int notifiedAppointments  // SUSPENDED 时返回受影响未完成预约数
    ) {}
}
