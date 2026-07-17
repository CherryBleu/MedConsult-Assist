package com.medconsult.common.feign.dto;

import java.util.List;

/**
 * 批量药品风险查询请求。
 *
 * <p>仅供内部 Feign 调用，调用方传入药品主键列表；drug-service 负责去重、限流和缺失项标记。
 */
public record DrugRiskBatchRequest(List<Long> drugIds) {
}
