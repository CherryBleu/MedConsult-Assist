package com.medconsult.outpatient.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 医生相关响应 DTO（对齐《接口文档》§2.3.2）。
 *
 * <p>医生为只读查询（§2.3.2 列表），无创建/更新请求体。
 * {@code departmentName} 由 service 层业务组装（不跨表 join，二次查询）。
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
}
