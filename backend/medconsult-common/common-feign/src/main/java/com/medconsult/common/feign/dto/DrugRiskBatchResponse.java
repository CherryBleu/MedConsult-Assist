package com.medconsult.common.feign.dto;

import java.util.List;

/**
 * 批量药品风险查询响应。
 *
 * @param items 命中的药品风险信息，按请求中首次出现的 drugId 顺序返回
 * @param missingDrugIds 未找到的药品主键，按请求中首次出现的 drugId 顺序返回
 */
public record DrugRiskBatchResponse(
        List<DrugRiskInfoDTO> items,
        List<Long> missingDrugIds
) {
}
