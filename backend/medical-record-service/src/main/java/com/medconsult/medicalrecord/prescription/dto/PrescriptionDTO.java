package com.medconsult.medicalrecord.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * service 层用 Feign/同服务反查落真实主键（recordId 同服务直查 medical_record.id；
 * patientId/doctorId/departmentId 经 Feign 反查 patient/outpatient 服务）。
 *
 * <p>当前主流程：POST /prescriptions 开方后直接 APPROVED，患者可缴费，药房可调剂发药。
 * submit/review 保留用于历史 PENDING_REVIEW 处方兼容处理。
 */
public class PrescriptionDTO {

    // ===== 开方 POST /prescriptions =====

    @Data
    @Schema(description = "创建处方请求")
    public static class CreateRequest {
        /** 关联病历编号 record_no */
        @NotBlank(message = "病历编号不能为空")
        @Schema(description = "病历编号")
        private String recordId;
        /** 患者编号 patient_no */
        @NotBlank(message = "患者编号不能为空")
        @Schema(description = "患者编号")
        private String patientId;
        /** 开方医生编号 doctor_no */
        @NotBlank(message = "医生编号不能为空")
        @Schema(description = "医生编号")
        private String doctorId;
        /** 开方科室编号 department_no（可空） */
        @Schema(description = "科室编号")
        private String departmentId;
        /** 处方来源：OUTPATIENT / INPATIENT / EMERGENCY（不传默认 OUTPATIENT） */
        @Pattern(regexp = "^$|^(OUTPATIENT|INPATIENT|EMERGENCY)$",
                message = "处方来源须为 OUTPATIENT / INPATIENT / EMERGENCY")
        @Schema(description = "来源：OUTPATIENT / INPATIENT / EMERGENCY")
        private String source;
        /** 处方明细（至少 1 条） */
        @NotEmpty(message = "处方明细不能为空")
        @Valid
        @Schema(description = "处方明细列表")
        private List<ItemRequest> items;
    }

    @Data
    @Schema(description = "处方明细项")
    public static class ItemRequest {
        /**
         * 药品编号 drug_no（第 2 批新增，可选）。
         * <p>非空时直接存入 prescription_item.drug_no（不做 Feign 反查，药品存在性由 dispense 时
         * drug-service 自然校验）。为空时 drug_no 存 null，dispense 时须由前端通过
         * DispenseRequest.itemDrugNoMap 补传。
         */
        @Size(max = 32, message = "药品编号不能超过 32 字")
        @Schema(description = "药品编号")
        private String drugNo;
        /** 药品名快照（必填，本批不校验 drugId 存在性） */
        @NotBlank(message = "药品名不能为空")
        @Size(max = 100, message = "药品名不能超过 100 字")
        @Schema(description = "药品名称")
        private String drugName;
        /** 规格快照（可空） */
        @Size(max = 100, message = "规格不能超过 100 字")
        @Schema(description = "规格")
        private String specification;
        /** 单次剂量（如 "30mg"） */
        @Size(max = 50, message = "剂量不能超过 50 字")
        @Schema(description = "用法用量")
        private String dosage;
        /** 频次（如 "每日一次"） */
        @Size(max = 50, message = "频次不能超过 50 字")
        @Schema(description = "用药频次")
        private String frequency;
        /** 给药途径（可空，如 "口服"） */
        @Size(max = 50, message = "给药途径不能超过 50 字")
        @Schema(description = "给药途径")
        private String route;
        /** 天数（≥1，必填） */
        @NotNull(message = "天数不能为空")
        @Min(value = 1, message = "天数至少 1 天")
        @Schema(description = "用药天数")
        private Integer days;
        /** 总数量（>0，必填——@DecimalMin 对 null 返回 valid，故须 @NotNull 兜底） */
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0.01", message = "数量必须大于 0")
        @Schema(description = "数量")
        private BigDecimal quantity;
        /** 单位（如 "片"、"盒"） */
        @Size(max = 20, message = "单位不能超过 20 字")
        @Schema(description = "单位")
        private String unit;
        /** 单价（≥0，可空——本批前端可不传，subtotal 按 0 计） */
        @DecimalMin(value = "0", message = "单价不能为负")
        @Schema(description = "单价")
        private BigDecimal unitPrice;
    }

