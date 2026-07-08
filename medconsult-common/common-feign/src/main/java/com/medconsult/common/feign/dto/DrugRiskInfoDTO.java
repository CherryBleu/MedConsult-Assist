package com.medconsult.common.feign.dto;

import java.util.List;

/**
 * 药品风险信息 DTO（架构文档 §2.3 GET /internal/drugs/{id}/risk-info）。
 *
 * <p>drug-service 提供给 ai-service Function Calling 用的结构化禁忌/相互作用
 * （《修改建议》§4.4：drug.contraindications/interactions 改 JSON 结构化）。
 */
public record DrugRiskInfoDTO(
        Long drugId,
        String genericName,
        List<Contraindication> contraindications,
        List<Interaction> interactions
) {
    /** 禁忌项：条件 + 等级（ABSOLUTE 绝对 / RELATIVE 相对） */
    public record Contraindication(String condition, String level, String note) {}

    /** 相互作用：对方药品编码 + 效应 + 等级 */
    public record Interaction(String drugCode, String effect, String level) {}

    public static DrugRiskInfoDTO safe(Long drugId, String name) {
        return new DrugRiskInfoDTO(drugId, name, List.of(), List.of());
    }
}
