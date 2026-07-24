package com.medconsult.medicalrecord.medicalrecord.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.DataScope;
import com.medconsult.common.security.Permission;
import com.medconsult.medicalrecord.medicalrecord.dto.MedicalRecordDTO;
import com.medconsult.medicalrecord.medicalrecord.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
@Tag(name = "Medical records", description = "Medical record storage APIs")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @PostMapping
    @Operation(summary = "Create medical record draft")
    @Permission(roles = {"DOCTOR"}, dataScope = DataScope.ALL)
    public Result<MedicalRecordDTO.CreateResponse> create(@Valid @RequestBody MedicalRecordDTO.CreateRequest req) {
        return Result.ok(medicalRecordService.create(req));
    }

    @GetMapping("/{recordId}")
    @Operation(summary = "Get medical record detail")
    public Result<MedicalRecordDTO.DetailResponse> detail(
            @Parameter(description = "Record number", required = true) @PathVariable String recordId) {
        return Result.ok(medicalRecordService.detail(recordId));
    }

    @GetMapping
    @Operation(summary = "List medical records")
    public Result<PageResult<MedicalRecordDTO.ListItem>> list(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "Patient number or id") @RequestParam(required = false) String patientId,
            @Parameter(description = "Appointment number or id") @RequestParam(required = false) String appointmentId) {
        return Result.ok(medicalRecordService.list(page, pageSize, patientId, appointmentId));
    }

    @PutMapping("/{recordId}")
    @Operation(summary = "Update draft medical record")
    @Permission(roles = {"DOCTOR"}, dataScope = DataScope.ALL)
    public Result<MedicalRecordDTO.UpdateResponse> updateDraft(
            @Parameter(description = "Record number", required = true) @PathVariable String recordId,
            @Valid @RequestBody MedicalRecordDTO.UpdateDraftRequest req) {
        return Result.ok(medicalRecordService.updateDraft(recordId, req));
    }

    @PostMapping("/{recordId}/archive")
    @Operation(summary = "Archive medical record")
    @Permission(roles = {"DOCTOR"}, dataScope = DataScope.ALL)
    public Result<MedicalRecordDTO.ArchiveResponse> archive(
            @Parameter(description = "Record number", required = true) @PathVariable String recordId,
            @Valid @RequestBody MedicalRecordDTO.ArchiveRequest req) {
        return Result.ok(medicalRecordService.archive(recordId, req));
    }
}
