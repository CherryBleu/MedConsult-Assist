package com.medconsult.outpatient.schedule.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 排班模板相关 DTO（后端修改.md #16 默认排班）。
 *
 * <p>doctorId/departmentId 入参为业务编号（doctor_no/department_no），与 ScheduleDTO 一致，
 * service 层反查 BIGINT 主键落库。
 */
public class ScheduleTemplateDTO {

    /** 创建模板请求 */
    @Data
    @Schema(description = "创建排班模板请求")
    public static class CreateRequest {
        @NotBlank(message = "医生编号不能为空")
        @Schema(description = "医生编号 doctor_no")
        private String doctorId;

        @NotBlank(message = "科室编号不能为空")
        @Schema(description = "科室编号 department_no")
        private String departmentId;

        @NotNull(message = "周几不能为空")
        @Min(value = 1, message = "周几需为 1-7")
        @Max(value = 7, message = "周几需为 1-7")
        @Schema(description = "周几：1=周一 ... 7=周日")
        private Integer dayOfWeek;

        @NotBlank(message = "时段不能为空")
        @Pattern(regexp = "^(MORNING|AFTERNOON|EVENING|FULL_DAY)$", message = "时段须为 MORNING/AFTERNOON/EVENING/FULL_DAY")
        @Schema(description = "时段")
        private String period;

        @Schema(description = "开始时间")
        private LocalTime startTime;

        @Schema(description = "结束时间")
        private LocalTime endTime;

        @NotNull(message = "总号源不能为空")
        @Positive(message = "总号源必须大于 0")
        @Schema(description = "总号源")
        private Integer totalQuota;

        @Schema(description = "挂号费")
        private BigDecimal registrationFee;

        @Schema(description = "是否启用（默认 1）")
        private Integer enabled;
    }

    /** 更新模板请求（doctorId 不可改，对齐排班 update 决策） */
    @Data
    @Schema(description = "更新排班模板请求")
    public static class UpdateRequest {
        @Schema(description = "科室编号")
        private String departmentId;
        @Min(1) @Max(7) @Schema(description = "周几") private Integer dayOfWeek;
        @Pattern(regexp = "^(MORNING|AFTERNOON|EVENING|FULL_DAY)$") @Schema(description = "时段") private String period;
        @Schema(description = "开始时间") private LocalTime startTime;
        @Schema(description = "结束时间") private LocalTime endTime;
        @Positive @Schema(description = "总号源") private Integer totalQuota;
        @Schema(description = "挂号费") private BigDecimal registrationFee;
        @Schema(description = "是否启用") private Integer enabled;
    }

    /** 模板列表项（doctorName/departmentName 业务层组装） */
    @Schema(description = "排班模板列表项")
    public record TemplateListItem(
            @Schema(description = "模板编号") String templateId,           // template_no
            @Schema(description = "医生编号") String doctorId,             // doctor_no
            @Schema(description = "医生姓名") String doctorName,
            @Schema(description = "科室编号") String departmentId,         // department_no
            @Schema(description = "科室名称") String departmentName,
            @Schema(description = "周几 1-7") Integer dayOfWeek,
            @Schema(description = "时段") String period,
            @Schema(description = "开始时间") LocalTime startTime,
            @Schema(description = "结束时间") LocalTime endTime,
            @Schema(description = "总号源") Integer totalQuota,
            @Schema(description = "挂号费") BigDecimal registrationFee,
            @Schema(description = "是否启用") boolean enabled
    ) {}

    /** 创建/更新响应 */
    @Schema(description = "模板保存响应")
    public record SaveResponse(
            @Schema(description = "模板编号") String templateId
    ) {}

    /** 一键生成请求 */
    @Data
    @Schema(description = "一键生成排班请求")
    public static class ApplyRequest {
        @NotNull(message = "起始日期不能为空")
        @Schema(description = "起始日期（含，YYYY-MM-DD）")
        private LocalDate startDate;

        @NotNull(message = "周数不能为空")
        @Min(value = 1, message = "周数至少 1")
        @Max(value = 8, message = "周数最多 8")
        @Schema(description = "生成周数（1-8）")
        private Integer weeks;

        @Schema(description = "限定医生编号（不传则处理全部启用模板）")
        private String doctorId;
    }

    /** 一键生成响应 */
    @Schema(description = "一键生成排班响应")
    public record ApplyResponse(
            @Schema(description = "新生成排班数") int generated,
            @Schema(description = "跳过（已存在）数") int skipped
    ) {}
}
