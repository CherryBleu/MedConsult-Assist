package com.medconsult.outpatient.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "创建排班请求")
    public static class CreateRequest {
        /** 医生编号 doctor_no */
        @NotBlank(message = "医生编号不能为空")
        @Schema(description = "医生编号") private String doctorId;
        /** 科室编号 department_no */
        @NotBlank(message = "科室编号不能为空")
        @Schema(description = "科室编号") private String departmentId;
        @NotNull(message = "出诊日期不能为空")
        @Schema(description = "排班日期") private LocalDate scheduleDate;
        /** MORNING / AFTERNOON / EVENING / FULL_DAY */
        @NotBlank(message = "时段不能为空")
        @Pattern(regexp = "^(MORNING|AFTERNOON|EVENING|FULL_DAY)$",
                message = "时段须为 MORNING / AFTERNOON / EVENING / FULL_DAY")
        @Schema(description = "时段：MORNING / AFTERNOON / EVENING / FULL_DAY") private String period;
        @Schema(description = "开始时间") private LocalTime startTime;
        @Schema(description = "结束时间") private LocalTime endTime;
        @NotNull(message = "总号源不能为空")
        @Schema(description = "总号源数") private Integer totalQuota;
        @Schema(description = "挂号费") private BigDecimal registrationFee;
    }

    /** §2.4.1 创建响应 */
    @Schema(description = "创建排班响应")
    public record CreateResponse(
            @Schema(description = "排班编号") String scheduleId,        // schedule_no
            @Schema(description = "剩余号源") int remainingQuota,
            @Schema(description = "状态：AVAILABLE / FULL / SUSPENDED / CANCELLED") String status
    ) {}

    // ===== §2.4.2 列表 =====

    /** §2.4.2 排班列表 item（含医生名/科室名，业务层组装） */
    @Schema(description = "排班列表项")
    public record ListItem(
            @Schema(description = "排班编号") String scheduleId,        // schedule_no
            @Schema(description = "医生姓名") String doctorName,
            @Schema(description = "科室名称") String departmentName,
            @Schema(description = "排班日期") LocalDate scheduleDate,
            @Schema(description = "时段") String period,
            @Schema(description = "开始时间") LocalTime startTime,
            @Schema(description = "结束时间") LocalTime endTime,
            @Schema(description = "总号源") int totalQuota,
            @Schema(description = "已预约号源") int bookedQuota,
            @Schema(description = "剩余号源") int remainingQuota,
            @Schema(description = "挂号费") BigDecimal registrationFee,
            @Schema(description = "状态") String status
    ) {}

    // ===== §2.4.3 可预约号源 =====

    /** §2.4.3 可预约号源 item */
    @Schema(description = "可预约号源")
    public record AvailableItem(
            @Schema(description = "排班编号") String scheduleId,        // schedule_no
            @Schema(description = "医生编号") String doctorId,          // doctor_no
            @Schema(description = "医生姓名") String doctorName,
            @Schema(description = "时段") String period,
            @Schema(description = "剩余号源") int remainingQuota,
            @Schema(description = "挂号费") BigDecimal registrationFee
    ) {}

    // ===== §2.4.4 状态变更 =====

    @Data
    @Schema(description = "更新排班状态请求")
    public static class StatusUpdateRequest {
        /** AVAILABLE / FULL / SUSPENDED / CANCELLED */
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "^(AVAILABLE|FULL|SUSPENDED|CANCELLED)$",
                message = "排班状态须为 AVAILABLE / FULL / SUSPENDED / CANCELLED")
        @Schema(description = "目标状态：AVAILABLE / FULL / SUSPENDED / CANCELLED") private String status;
        @Schema(description = "变更原因") private String reason;
    }

    /** §2.4.4 状态变更响应 */
    @Schema(description = "更新排班状态响应")
    public record StatusResponse(
            @Schema(description = "排班编号") String scheduleId,        // schedule_no
            @Schema(description = "当前状态") String status,
            @Schema(description = "受影响预约数") int notifiedAppointments  // SUSPENDED 时返回受影响未完成预约数
    ) {}
}
