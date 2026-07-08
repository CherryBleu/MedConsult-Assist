package com.medconsult.outpatient.doctor.dto;

import java.util.List;

/**
 * 医生相关响应 DTO（对齐《接口文档》§2.3.2）。
 *
 * <p>医生为只读查询（§2.3.2 列表），无创建/更新请求体。
 * {@code departmentName} 由 service 层业务组装（不跨表 join，二次查询）。
 */
public class DoctorDTO {

    /** §2.3.2 医生列表 item */
    public record ListItem(
            String doctorId,          // doctor_no（业务可读）
            String doctorName,
            String departmentId,      // department_no
            String departmentName,    // 业务层组装（非 join）
            String title,
            List<String> specialties,
            boolean enabled
    ) {}
}
