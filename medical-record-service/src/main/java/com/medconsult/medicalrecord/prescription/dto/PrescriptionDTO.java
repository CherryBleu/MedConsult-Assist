package com.medconsult.medicalrecord.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方相关请求/响应 DTO（对齐《修改建议》§2.1 处方接口补充表）。
 *
 * <p>{@code prescriptionId} 实为 {@code prescription_no}（业务可读编号）。
 * {@code recordId}/{@code patientId}/{@code doctorId}/{@code departmentId} 传业务编号串，
 * service 层用正哈希落库（同病历域策略，下一批接 Feign 后替换为真实主键反查）。
 *
 * <p>本批 5 接口：POST /prescriptions（开方）/ GET /prescriptions（列表）/ GET /{id}（详情）/
 * POST /{id}/submit（提交审方 DRAFT→PENDING_REVIEW）/ POST /{id}/review（审方→APPROVED|REJECTED）。
 */
public class PrescriptionDTO {

    // ===== 开方 POST /prescriptions =====

    @Data
    public static class CreateRequest {
        /** 关联病历编号 record_no */
        @NotBlank(message = "病历编号不能为空")
        private String recordId;
        /** 患者编号 patient_no */
        @NotBlank(message = "患者编号不能为空")
        private String patientId;
        /** 开方医生编号 doctor_no */
        @NotBlank(message = "医生编号不能为空")
        private String doctorId;
        /** 开方科室编号 department_no（可空） */
        private String departmentId;
        /** 处方来源：OUTPATIENT / INPATIENT / EMERGENCY（不传默认 OUTPATIENT） */
        @Pattern(regexp = "^$|^(OUTPATIENT|INPATIENT|EMERGENCY)$",
                message = "处方来源须为 OUTPATIENT / INPATIENT / EMERGENCY")
        private String source;
        /** 处方明细（至少 1 条） */
        @NotEmpty(message = "处方明细不能为空")
        @Valid
        private List<ItemRequest> items;
    }

    @Data
    public static class ItemRequest {
        /** 药品名快照（必填，本批不校验 drugId 存在性） */
        @NotBlank(message = "药品名不能为空")
        @Size(max = 100, message = "药品名不能超过 100 字")
        private String drugName;
        /** 规格快照（可空） */
        @Size(max = 100, message = "规格不能超过 100 字")
        private String specification;
        /** 单次剂量（如 "30mg"） */
        @Size(max = 50, message = "剂量不能超过 50 字")
        private String dosage;
        /** 频次（如 "每日一次"） */
        @Size(max = 50, message = "频次不能超过 50 字")
        private String frequency;
        /** 给药途径（可空，如 "口服"） */
        @Size(max = 50, message = "给药途径不能超过 50 字")
        private String route;
        /** 天数（≥1） */
        @Min(value = 1, message = "天数至少 1 天")
        private Integer days;
        /** 总数量（>0） */
        @DecimalMin(value = "0.01", message = "数量必须大于 0")
        private BigDecimal quantity;
        /** 单位（如 "片"、"盒"） */
        @Size(max = 20, message = "单位不能超过 20 字")
        private String unit;
        /** 单价（≥0，可空——本批前端可不传，subtotal 按 0 计） */
        @DecimalMin(value = "0", message = "单价不能为负")
        private BigDecimal unitPrice;
    }

    /** 开方响应 */
    public record CreateResponse(
            String prescriptionId,   // prescription_no
            String status,           // DRAFT
            BigDecimal totalFee
    ) {}

    // ===== 列表 GET /prescriptions =====

    /** 列表项 */
    public record ListItem(
            String prescriptionId,    // prescription_no
            String recordId,          // record_no（本批回传 null，跨服务反查留第 2 批）
            String status,
            BigDecimal totalFee,
            String paymentStatus,
            LocalDateTime createdAt
    ) {}

    // ===== 详情 GET /{id} =====

    /** 明细项（响应） */
    public record ItemResponse(
            String drugName,
            String specification,
            String dosage,
            String frequency,
            String route,
            Integer days,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}

    /** 详情响应（含明细） */
    public record DetailResponse(
            String prescriptionId,
            String status,
            String source,
            BigDecimal totalFee,
            String paymentStatus,
            Long pharmacyPharmacistId,
            LocalDateTime reviewedAt,
            String reviewComment,
            String rejectReason,
            LocalDateTime createdAt,
            List<ItemResponse> items
    ) {}

    // ===== 提交审方 POST /{id}/submit =====

    /** 提交审方响应（DRAFT→PENDING_REVIEW） */
    public record SubmitResponse(
            String prescriptionId,
            String status           // PENDING_REVIEW
    ) {}

    // ===== 审方 POST /{id}/review =====

    @Data
    public static class ReviewRequest {
        /** 审方动作：APPROVE / REJECT */
        @Pattern(regexp = "^(APPROVE|REJECT)$", message = "审方动作须为 APPROVE / REJECT")
        private String action;
        /** 审方药师编号 pharmacist_no（药师本人） */
        @NotBlank(message = "审方药师编号不能为空")
        private String pharmacistId;
        /** 审方意见（approve/reject 均可填） */
        @Size(max = 500, message = "审方意见不能超过 500 字")
        private String reviewComment;
        /** 驳回原因（action=REJECT 时必填，approve 时忽略） */
        @Size(max = 500, message = "驳回原因不能超过 500 字")
        private String rejectReason;
    }

    /** 审方响应（PENDING_REVIEW→APPROVED|REJECTED） */
    public record ReviewResponse(
            String prescriptionId,
            String status,          // APPROVED / REJECTED
            LocalDateTime reviewedAt
    ) {}
}
