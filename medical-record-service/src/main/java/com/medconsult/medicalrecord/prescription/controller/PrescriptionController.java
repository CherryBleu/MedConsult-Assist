package com.medconsult.medicalrecord.prescription.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.medicalrecord.prescription.dto.PrescriptionDTO;
import com.medconsult.medicalrecord.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 处方对外接口（对齐《修改建议》§2.1 处方接口补充表，第 1 批 5 接口）。
 *
 * <p>路径前缀 /api/v1/prescriptions（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code prescriptionId} 实为 {@code prescription_no}（业务可读编号）。
 *
 * <p>第 1 批：POST 创建 / GET 列表 / GET 详情 / POST {id}/submit / POST {id}/review。
 * <p>第 2 批（后续）：POST {id}/pay / POST {id}/dispense / POST {id}/complete / POST {id}/cancel。
 */
@RestController
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    /** 开方（初始 DRAFT，写主表 + 明细） */
    @PostMapping
    public Result<PrescriptionDTO.CreateResponse> create(@Valid @RequestBody PrescriptionDTO.CreateRequest req) {
        return Result.ok(prescriptionService.create(req));
    }

    /** 处方列表（可按 status 过滤，药师审方工作台） */
    @GetMapping
    public Result<PageResult<PrescriptionDTO.ListItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(prescriptionService.list(page, pageSize, status));
    }

    /** 处方详情（含明细） */
    @GetMapping("/{prescriptionId}")
    public Result<PrescriptionDTO.DetailResponse> detail(@PathVariable String prescriptionId) {
        return Result.ok(prescriptionService.detail(prescriptionId));
    }

    /** 提交审方（DRAFT → PENDING_REVIEW） */
    @PostMapping("/{prescriptionId}/submit")
    public Result<PrescriptionDTO.SubmitResponse> submit(@PathVariable String prescriptionId) {
        return Result.ok(prescriptionService.submit(prescriptionId));
    }

    /** 审方（PENDING_REVIEW → APPROVED | REJECTED，Redis 锁内防并发） */
    @PostMapping("/{prescriptionId}/review")
    public Result<PrescriptionDTO.ReviewResponse> review(
            @PathVariable String prescriptionId,
            @Valid @RequestBody PrescriptionDTO.ReviewRequest req) {
        return Result.ok(prescriptionService.review(prescriptionId, req));
    }
}