    /** 开方响应 */
    @Schema(description = "创建处方响应")
    public record CreateResponse(
            @Schema(description = "处方编号") String prescriptionId,   // prescription_no
            @Schema(description = "状态") String status,           // APPROVED
            @Schema(description = "总金额") BigDecimal totalFee
    ) {}

    // ===== 列表 GET /prescriptions =====

    /** 列表项 */
    @Schema(description = "处方列表项")
    public record ListItem(
            @Schema(description = "处方编号") String prescriptionId,    // prescription_no
            @Schema(description = "病历编号") String recordId,          // record_no（本批回传 null，跨服务反查留第 2 批）
            @Schema(description = "处方状态") String status,
            @Schema(description = "总金额") BigDecimal totalFee,
            @Schema(description = "支付状态") String paymentStatus,
            @Schema(description = "创建时间") LocalDateTime createdAt
    ) {}

    // ===== 详情 GET /{id} =====

    /** 明细项（响应） */
    @Schema(description = "处方明细响应项")
    public record ItemResponse(
            @Schema(description = "药品名称") String drugName,
            @Schema(description = "规格") String specification,
            @Schema(description = "用法用量") String dosage,
            @Schema(description = "用药频次") String frequency,
            @Schema(description = "给药途径") String route,
            @Schema(description = "用药天数") Integer days,
            @Schema(description = "数量") BigDecimal quantity,
            @Schema(description = "单位") String unit,
            @Schema(description = "单价") BigDecimal unitPrice,
            @Schema(description = "小计") BigDecimal subtotal
    ) {}

    /** 详情响应（含明细） */
    @Schema(description = "处方详情响应")
    public record DetailResponse(
            @Schema(description = "处方编号") String prescriptionId,
            @Schema(description = "处方状态") String status,
            @Schema(description = "来源") String source,
            @Schema(description = "总金额") BigDecimal totalFee,
            @Schema(description = "支付状态") String paymentStatus,
            @Schema(description = "审方药师ID") Long pharmacyPharmacistId,
            @Schema(description = "审方时间") LocalDateTime reviewedAt,
            @Schema(description = "审方意见") String reviewComment,
            @Schema(description = "驳回原因") String rejectReason,
            @Schema(description = "创建时间") LocalDateTime createdAt,
            @Schema(description = "处方明细列表") List<ItemResponse> items
    ) {}

    // ===== 提交审方 POST /{id}/submit =====

    /** 提交审方响应（DRAFT→PENDING_REVIEW） */
    @Schema(description = "提交处方响应")
    public record SubmitResponse(
            @Schema(description = "处方编号") String prescriptionId,
            @Schema(description = "状态") String status           // PENDING_REVIEW
    ) {}

    // ===== 审方 POST /{id}/review =====

    @Data
    @Schema(description = "药师审方请求")
    public static class ReviewRequest {
        /** 审方动作：APPROVE / REJECT（必填——@Pattern 对 null 返回 valid，故须 @NotBlank 兜底） */
        @NotBlank(message = "审方动作不能为空（APPROVE/REJECT）")
        @Pattern(regexp = "^(APPROVE|REJECT)$", message = "审方动作须为 APPROVE / REJECT")
        @Schema(description = "审方动作：APPROVE / REJECT")
        private String action;
        /** 审方药师编号 pharmacist_no（药师本人） */
        @NotBlank(message = "审方药师编号不能为空")
        @Schema(description = "药师编号")
        private String pharmacistId;
        /** 审方意见（approve/reject 均可填） */
        @Size(max = 500, message = "审方意见不能超过 500 字")
        @Schema(description = "审方意见")
        private String reviewComment;
        /** 驳回原因（action=REJECT 时必填，approve 时忽略） */
        @Size(max = 500, message = "驳回原因不能超过 500 字")
        @Schema(description = "驳回原因")
        private String rejectReason;
    }

    /** 审方响应（PENDING_REVIEW→APPROVED|REJECTED） */
    @Schema(description = "审方响应")
    public record ReviewResponse(
            @Schema(description = "处方编号") String prescriptionId,
            @Schema(description = "状态") String status,          // APPROVED / REJECTED
            @Schema(description = "审方时间") LocalDateTime reviewedAt
    ) {}

