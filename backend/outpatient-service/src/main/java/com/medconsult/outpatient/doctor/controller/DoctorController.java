package com.medconsult.outpatient.doctor.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.Permission;
import com.medconsult.outpatient.doctor.dto.DoctorDTO;
import com.medconsult.outpatient.doctor.service.DoctorService;
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
 * 医生对外接口（对齐《接口文档》§2.3.2 + 管理员维护接口）。
 *
 * <p>路径前缀 /api/v1/doctors（对外，走 Gateway 鉴权）。
 * §2.3.2 列表所有角色可查；新增/编辑/删除仅 HOSPITAL_ADMIN（与 DepartmentController 一致）。
 */
@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Tag(name = "医生接口", description = "医生基础信息查询与维护（§2.3）")
public class DoctorController {

    private final DoctorService doctorService;

    /** §2.3.2 查询医生列表 */
    @GetMapping
    @Operation(summary = "查询医生列表")
    public Result<PageResult<DoctorDTO.ListItem>> list(
            @Parameter(description = "页码", required = true) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", required = true) @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "科室编号", required = false) @RequestParam(required = false) String departmentId,
            @Parameter(description = "是否启用", required = false) @RequestParam(required = false) Boolean enabled) {
        return Result.ok(doctorService.list(page, pageSize, departmentId, enabled));
    }

    /** 新增医生（仅 HOSPITAL_ADMIN；doctor_no 后端自动生成） */
    @PostMapping
    @Permission(roles = {"HOSPITAL_ADMIN"})
    @Operation(summary = "新增医生")
    public Result<DoctorDTO.MutationResponse> create(@Valid @RequestBody DoctorDTO.CreateRequest req) {
        return Result.ok(doctorService.create(req));
    }

    /** 更新医生（仅 HOSPITAL_ADMIN；部分字段覆盖） */
    @PatchMapping("/{doctorId}")
    @Permission(roles = {"HOSPITAL_ADMIN"})
    @Operation(summary = "更新医生")
    public Result<DoctorDTO.MutationResponse> update(
            @Parameter(description = "医生编号", required = true) @PathVariable String doctorId,
            @Valid @RequestBody DoctorDTO.UpdateRequest req) {
        return Result.ok(doctorService.update(doctorId, req));
    }

    /** 删除医生（仅 HOSPITAL_ADMIN；逻辑删除） */
    @DeleteMapping("/{doctorId}")
    @Permission(roles = {"HOSPITAL_ADMIN"})
    @Operation(summary = "删除医生")
    public Result<Void> delete(
            @Parameter(description = "医生编号", required = true) @PathVariable String doctorId) {
        doctorService.delete(doctorId);
        return Result.ok(null);
    }
}
