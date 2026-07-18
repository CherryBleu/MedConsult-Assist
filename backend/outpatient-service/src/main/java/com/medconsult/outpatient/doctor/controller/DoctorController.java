package com.medconsult.outpatient.doctor.controller;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.doctor.dto.DoctorDTO;
import com.medconsult.outpatient.doctor.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 医生对外接口（对齐《接口文档》§2.3.2）。
 *
 * <p>路径前缀 /api/v1/doctors（对外，走 Gateway 鉴权）。
 * <p>查询接口对所有登录用户开放；维护接口（POST/PUT/DELETE）仅 HOSPITAL_ADMIN。
 * <p>本批维护接口只管 doctor 档案，不涉及登录账号（账号管理是 auth-service 职责）。
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

    /** 管理员创建医生档案 */
    @PostMapping
    @Operation(summary = "创建医生")
    public Result<DoctorDTO.SaveResponse> create(@Valid @RequestBody DoctorDTO.CreateRequest req) {
        requireHospitalAdmin();
        return Result.ok(doctorService.create(req));
    }

    /** 管理员更新医生档案 */
    @PutMapping("/{doctorId}")
    @Operation(summary = "更新医生")
    public Result<DoctorDTO.SaveResponse> update(
            @Parameter(description = "医生编号", required = true) @PathVariable String doctorId,
            @Valid @RequestBody DoctorDTO.UpdateRequest req) {
        requireHospitalAdmin();
        return Result.ok(doctorService.update(doctorId, req));
    }

    /** 管理员删除医生（软删） */
    @DeleteMapping("/{doctorId}")
    @Operation(summary = "删除医生")
    public Result<Void> delete(
            @Parameter(description = "医生编号", required = true) @PathVariable String doctorId) {
        requireHospitalAdmin();
        doctorService.delete(doctorId);
        return Result.ok();
    }

    private void requireHospitalAdmin() {
        JwtPayload p = SecurityContext.requireUser();
        if (!p.hasRole("HOSPITAL_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅医院管理员可维护医生信息");
        }
    }
}
