package com.medconsult.common.feign.dto;

/**
 * 调剂发药相关 DTO（架构文档 §6.2 / §2.3 内部接口 POST /internal/drugs/{drugNo}/outbound）。
 *
 * <p>medical-record-service 的 dispense 接口通过 {@link com.medconsult.common.feign.client.DrugFeignClient}
 * 调 drug-service 的内部出库端点。本 DTO 是跨服务契约，字段与 drug-service 的
 * {@code DrugDTO.OutboundRequest/OutboundResponse} 对齐（跨服务不直接依赖对方 DTO）。
 *
 * <p><b>quantity 类型</b>：drug-service 库存按整数件管理（OutboundRequest.quantity 是 Integer），
 * 故本请求 quantity 也是 int。medical-record 的 prescription_item.quantity 是 BigDecimal（支持 1.5 片），
 * dispense 时在 medical-record 侧做 BigDecimal→int 转换（拒绝小数）。
 */
public class DispenseDTO {

    /**
     * 内部出库请求（对应 drug-service POST /internal/drugs/{drugNo}/outbound 的 body）。
     *
     * <p>字段语义与 drug-service {@code DrugDTO.OutboundRequest} 完全一致，仅类名/包名独立
     * （避免跨服务 DTO 耦合）。
     */
    public record OutboundRequest(
            int quantity,               // 出库数量（整数件）
            String purpose,             // 出库用途：DISPENSE（调剂发药）/ PRESCRIPTION / SCRAP
            String relatedRecordId,     // 关联业务记录 ID（病历号等，可空）
            String batchStrategy,       // 选批策略：FEFO（默认）/ FIFO / MANUAL
            Long prescriptionId,        // 关联处方 ID（溯源，《修改建议》§5.1）
            Long prescriptionItemId     // 关联处方明细 ID（溯源到具体药品）
    ) {
        /** 调剂发药默认构造：FEFO + DISPENSE + 处方溯源 */
        public static OutboundRequest forDispense(int quantity, Long prescriptionId, Long prescriptionItemId) {
            return new OutboundRequest(quantity, "DISPENSE", null, "FEFO", prescriptionId, prescriptionItemId);
        }
    }

    /** 内部出库响应（对应 drug-service OutboundResponse） */
    public record OutboundResponse(
            String stockFlowId,         // 首条 flow_no（多批次出库有多条 flow）
            String drugId,              // drug_no
            Integer currentStock        // 出库后当前总库存
    ) {}
}
