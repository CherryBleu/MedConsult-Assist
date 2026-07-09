package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.ai.common.PageResult;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.persistence.entity.AiCallLogEntity;
import com.medconsult.ai.persistence.mapper.AiCallLogMapper;
import com.medconsult.ai.util.BusinessIds;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AiCallLogService {
    private final AiCallLogMapper callLogMapper;

    public AiCallLogService(AiCallLogMapper callLogMapper) {
        this.callLogMapper = callLogMapper;
    }

    public void success(String type, String patientId, String relatedId, String model, String requestSummary,
                        String responseSummary, String riskLevel, long latencyMs) {
        save(type, patientId, relatedId, model, requestSummary, responseSummary, riskLevel, "SUCCESS", latencyMs, null);
    }

    public void failed(String type, String patientId, String relatedId, String model, String requestSummary,
                       long latencyMs, Exception ex) {
        save(type, patientId, relatedId, model, requestSummary, "", null, "FAILED", latencyMs, ex.getMessage());
    }

    public PageResult<CallLogItem> list(String patientId, String type, long page, long pageSize) {
        LambdaQueryWrapper<AiCallLogEntity> wrapper = new LambdaQueryWrapper<AiCallLogEntity>()
                .eq(type != null && !type.isBlank(), AiCallLogEntity::getCallType, type)
                .eq(patientId != null && !patientId.isBlank(), AiCallLogEntity::getPatientId, BusinessIds.numericId(patientId))
                .orderByDesc(AiCallLogEntity::getCreatedAt);
        Page<AiCallLogEntity> result = callLogMapper.selectPage(Page.of(page, pageSize), wrapper);
        return new PageResult<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(item -> new CallLogItem(
                                item.getLogNo(),
                                item.getCallType(),
                                BusinessIds.businessOrEmpty(patientId),
                                item.getRelatedId(),
                                item.getModelName(),
                                item.getLatencyMs(),
                                item.getRiskLevel()
                        ))
                        .toList()
        );
    }

    private void save(String type, String patientId, String relatedId, String model, String requestSummary,
                      String responseSummary, String riskLevel, String status, long latencyMs, String errorMessage) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setLogNo(BusinessIds.next("AILOG"));
        entity.setCallType(type);
        entity.setPatientId(BusinessIds.numericId(patientId));
        entity.setRelatedId(relatedId);
        entity.setModelName(model);
        entity.setKnowledgeSource(type.equals("SYMPTOM_CHAT") || type.equals("TRIAGE") ? "DISEASE_JSON" : null);
        entity.setRequestSummary(truncate(requestSummary, 500));
        entity.setResponseSummary(truncate(responseSummary, 500));
        entity.setRiskLevel(riskLevel);
        entity.setStatus(status);
        entity.setLatencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs));
        entity.setErrorMessage(truncate(errorMessage, 1000));
        entity.setCreatedAt(LocalDateTime.now());
        callLogMapper.insert(entity);
    }

    private static String truncate(String value, int length) {
        if (value == null) {
            return null;
        }
        return value.length() <= length ? value : value.substring(0, length);
    }
}
