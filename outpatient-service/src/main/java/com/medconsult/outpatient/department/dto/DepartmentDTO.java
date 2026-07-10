package com.medconsult.outpatient.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 科室相关响应 DTO（对齐《接口文档》§2.3）。
 *
 * <p>科室为只读查询（§2.3.1 列表），无创建/更新请求体。
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
}
