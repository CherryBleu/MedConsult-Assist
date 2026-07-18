package com.medconsult.outpatient.department.controller;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.department.dto.DepartmentDTO;
import com.medconsult.outpatient.department.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 科室对外接口（对齐《接口文档》§2.3）。
 *
 * <p>路径前缀 /api/v1/departments（对外，走 Gateway 鉴权）。
 * <p>查询接口对所有登录用户开放；维护接口（POST/PUT/DELETE）仅 HOSPITAL_ADMIN。
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

    /** 管理员创建科室 */
    @PostMapping
    @Operation(summary = "创建科室")
    public Result<DepartmentDTO.SaveResponse> create(@Valid @RequestBody DepartmentDTO.CreateRequest req) {
        requireHospitalAdmin();
        return Result.ok(departmentService.create(req));
    }

    /** 管理员更新科室 */
    @PutMapping("/{departmentId}")
    @Operation(summary = "更新科室")
    public Result<DepartmentDTO.SaveResponse> update(
            @Parameter(description = "科室编号", required = true) @PathVariable String departmentId,
            @Valid @RequestBody DepartmentDTO.UpdateRequest req) {
        requireHospitalAdmin();
        return Result.ok(departmentService.update(departmentId, req));
    }

    /** 管理员删除科室（软删，有关联启用医生时拒绝） */
    @DeleteMapping("/{departmentId}")
    @Operation(summary = "删除科室")
    public Result<Void> delete(
            @Parameter(description = "科室编号", required = true) @PathVariable String departmentId) {
        requireHospitalAdmin();
        departmentService.delete(departmentId);
        return Result.ok();
    }

    /** 鉴权：仅 HOSPITAL_ADMIN 可维护（outpatient 未启用 @Permission 切面，手动校验） */
    private void requireHospitalAdmin() {
        JwtPayload p = SecurityContext.requireUser();
        if (!p.hasRole("HOSPITAL_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅医院管理员可维护科室");
        }
    }
}
