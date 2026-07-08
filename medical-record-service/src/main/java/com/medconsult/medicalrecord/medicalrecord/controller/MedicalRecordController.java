package com.medconsult.medicalrecord.medicalrecord.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.medicalrecord.medicalrecord.dto.MedicalRecordDTO;
import com.medconsult.medicalrecord.medicalrecord.service.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 电子病历对外接口（对齐《接口文档》§2.6 + 《修改建议》§2.1）。
 *
 * <p>路径前缀 /api/v1/medical-records（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code recordId} 实为 {@code record_no}（业务可读编号）。
 *
 * <p><b>修订项 §2.1</b>：创建接口<b>不</b>接受 prescriptions 入参——处方独立走
 * {@code POST /api/v1/prescriptions}，通过 record_id 反查。
 */
@RestController
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    /** §2.6.1 创建电子病历（初始 DRAFT） */
    @PostMapping
    public Result<MedicalRecordDTO.CreateResponse> create(@Valid @RequestBody MedicalRecordDTO.CreateRequest req) {
        return Result.ok(medicalRecordService.create(req));
    }

    /** §2.6.2 查询病历详情 */
    @GetMapping("/{recordId}")
    public Result<MedicalRecordDTO.DetailResponse> detail(@PathVariable String recordId) {
        return Result.ok(medicalRecordService.detail(recordId));
    }

    /** §2.6.3 分页查询病历（可按 patientId 过滤） */
    @GetMapping
    public Result<PageResult<MedicalRecordDTO.ListItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String patientId) {
        return Result.ok(medicalRecordService.list(page, pageSize, patientId));
    }

    /** §2.6.4 更新草稿病历（仅 DRAFT 可改） */
    @PutMapping("/{recordId}")
    public Result<MedicalRecordDTO.UpdateResponse> updateDraft(
            @PathVariable String recordId,
            @Valid @RequestBody MedicalRecordDTO.UpdateDraftRequest req) {
        return Result.ok(medicalRecordService.updateDraft(recordId, req));
    }

    /** §2.6.5 归档病历（DRAFT → ARCHIVED，不可逆） */
    @PostMapping("/{recordId}/archive")
    public Result<MedicalRecordDTO.ArchiveResponse> archive(
            @PathVariable String recordId,
            @Valid @RequestBody MedicalRecordDTO.ArchiveRequest req) {
        return Result.ok(medicalRecordService.archive(recordId, req));
    }
}
