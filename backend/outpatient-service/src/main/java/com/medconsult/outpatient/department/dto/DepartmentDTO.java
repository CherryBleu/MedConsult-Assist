package com.medconsult.outpatient.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 科室相关 DTO（对齐《接口文档》§2.3）。
 *
 * <p>§2.3.1 列表为只读查询；§2.3.2/2.3.3/2.3.4 新增/编辑/删除为管理员维护接口（#15）。
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

    /** §2.3.2 新增科室请求体。departmentNo 由后端自动生成（前端不传）。 */
    @Schema(description = "新增科室请求")
    public record CreateRequest(
            @Schema(description = "科室名称") @NotBlank(message = "科室名称不能为空") @Size(max = 100) String name,
            @Schema(description = "科室介绍") @Size(max = 500) String description,
            @Schema(description = "科室位置") @Size(max = 200) String location,
            @Schema(description = "是否启用（默认 true）") Boolean enabled
    ) {}

    /** §2.3.3 更新科室请求体。全部字段可选，仅传需要变更的字段。 */
    @Schema(description = "更新科室请求")
    public record UpdateRequest(
            @Schema(description = "科室名称") @Size(max = 100) String name,
            @Schema(description = "科室介绍") @Size(max = 500) String description,
            @Schema(description = "科室位置") @Size(max = 200) String location,
            @Schema(description = "是否启用") Boolean enabled
    ) {}

    /** §2.3.2/2.3.3 新增/更新响应体 */
    @Schema(description = "科室操作响应")
    public record MutationResponse(
            @Schema(description = "科室编号") String departmentId
    ) {}
}
