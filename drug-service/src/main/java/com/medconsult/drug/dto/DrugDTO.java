package com.medconsult.drug.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 药品库存管理相关请求/响应 DTO（对齐《接口文档》§2.7）。
 *
 * <p>对外路径变量 {@code drugId} 实为 {@code drug_no}（业务可读编号，对外暴露）；
 * 内部接口（/internal/drugs）路径变量 {@code drugId} 是 BIGINT 主键（跨服务调用用主键更稳定）。
 *
 * <p>{@code contraindications} / {@code interactions} 在请求/响应中用结构化对象（List），
 * 落库为 JSON 串（Service 层用 Jackson 序列化）。
 */
public class DrugDTO {

    // ===== §2.7.1 创建药品 =====

    /**
     * 禁忌项（结构化，与 DrugRiskInfoDTO.Contraindication 对齐，《修改建议》§4.4）。
     * 请求可直接传 List&lt;String&gt;（简化版）或 List&lt;Contraindication&gt;（结构化）；
     * 本 DTO 用 List&lt;String&gt; 简化创建接口输入，Service 层补 level/note 默认值。
     */
    @Data
    public static class CreateDrugRequest {
        /** 通用名，如 硝苯地平 */
        @NotBlank(message = "通用名不能为空")
        private String genericName;
        /** 商品名，如 拜新同 */
        private String tradeName;
        /** 规格，如 30mg*7片 */
        private String specification;
        /** 剂型，如 控释片 */
        private String dosageForm;
        /** 生产厂家 */
        private String manufacturer;
        /** 批准文号（国药准字） */
        private String approvalNo;
        /** 单位，如 盒 */
        private String unit;
        /** 最低库存阈值 */
        @NotNull(message = "最低库存阈值不能为空")
        @Min(value = 0, message = "最低库存阈值不能为负")
        private Integer minStockThreshold;
        /** 禁忌信息（简化为字符串数组，如 ["严重低血压","心源性休克"]） */
        private List<String> contraindications;
        /** 相互作用信息（简化为字符串数组） */
        private List<String> interactions;
    }

    /** §2.7.1 / §2.7.2 复用：药品摘要响应（含 drugNo / genericName / status） */
    public record DrugSummary(
            String drugId,          // drug_no
            String genericName,
            String status
    ) {}

    // ===== §2.7.2 查询药品列表 =====

    /** §2.7.2 药品列表 item */
    public record DrugListItem(
            String drugId,          // drug_no
            String genericName,
            Integer stockQuantity,  // current_stock
            String unit
    ) {}

    /** §2.7.2 药品详情响应（含批次/风险信息，供内部或详情接口扩展） */
    public record DrugDetailResponse(
            String drugId,          // drug_no
            String genericName,
            String tradeName,
            String specification,
            String dosageForm,
            String manufacturer,
            String approvalNo,
            String unit,
            Integer stockQuantity,  // current_stock
            Integer minStockThreshold,
            String status,
            List<String> contraindications,
            List<String> interactions
    ) {}

    // ===== §2.7.3 药品入库 =====

    @Data
    public static class InboundRequest {
        /** 批次号（同 batchNo 则累加 quantity） */
        @NotBlank(message = "批次号不能为空")
        private String batchNo;
        /** 入库数量 */
        @NotNull(message = "入库数量不能为空")
        @Positive(message = "入库数量必须为正")
        private Integer quantity;
        /** 单价 */
        private BigDecimal unitPrice;
        /** 生产日期（可选） */
        private LocalDate productionDate;
        /** 有效期（FEFO 排序依据） */
        @NotNull(message = "有效期不能为空")
        private LocalDate expireDate;
        /** 供应商 */
        private String supplier;
    }

    /** §2.7.3 入库响应（含 stockFlowId / currentStock） */
    public record InboundResponse(
            String stockFlowId,     // flow_no
            String drugId,          // drug_no
            Integer currentStock
    ) {}

    // ===== §2.7.4 药品出库 =====

    /** 出库选批策略：FEFO（按 expire_date ASC）/ FIFO（按 created_at ASC）/ MANUAL（暂不支持） */
    public enum BatchStrategy { FEFO, FIFO, MANUAL }

    @Data
    public static class OutboundRequest {
        /** 出库数量 */
        @NotNull(message = "出库数量不能为空")
        @Positive(message = "出库数量必须为正")
        private Integer quantity;
        /** 出库用途，如 PRESCRIPTION / DISPENSE / SCRAP */
        private String purpose;
        /** 关联业务记录 ID（病历号等），可为空 */
        private String relatedRecordId;
        /** 选批策略，默认 FEFO（医疗强制：近效期优先） */
        private BatchStrategy batchStrategy;
        /** 关联处方 ID（处方出库溯源，《修改建议》§5.1） */
        private Long prescriptionId;
        /** 关联处方明细 ID */
        private Long prescriptionItemId;
    }

    /** §2.7.4 出库响应（含 stockFlowId / currentStock；多批次出库返回首条 flowNo） */
    public record OutboundResponse(
            String stockFlowId,     // 首条 flow_no（多批次出库有多条 flow）
            String drugId,          // drug_no
            Integer currentStock
    ) {}

    // ===== §2.7.5 查询库存流水 =====

    /** §2.7.5 库存流水列表 item */
    public record StockFlowListItem(
            String stockFlowId,     // flow_no
            String type,            // INBOUND/OUTBOUND/ADJUST
            Integer quantity,
            String batchNo,         // 关联批次号（便于追溯，组装自 batch）
            LocalDateTime createdAt
    ) {}

    // ===== §2.7.6 查询库存预警 =====

    /** 预警类型：LOW_STOCK 库存不足 / NEAR_EXPIRY 近效期 */
    public enum AlertType { LOW_STOCK, NEAR_EXPIRY }

    /** §2.7.6 预警列表 item */
    public record AlertItem(
            String drugId,          // drug_no
            String drugName,        // generic_name
            String alertType,       // LOW_STOCK / NEAR_EXPIRY
            Integer currentStock,
            Integer threshold,
            String batchNo,         // 近效期预警才有：相关批次号
            LocalDate expireDate    // 近效期预警才有：批次过期日
    ) {}

    // ===== 内部接口（/internal/drugs）=====
    // 复用 com.medconsult.common.feign.dto.DrugRiskInfoDTO 作为 getRiskInfo 出参
    // BatchInfo 作为 ffeoBatches 出参 item

    /** 内部 FEFO 选批查询响应 item（不扣减，只查询） */
    public record BatchInfo(
            Long batchId,
            String batchNo,
            Integer quantity,
            LocalDate expireDate,
            String supplier
    ) {}
}
