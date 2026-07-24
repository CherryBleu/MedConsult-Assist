package com.medconsult.drug.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "创建药品请求")
    public static class CreateDrugRequest {
        /** 通用名，如 硝苯地平 */
        @NotBlank(message = "通用名不能为空")
        @Size(max = 100, message = "通用名长度不能超过 100")
        @Schema(description = "通用名")
        private String genericName;
        /** 商品名，如 拜新同 */
        @Size(max = 100, message = "商品名长度不能超过 100")
        @Schema(description = "商品名")
        private String tradeName;
        /** 规格，如 30mg*7片 */
        @Size(max = 100, message = "规格长度不能超过 100")
        @Schema(description = "规格")
        private String specification;
        /** 剂型，如 控释片 */
        @Size(max = 50, message = "剂型长度不能超过 50")
        @Schema(description = "剂型")
        private String dosageForm;
        /** 生产厂家 */
        @Size(max = 200, message = "生产厂家长度不能超过 200")
        @Schema(description = "生产厂家")
        private String manufacturer;
        /** 批准文号（国药准字） */
        @Size(max = 100, message = "批准文号长度不能超过 100")
        @Schema(description = "批准文号")
        private String approvalNo;
        /** 单位，如 盒 */
        @Size(max = 20, message = "单位长度不能超过 20")
        @Schema(description = "单位")
        private String unit;
        /** 最低库存阈值 */
        @NotNull(message = "最低库存阈值不能为空")
        @Min(value = 0, message = "最低库存阈值不能为负")
        @Schema(description = "最低库存阈值")
        private Integer minStockThreshold;
        /** 禁忌信息（简化为字符串数组，如 ["严重低血压","心源性休克"]） */
        @Schema(description = "禁忌信息")
        private List<String> contraindications;
        /** 相互作用信息（简化为字符串数组） */
        @Schema(description = "相互作用信息")
        private List<String> interactions;
    }

    /** §2.7.1 / §2.7.2 复用：药品摘要响应（含 drugNo / genericName / status） */
    @Schema(description = "创建药品响应")
    public record DrugSummary(
            @Schema(description = "药品编号") String drugId,          // drug_no
            @Schema(description = "通用名") String genericName,
            @Schema(description = "状态") String status
    ) {}

    // ===== §2.7.2 查询药品列表 =====

    /** §2.7.2 药品列表 item */
    @Schema(description = "药品列表项")
    public record DrugListItem(
            @Schema(description = "药品编号") String drugId,          // drug_no
            @Schema(description = "通用名") String genericName,
            @Schema(description = "规格") String specification,
            @Schema(description = "生产厂家") String manufacturer,
            @Schema(description = "库存数量") Integer stockQuantity,  // current_stock
            @Schema(description = "单位") String unit,
            @Schema(description = "当前可用批次单价") BigDecimal unitPrice,
            @Schema(description = "状态：ACTIVE/DISABLED") String status
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
    @Schema(description = "药品入库请求")
    public static class InboundRequest {
        /** 批次号（同 batchNo 则累加 quantity） */
        @NotBlank(message = "批次号不能为空")
        @Size(max = 64, message = "批次号长度不能超过 64")
        @Schema(description = "批次号")
        private String batchNo;
        /** 入库数量 */
        @NotNull(message = "入库数量不能为空")
        @Positive(message = "入库数量必须为正")
        @Schema(description = "入库数量")
        private Integer quantity;
        /** 单价 */
        @Schema(description = "单价")
        private BigDecimal unitPrice;
        /** 生产日期（可选） */
        @Schema(description = "生产日期")
        private LocalDate productionDate;
        /** 有效期（FEFO 排序依据；必须为未来日期，禁止入库已过期批次） */
        @NotNull(message = "有效期不能为空")
        @Future(message = "有效期必须为未来日期（禁止入库已过期批次）")
        @Schema(description = "过期日期")
        private LocalDate expireDate;
        /** 供应商 */
        @Size(max = 200, message = "供应商长度不能超过 200")
        @Schema(description = "供应商")
        private String supplier;
    }

    /** §2.7.3 入库响应（含 stockFlowId / currentStock） */
    @Schema(description = "药品入库响应")
    public record InboundResponse(
            @Schema(description = "库存流水编号") String stockFlowId,     // flow_no
            @Schema(description = "药品编号") String drugId,          // drug_no
            @Schema(description = "当前库存") Integer currentStock
    ) {}

    // ===== §2.7.4 药品出库 =====

    /** 出库选批策略：FEFO（按 expire_date ASC）/ FIFO（按 created_at ASC）/ MANUAL（暂不支持） */
    @Schema(description = "批次出库策略")
    public enum BatchStrategy { FEFO, FIFO, MANUAL }

    @Data
    @Schema(description = "药品出库请求")
    public static class OutboundRequest {
        /** 出库数量 */
        @NotNull(message = "出库数量不能为空")
        @Positive(message = "出库数量必须为正")
        @Schema(description = "出库数量")
        private Integer quantity;
        /** 出库用途：PRESCRIPTION / DISPENSE / SCRAP（不传默认 DISPENSE） */
        @Pattern(regexp = "^$|^(PRESCRIPTION|DISPENSE|SCRAP)$",
                message = "出库用途须为 PRESCRIPTION / DISPENSE / SCRAP")
        @Schema(description = "出库用途：PRESCRIPTION / DISPENSE / SCRAP")
        private String purpose;
        /** 关联业务记录 ID（病历号等），可为空 */
        @Schema(description = "关联记录编号")
        private String relatedRecordId;
        /** 选批策略，默认 FEFO（医疗强制：近效期优先） */
        @Schema(description = "批次策略")
        private BatchStrategy batchStrategy;
        /** 关联处方 ID（处方出库溯源，《修改建议》§5.1） */
        @Schema(description = "处方ID")
        private Long prescriptionId;
        /** 关联处方明细 ID */
        @Schema(description = "处方明细ID")
        private Long prescriptionItemId;
    }

    /** §2.7.4 出库响应（含 stockFlowId / currentStock；多批次出库返回首条 flowNo） */
    @Schema(description = "药品出库响应")
    public record OutboundResponse(
            @Schema(description = "库存流水编号") String stockFlowId,     // 首条 flow_no（多批次出库有多条 flow）
            @Schema(description = "药品编号") String drugId,          // drug_no
            @Schema(description = "当前库存") Integer currentStock
    ) {}

    // ===== §2.7.5 查询库存流水 =====

    /** §2.7.5 库存流水列表 item */
    @Schema(description = "库存流水列表项")
    public record StockFlowListItem(
            @Schema(description = "流水编号") String stockFlowId,     // flow_no
            @Schema(description = "类型：INBOUND / OUTBOUND") String type,            // INBOUND/OUTBOUND/ADJUST
            @Schema(description = "数量") Integer quantity,
            @Schema(description = "批次号") String batchNo,         // 关联批次号（便于追溯，组装自 batch）
            @Schema(description = "创建时间") LocalDateTime createdAt
    ) {}

    /** §2.7.5b 全局库存流水列表 item（管理员视角，含药品名/编号）。 */
    @Schema(description = "全局库存流水列表项")
    public record GlobalStockFlowListItem(
            @Schema(description = "流水编号") String stockFlowId,     // flow_no
            @Schema(description = "类型：INBOUND / OUTBOUND") String type,
            @Schema(description = "数量") Integer quantity,
            @Schema(description = "批次号") String batchNo,
            @Schema(description = "药品编号") String drugNo,
            @Schema(description = "药品名称") String drugName,
            @Schema(description = "创建时间") LocalDateTime createdAt
    ) {}

    // ===== §2.7.6 查询库存预警 =====

    /** 预警类型：LOW_STOCK 库存不足 / NEAR_EXPIRY 近效期 */
    public enum AlertType { LOW_STOCK, NEAR_EXPIRY }

    /** §2.7.6 预警列表 item */
    @Schema(description = "库存预警项")
    public record AlertItem(
            @Schema(description = "药品编号") String drugId,          // drug_no
            @Schema(description = "药品名称") String drugName,        // generic_name
            @Schema(description = "规格") String specification,
            @Schema(description = "单位") String unit,
            @Schema(description = "预警类型：LOW_STOCK / NEAR_EXPIRY") String alertType,       // LOW_STOCK / NEAR_EXPIRY
            @Schema(description = "当前库存") Integer currentStock,
            @Schema(description = "阈值") Integer threshold,
            @Schema(description = "批次号") String batchNo,         // 近效期预警才有：相关批次号
            @Schema(description = "过期日期") LocalDate expireDate,   // 近效期预警才有：批次过期日
            @Schema(description = "剩余天数") Long daysLeft
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
