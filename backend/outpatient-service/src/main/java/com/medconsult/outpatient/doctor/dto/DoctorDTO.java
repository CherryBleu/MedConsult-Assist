package com.medconsult.outpatient.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 医生相关 DTO（对齐《接口文档》§2.3.2）。
 *
 * <p>2026-07-17 补齐管理员维护接口（POST/PUT/DELETE），前 version 为只读。
 * <p>注意：本批只管 doctor 档案（doctorNo/name/departmentId/title/specialties/introduction/enabled），
 * <b>不涉及登录账号</b>（账号管理是 auth-service 职责，前端表单的 password/phone/gender 字段
 * 属后续待办，不在 doctor 档案范围）。
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

    /** 创建医生请求（departmentNo 为科室编号，service 反查主键） */
    @Data
    @Schema(description = "创建医生请求")
    public static class CreateRequest {
        @NotBlank(message = "医生姓名不能为空")
        @Size(max = 50)
        @Schema(description = "医生姓名")
        private String doctorName;

        @NotBlank(message = "科室编号不能为空")
        @Schema(description = "科室编号 department_no")
        private String departmentNo;

        @Size(max = 50)
        @Schema(description = "职称")
        private String title;

        @Size(max = 500)
        @Schema(description = "专长（逗号分隔，service 转 JSON 存）")
        private String specialties;

        @Size(max = 1000)
        @Schema(description = "简介")
        private String introduction;

        @Schema(description = "是否启用：1 启用 0 停用（默认启用）")
        private Integer enabled;
    }

    /** 更新医生请求（所有字段可选） */
    @Data
    @Schema(description = "更新医生请求")
    public static class UpdateRequest {
        @Size(max = 50) @Schema(description = "医生姓名") private String doctorName;
        @Schema(description = "科室编号") private String departmentNo;
        @Size(max = 50) @Schema(description = "职称") private String title;
        @Size(max = 500) @Schema(description = "专长") private String specialties;
        @Size(max = 1000) @Schema(description = "简介") private String introduction;
        @Schema(description = "是否启用") private Integer enabled;
    }

    @Schema(description = "医生保存响应")
    public record SaveResponse(
            @Schema(description = "医生编号") String doctorId
    ) {}
}
