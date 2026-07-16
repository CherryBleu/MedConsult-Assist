package com.medconsult.outpatient.department.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.Permission;
import com.medconsult.outpatient.department.dto.DepartmentDTO;
import com.medconsult.outpatient.department.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 科室对外接口（对齐《接口文档》§2.3）。
 *
 * <p>路径前缀 /api/v1/departments（对外，走 Gateway 鉴权）。
 * §2.3.1 列表所有角色可查；§2.3.2/2.3.3/2.3.4 新增/编辑/删除仅 HOSPITAL_ADMIN（#15）。
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "科室接口", description = "科室基础信息查询与维护（§2.3）")
public class DepartmentController {

    private final DepartmentService departmentService;

    /** §2.3.1 查询科室列表 */
    @GetMapping
    @Operation(summary = "查询科室列表")
    public Result<PageResult<DepartmentDTO.ListItem>> list(
            @Parameter(description = "页码", required = true) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", required = true) @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "是否启用", required = false) @RequestParam(required = false) Boolean enabled) {
        return Result.ok(departmentService.list(page, pageSize, enabled));
    }

    /** §2.3.2 新增科室（departmentNo 后端自动生成） */
    @PostMapping
    @Permission(roles = {"HOSPITAL_ADMIN"})
    @Operation(summary = "新增科室")
    public Result<DepartmentDTO.MutationResponse> create(@Valid @RequestBody DepartmentDTO.CreateRequest req) {
        return Result.ok(departmentService.create(req));
    }

    /** §2.3.3 更新科室（部分字段） */
    @PatchMapping("/{departmentId}")
    @Permission(roles = {"HOSPITAL_ADMIN"})
    @Operation(summary = "更新科室")
    public Result<DepartmentDTO.MutationResponse> update(
            @Parameter(description = "科室编号", required = true) @PathVariable String departmentId,
            @Valid @RequestBody DepartmentDTO.UpdateRequest req) {
        return Result.ok(departmentService.update(departmentId, req));
    }

    /** §2.3.4 删除科室（逻辑删除；被引用时拒绝） */
    @DeleteMapping("/{departmentId}")
    @Permission(roles = {"HOSPITAL_ADMIN"})
    @Operation(summary = "删除科室")
    public Result<Void> delete(
            @Parameter(description = "科室编号", required = true) @PathVariable String departmentId) {
        departmentService.delete(departmentId);
        return Result.ok(null);
    }
}
