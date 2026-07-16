package com.medconsult.medicalrecord.prescription.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.Permission;
import com.medconsult.medicalrecord.prescription.dto.PrescriptionDTO;
import com.medconsult.medicalrecord.prescription.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "处方接口", description = "处方开具 + 8 态状态机（§2.6 处方）")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    /** 开方（初始 DRAFT，写主表 + 明细）—— 仅医生可开方（§2.3 权限矩阵「处方 开方=DOCTOR」） */
    @PostMapping
    @Permission(roles = {"DOCTOR"})
    @Operation(summary = "创建处方")
    public Result<PrescriptionDTO.CreateResponse> create(@Valid @RequestBody PrescriptionDTO.CreateRequest req) {
        return Result.ok(prescriptionService.create(req));
    }

    /** 处方列表（可按 status 过滤，药师审方工作台）—— 医生看自己开的，药师/管理员看全部（§2.3） */
    @GetMapping
    @Permission(roles = {"DOCTOR", "PHARMACY_ADMIN", "HOSPITAL_ADMIN"})
    @Operation(summary = "分页查询处方")
    public Result<PageResult<PrescriptionDTO.ListItem>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "处方状态") @RequestParam(required = false) String status) {
        return Result.ok(prescriptionService.list(page, pageSize, status));
    }

    /** 处方详情（含明细）—— 医生/药师/管理员可查（§2.3；PATIENT 查自己处方的 SELF 校验见 service） */
    @GetMapping("/{prescriptionId}")
    @Permission(roles = {"DOCTOR", "PHARMACY_ADMIN", "HOSPITAL_ADMIN"})
    @Operation(summary = "查询处方详情")
    public Result<PrescriptionDTO.DetailResponse> detail(@Parameter(description = "处方编号", required = true) @PathVariable String prescriptionId) {
        return Result.ok(prescriptionService.detail(prescriptionId));
    }

    /** 提交审方（DRAFT → PENDING_REVIEW）—— 仅开方医生提交（§2.3「处方 开方=DOCTOR」） */
    @PostMapping("/{prescriptionId}/submit")
    @Permission(roles = {"DOCTOR"})
    @Operation(summary = "提交处方（草稿→待审）")
    public Result<PrescriptionDTO.SubmitResponse> submit(@Parameter(description = "处方编号", required = true) @PathVariable String prescriptionId) {
        return Result.ok(prescriptionService.submit(prescriptionId));
    }

    /** 审方（PENDING_REVIEW → APPROVED | REJECTED，Redis 锁内防并发）—— 仅药师审方（§2.3「处方 审核=PHARMACY_ADMIN」） */
    @PostMapping("/{prescriptionId}/review")
    @Permission(roles = {"PHARMACY_ADMIN"})
    @Operation(summary = "药师审方（待审→已通过/已驳回）")
    public Result<PrescriptionDTO.ReviewResponse> review(
            @Parameter(description = "处方编号", required = true) @PathVariable String prescriptionId,
            @Valid @RequestBody PrescriptionDTO.ReviewRequest req) {
        return Result.ok(prescriptionService.review(prescriptionId, req));
    }

    /** 缴费（APPROVED → PAID）—— 仅患者缴费（§2.3「处方 缴费=PATIENT」） */
    @PostMapping("/{prescriptionId}/pay")
    @Permission(roles = {"PATIENT"})
    @Operation(summary = "处方缴费（已通过→已缴费）")
    public Result<PrescriptionDTO.PayResponse> pay(
            @Parameter(description = "处方编号", required = true) @PathVariable String prescriptionId,
            @Valid @RequestBody PrescriptionDTO.PayRequest req) {
        return Result.ok(prescriptionService.pay(prescriptionId, req));
    }

    /**
     * 调剂发药（APPROVED/PAID → DISPENSED，同步 Feign 调 drug-service FEFO 出库）—— 仅药师（§2.3「处方 调剂发药=PHARMACY_ADMIN」）。
     * <p>架构文档 §6.2 原写 MQ 异步，本批务实采用同步 Feign（MQ 化为后续待办）。
     */
    @PostMapping("/{prescriptionId}/dispense")
    @Permission(roles = {"PHARMACY_ADMIN"})
    @Operation(summary = "调剂发药（已缴费→已调配）")
    public Result<PrescriptionDTO.DispenseResponse> dispense(
            @Parameter(description = "处方编号", required = true) @PathVariable String prescriptionId,
            @Valid @RequestBody PrescriptionDTO.DispenseRequest req) {
        return Result.ok(prescriptionService.dispense(prescriptionId, req));
    }

    /** 完成（DISPENSED → COMPLETED）—— 仅药师（§2.3「处方 调剂发药=PHARMACY_ADMIN」） */
    @PostMapping("/{prescriptionId}/complete")
    @Permission(roles = {"PHARMACY_ADMIN"})
    @Operation(summary = "发药完成（已调配→已完成）")
    public Result<PrescriptionDTO.CompleteResponse> complete(@Parameter(description = "处方编号", required = true) @PathVariable String prescriptionId) {
        return Result.ok(prescriptionService.complete(prescriptionId));
    }

    /** 退方（APPROVED/PAID → CANCELLED）—— 医生/药师可退（§2.3） */
    @PostMapping("/{prescriptionId}/cancel")
    @Permission(roles = {"DOCTOR", "PHARMACY_ADMIN"})
    @Operation(summary = "取消处方")
    public Result<PrescriptionDTO.CancelResponse> cancel(
            @Parameter(description = "处方编号", required = true) @PathVariable String prescriptionId,
            @Valid @RequestBody PrescriptionDTO.CancelRequest req) {
        return Result.ok(prescriptionService.cancel(prescriptionId, req));
    }
}
