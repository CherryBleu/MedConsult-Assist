package com.medconsult.patient.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.patient.dto.PatientDTO;
import com.medconsult.patient.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 患者档案对外接口（对齐《接口文档》§2.2）。
 *
 * <p>路径前缀 /api/v1/patients（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code patientId} 实为 {@code patient_no}（业务可读编号，如 P202607060001），
 * 与《接口文档》§2.2 字段命名保持一致。
 */
@Tag(name = "患者档案接口", description = "患者档案 CRUD + 状态流转 + 脱敏（§2.2）")
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    /** §2.2.1 创建患者档案 */
    @Operation(summary = "创建患者档案")
    @PostMapping
    public Result<PatientDTO.SummaryResponse> create(@Valid @RequestBody PatientDTO.CreateRequest req) {
        return Result.ok(patientService.create(req));
    }

    /** §2.2.2 查询患者档案详情 */
    @Operation(summary = "查询患者档案详情")
    @GetMapping("/{patientId}")
    public Result<PatientDTO.DetailResponse> detail(@Parameter(description = "患者编号", required = true) @PathVariable String patientId) {
        return Result.ok(patientService.detail(patientId));
    }

    /** §2.2.3 分页查询患者 */
    @Operation(summary = "分页查询患者列表")
    @GetMapping
    public Result<PageResult<PatientDTO.ListItem>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        return Result.ok(patientService.list(page, pageSize, keyword));
    }

    /** §2.2.4 更新患者档案 */
    @Operation(summary = "更新患者档案")
    @PutMapping("/{patientId}")
    public Result<PatientDTO.UpdateResponse> update(@Parameter(description = "患者编号", required = true) @PathVariable String patientId,
                                                    @Valid @RequestBody PatientDTO.UpdateRequest req) {
        return Result.ok(patientService.update(patientId, req));
    }

    /** §2.2.5 更新患者档案状态 */
    @Operation(summary = "更新患者档案状态")
    @PatchMapping("/{patientId}/status")
    public Result<PatientDTO.StatusResponse> updateStatus(@Parameter(description = "患者编号", required = true) @PathVariable String patientId,
                                                          @Valid @RequestBody PatientDTO.StatusUpdateRequest req) {
        return Result.ok(patientService.updateStatus(patientId, req));
    }
}
