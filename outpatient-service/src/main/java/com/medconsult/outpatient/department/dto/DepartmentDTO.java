package com.medconsult.outpatient.department.dto;

/**
 * 科室相关响应 DTO（对齐《接口文档》§2.3）。
 *
 * <p>科室为只读查询（§2.3.1 列表），无创建/更新请求体。
 */
public class DepartmentDTO {

    /** §2.3.1 科室列表 item */
    public record ListItem(
            String departmentId,      // department_no（业务可读）
            String departmentName,
            String location,
            boolean enabled
    ) {}
}
