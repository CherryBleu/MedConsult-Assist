package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.mq.AiCallLogMessage;
import com.medconsult.ai.mq.AiMqConfig;
import com.medconsult.ai.persistence.entity.AiCallLogEntity;
import com.medconsult.ai.persistence.mapper.AiCallLogMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.web.RequestContext;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
public class AiCallLogService {
    private final AiCallLogMapper callLogMapper;
    private final RabbitTemplate rabbitTemplate;
    private final AiProperties properties;

    public AiCallLogService(AiCallLogMapper callLogMapper, RabbitTemplate rabbitTemplate, AiProperties properties) {
        this.callLogMapper = callLogMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void success(String type, String patientId, String relatedId, String model, String requestSummary,
                        String responseSummary, String riskLevel, long latencyMs) {
        enqueueOrSave(new AiCallLogMessage(type, patientId, relatedId, model, requestSummary, responseSummary,
                riskLevel, "SUCCESS", latencyMs, null, callerService(), RequestContext.traceId(), triggerUserId()));
    }

    public void failed(String type, String patientId, String relatedId, String model, String requestSummary,
                       long latencyMs, Exception ex) {
        enqueueOrSave(new AiCallLogMessage(type, patientId, relatedId, model, requestSummary, "",
                null, "FAILED", latencyMs, ex.getMessage(), callerService(), RequestContext.traceId(), triggerUserId()));
    }

    public void saveFromMessage(AiCallLogMessage message) {
        try {
            save(message.type(), message.patientId(), message.relatedId(), message.model(), message.requestSummary(),
                    message.responseSummary(), message.riskLevel(), message.status(), message.latencyMs(),
                    message.errorMessage(), message.callerService(), message.traceId(), message.triggerUserId());
        } catch (DuplicateKeyException ignored) {
            // RabbitMQ can redeliver an already persisted log; unique keys make the write idempotent.
        }
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

    private void enqueueOrSave(AiCallLogMessage message) {
        try {
            rabbitTemplate.convertAndSend(properties.mq().exchange(), AiMqConfig.CALL_LOG_ROUTING_KEY, message);
        } catch (RuntimeException ex) {
            saveFromMessage(message);
        }
    }

    private void save(String type, String patientId, String relatedId, String model, String requestSummary,
                      String responseSummary, String riskLevel, String status, long latencyMs, String errorMessage,
                      String callerService, String traceId, String triggerUserId) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setLogNo(BusinessIds.next("AILOG"));
        entity.setCallType(type);
        entity.setPatientId(BusinessIds.numericId(patientId));
        entity.setRelatedId(relatedId);
        entity.setCallerService(StringUtils.hasText(callerService) ? callerService : "api");
        entity.setTriggerUserId(BusinessIds.numericId(triggerUserId));
        entity.setTraceId(StringUtils.hasText(traceId) ? traceId : RequestContext.traceId());
        entity.setCostTokens(0);
        entity.setRequestId(BusinessIds.next("REQ"));
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

    private static String callerService() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "api";
        }
        String caller = attributes.getRequest().getHeader(RequestContext.CALLER_SERVICE_HEADER);
        return StringUtils.hasText(caller) ? caller : "api";
    }

    private static String triggerUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getHeader(RequestContext.TRIGGER_USER_ID_HEADER);
    }

    private static String truncate(String value, int length) {
        if (value == null) {
            return null;
        }
        return value.length() <= length ? value : value.substring(0, length);
    }
}
