package com.medconsult.outpatient.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 医生相关 DTO（对齐《接口文档》§2.3.2 + 管理员维护接口）。
 *
 * <p>{@code departmentName} 由 service 层业务组装（不跨表 join，二次查询）。
 */
public class DoctorDTO {

    /** §2.3.2 医生列表 item */
    @Schema(description = "医生列表项")
    public record ListItem(
            @Schema(description = "医生编号") String doctorId,          // doctor_no（业务可读）
            @Schema(description = "医生姓名") String doctorName,
            @Schema(description = "科室编号") String departmentId,      // department_no
            @Schema(description = "科室名称") String departmentName,    // 业务层组装（非 join）
            @Schema(description = "职称") String title,
            @Schema(description = "专长列表") List<String> specialties,
            @Schema(description = "是否启用") boolean enabled
    ) {}

    /** 新增医生请求体（管理员） */
    @Schema(description = "新增医生请求")
    public record CreateRequest(
            @Schema(description = "医生姓名") @NotBlank(message = "医生姓名不能为空") @Size(max = 50) String name,
            @Schema(description = "所属科室编号 department_no") @NotBlank(message = "科室不能为空") String departmentId,
            @Schema(description = "职称") @Size(max = 50) String title,
            @Schema(description = "擅长方向（逗号分隔字符串，后端解析为 JSON 数组）") @Size(max = 500) String specialties,
            @Schema(description = "医生简介") @Size(max = 1000) String introduction,
            @Schema(description = "是否启用（默认 true）") Boolean enabled
    ) {}

    /** 更新医生请求体（部分字段） */
    @Schema(description = "更新医生请求")
    public record UpdateRequest(
            @Schema(description = "医生姓名") @Size(max = 50) String name,
            @Schema(description = "所属科室编号 department_no") String departmentId,
            @Schema(description = "职称") @Size(max = 50) String title,
            @Schema(description = "擅长方向（逗号分隔字符串）") @Size(max = 500) String specialties,
            @Schema(description = "医生简介") @Size(max = 1000) String introduction,
            @Schema(description = "是否启用") Boolean enabled
    ) {}

    /** 新增/更新响应体 */
    @Schema(description = "医生操作响应")
    public record MutationResponse(
            @Schema(description = "医生编号") String doctorId
    ) {}
}