    // ===== 缴费 POST /{id}/pay（APPROVED → PAID）=====

    @Data
    @Schema(description = "处方缴费请求")
    public static class PayRequest {
        /** 实付金额（≥0，须与处方 totalFee 一致或由前端确认） */
        @NotNull(message = "实付金额不能为空")
        @DecimalMin(value = "0", message = "实付金额不能为负")
        @Schema(description = "支付金额")
        private BigDecimal paidAmount;
        /** 支付单号（外部支付系统回传） */
        @NotBlank(message = "支付单号不能为空")
        @Size(max = 64, message = "支付单号不能超过 64 字")
        @Schema(description = "支付流水号")
        private String paymentNo;
    }

    /** 缴费响应 */
    @Schema(description = "处方缴费响应")
    public record PayResponse(
            @Schema(description = "处方编号") String prescriptionId,
            @Schema(description = "处方状态") String status,          // PAID
            @Schema(description = "支付状态") String paymentStatus,   // PAID
            @Schema(description = "支付金额") BigDecimal paidAmount,
            @Schema(description = "支付流水号") String paymentNo        // 支付单号（回传确认已记录）
    ) {}

    // ===== 调剂发药 POST /{id}/dispense（APPROVED/PAID → DISPENSED）=====

    @Data
    @Schema(description = "调剂发药请求")
    public static class DispenseRequest {
        /**
         * 调剂操作人编号 pharmacist_no（药师本人）。
         */
        @NotBlank(message = "调剂药师编号不能为空")
        @Schema(description = "发药药师编号")
        private String pharmacistId;
        /**
         * 明细→药品编号映射（可选，兼容 batch 1 历史 drug_no=null 的处方）。
         * <p>key = prescription_item.id（字符串化），value = drug_no。
         * <p>dispense 时优先用 item.drugNo（batch 2 开方时已存），若为空则查此 map。
         * 历史处方（batch 1 开的，drug_no=null）必须传此 map 才能调剂。
         */
        @Schema(description = "处方明细项ID到药品编号的映射")
        private Map<String, String> itemDrugNoMap;
    }

    /** 调剂发药明细结果 */
    @Schema(description = "发药明细项")
    public record DispenseItem(
            @Schema(description = "明细项ID") Long itemId,
            @Schema(description = "药品名称") String drugName,
            @Schema(description = "药品编号") String drugNo,
            @Schema(description = "实际发药数量") BigDecimal dispensedQuantity,
            @Schema(description = "库存流水编号") String stockFlowId        // drug-service 返回的首条 flow_no
    ) {}

    /** 调剂发药响应（含每条明细扣减结果） */
    @Schema(description = "调剂发药响应")
    public record DispenseResponse(
            @Schema(description = "处方编号") String prescriptionId,
            @Schema(description = "处方状态") String status,            // DISPENSED
            @Schema(description = "发药时间") LocalDateTime dispensedAt,
            @Schema(description = "发药明细列表") List<DispenseItem> items
    ) {}

    // ===== 完成 POST /{id}/complete（DISPENSED → COMPLETED）=====

    /** 完成响应（无请求体，POST 空 body 即可） */
    @Schema(description = "发药完成响应")
    public record CompleteResponse(
            @Schema(description = "处方编号") String prescriptionId,
            @Schema(description = "状态") String status             // COMPLETED
    ) {}

    // ===== 退方 POST /{id}/cancel（APPROVED/PAID → CANCELLED）=====

    @Data
    @Schema(description = "取消处方请求")
    public static class CancelRequest {
        /** 退方原因 */
        @NotBlank(message = "退方原因不能为空")
        @Size(max = 500, message = "退方原因不能超过 500 字")
        @Schema(description = "取消原因")
        private String cancelReason;
        /** 退方操作人编号 */
        @NotBlank(message = "退方操作人不能为空")
        @Schema(description = "操作人编号")
        private String operatorId;
    }

    /** 退方响应 */
    @Schema(description = "取消处方响应")
    public record CancelResponse(
            @Schema(description = "处方编号") String prescriptionId,
            @Schema(description = "状态") String status,            // CANCELLED
            @Schema(description = "取消原因") String cancelReason
    ) {}
}
