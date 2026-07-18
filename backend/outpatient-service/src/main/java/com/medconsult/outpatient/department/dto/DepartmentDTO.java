package com.medconsult.outpatient.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 科室相关 DTO（对齐《接口文档》§2.3）。
 *
 * <p>2026-07-17 补齐管理员维护接口（POST/PUT/DELETE），前 version 为只读。
 */
public class DepartmentDTO {

    /** §2.3.1 科室列表 item */
    @Schema(description = "科室列表项")
    public record ListItem(
            @Schema(description = "科室编号") String departmentId,      // department_no（业务可读）
            @Schema(description = "科室名称") String departmentName,
            @Schema(description = "位置") String location,
            @Schema(description = "是否启用") boolean enabled
    ) {}

    /** 创建科室请求 */
    @Data
    @Schema(description = "创建科室请求")
    public static class CreateRequest {
        @NotBlank(message = "科室名称不能为空")
        @Size(max = 50, message = "科室名称不能超过 50 字")
        @Schema(description = "科室名称")
        private String departmentName;

        @Size(max = 200, message = "科室介绍不能超过 200 字")
        @Schema(description = "科室介绍")
        private String description;

        @Size(max = 100, message = "位置不能超过 100 字")
        @Schema(description = "位置")
        private String location;

        @Schema(description = "是否启用：1 启用 0 停用（默认启用）")
        private Integer enabled;
    }

    /** 更新科室请求（所有字段可选，仅传的才更新） */
    @Data
    @Schema(description = "更新科室请求")
    public static class UpdateRequest {
        @Size(max = 50)
        @Schema(description = "科室名称")
        private String departmentName;

        @Size(max = 200)
        @Schema(description = "科室介绍")
        private String description;

        @Size(max = 100)
        @Schema(description = "位置")
        private String location;

        @Schema(description = "是否启用")
        private Integer enabled;
    }

    /** 创建/更新响应 */
    @Schema(description = "科室保存响应")
    public record SaveResponse(
            @Schema(description = "科室编号") String departmentId
    ) {}
}
