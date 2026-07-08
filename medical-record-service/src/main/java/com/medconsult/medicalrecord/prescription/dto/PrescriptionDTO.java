package com.medconsult.medicalrecord.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        /**
         * 药品编号 drug_no（第 2 批新增，可选）。
         * <p>非空时 service 用 Feign 反查 drug-service 校验药品存在并存 drug_id（替代 batch 1 的 null 占位）。
         * 为空时 drug_id 存 null，dispense 时须由前端通过 DispenseRequest.itemDrugNoMap 补传。
         */
        @Size(max = 32, message = "药品编号不能超过 32 字")
        private String drugNo;
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
        /** 天数（≥1，必填） */
        @NotNull(message = "天数不能为空")
        @Min(value = 1, message = "天数至少 1 天")
        private Integer days;
        /** 总数量（>0，必填——@DecimalMin 对 null 返回 valid，故须 @NotNull 兜底） */
        @NotNull(message = "数量不能为空")
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
        /** 审方动作：APPROVE / REJECT（必填——@Pattern 对 null 返回 valid，故须 @NotBlank 兜底） */
        @NotBlank(message = "审方动作不能为空（APPROVE/REJECT）")
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

    // ===== 缴费 POST /{id}/pay（APPROVED → PAID）=====

    @Data
    public static class PayRequest {
        /** 实付金额（≥0，须与处方 totalFee 一致或由前端确认） */
        @NotNull(message = "实付金额不能为空")
        @DecimalMin(value = "0", message = "实付金额不能为负")
        private BigDecimal paidAmount;
        /** 支付单号（外部支付系统回传） */
        @NotBlank(message = "支付单号不能为空")
        @Size(max = 64, message = "支付单号不能超过 64 字")
        private String paymentNo;
    }

    /** 缴费响应 */
    public record PayResponse(
            String prescriptionId,
            String status,          // PAID
            String paymentStatus,   // PAID
            BigDecimal paidAmount
    ) {}

    // ===== 调剂发药 POST /{id}/dispense（APPROVED/PAID → DISPENSED）=====

    @Data
    public static class DispenseRequest {
        /**
         * 调剂操作人编号 pharmacist_no（药师本人）。
         */
        @NotBlank(message = "调剂药师编号不能为空")
        private String pharmacistId;
        /**
         * 明细→药品编号映射（可选，兼容 batch 1 历史 drug_id=null 的处方）。
         * <p>key = prescription_item.id（字符串化），value = drug_no。
         * <p>dispense 时优先用 item.drugId（batch 2 开方时已存），若为 null 则查此 map。
         * 历史处方（batch 1 开的，drug_id=null）必须传此 map 才能调剂。
         */
        private Map<String, String> itemDrugNoMap;
    }

    /** 调剂发药明细结果 */
    public record DispenseItem(
            Long itemId,
            String drugName,
            String drugNo,
            BigDecimal dispensedQuantity,
            String stockFlowId        // drug-service 返回的首条 flow_no
    ) {}

    /** 调剂发药响应（含每条明细扣减结果） */
    public record DispenseResponse(
            String prescriptionId,
            String status,            // DISPENSED
            LocalDateTime dispensedAt,
            List<DispenseItem> items
    ) {}

    // ===== 完成 POST /{id}/complete（DISPENSED → COMPLETED）=====

    /** 完成响应（无请求体，POST 空 body 即可） */
    public record CompleteResponse(
            String prescriptionId,
            String status             // COMPLETED
    ) {}

    // ===== 退方 POST /{id}/cancel（APPROVED/PAID → CANCELLED）=====

    @Data
    public static class CancelRequest {
        /** 退方原因 */
        @NotBlank(message = "退方原因不能为空")
        @Size(max = 500, message = "退方原因不能超过 500 字")
        private String cancelReason;
        /** 退方操作人编号 */
        @NotBlank(message = "退方操作人不能为空")
        private String operatorId;
    }

    /** 退方响应 */
    public record CancelResponse(
            String prescriptionId,
            String status,            // CANCELLED
            String cancelReason
    ) {}
}
