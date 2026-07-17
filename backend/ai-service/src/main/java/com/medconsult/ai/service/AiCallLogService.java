package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.mq.AiCallLogMessage;
import com.medconsult.ai.persistence.entity.AiCallLogEntity;
import com.medconsult.ai.persistence.mapper.AiCallLogMapper;
import com.medconsult.ai.security.AiHeaders;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.common.mq.MqConstants;
import org.slf4j.MDC;
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
                riskLevel, "SUCCESS", latencyMs, null, callerService(), currentTraceId(), triggerUserId()));
    }

    public void failed(String type, String patientId, String relatedId, String model, String requestSummary,
                       long latencyMs, Exception ex) {
        enqueueOrSave(new AiCallLogMessage(type, patientId, relatedId, model, requestSummary, "",
                null, "FAILED", latencyMs, ex.getMessage(), callerService(), currentTraceId(), triggerUserId()));
    }

    public void saveFromMessage(AiCallLogMessage message) {
        try {
            save(message.type(), message.patientId(), message.relatedId(), message.model(), message.requestSummary(),
                    message.responseSummary(), message.riskLevel(), message.status(), message.latencyMs(),
                    message.errorMessage(), message.callerService(), message.traceId(), message.triggerUserId(),
                    message.requestId());
        } catch (DuplicateKeyException ignored) {
            // RabbitMQ can redeliver an already persisted log; unique keys make the write idempotent.
        }
    }

    public PageResult<CallLogItem> list(String patientId, String type, int page, int pageSize) {
        // 越权防护（IDOR，架构 §4.3 SELF）：PATIENT 身份时强制限定为本人 patientId，
        // 忽略入参 patientId——调用日志含 requestSummary（患者症状/用药文本等敏感医疗数据），
        // 不得让患者随意拉取他人日志。DOCTOR/管理员可按入参或全量查询。
        Long scopedPatientId = resolvePatientScope(patientId);
        // 分页参数上下界校验，防止 pageSize 传极大值拖垮查询
        int safePage = page < 1 ? 1 : page;
        int safeSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        LambdaQueryWrapper<AiCallLogEntity> wrapper = new LambdaQueryWrapper<AiCallLogEntity>()
                .eq(type != null && !type.isBlank(), AiCallLogEntity::getCallType, type)
                .eq(scopedPatientId != null, AiCallLogEntity::getPatientId, scopedPatientId)
                .orderByDesc(AiCallLogEntity::getCreatedAt);
        Page<AiCallLogEntity> result = callLogMapper.selectPage(Page.of(safePage, safeSize), wrapper);
        return PageResult.of(
                (int) result.getCurrent(),
                (int) result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(item -> new CallLogItem(
                                item.getLogNo(),
                                item.getCallType(),
                                // 每条日志的真实 patientId，不能用查询参数（不带 patientId 查询时会被错填成空串）
                                BusinessIds.businessOrEmpty(String.valueOf(item.getPatientId())),
                                item.getRelatedId(),
                                item.getModelName(),
                                item.getLatencyMs(),
                                item.getRiskLevel(),
                                // 管理后台调用日志页字段（对齐 AiCallLog.vue 列定义）
                                item.getLogNo(),
                                item.getCallType(),
                                item.getModelName(),
                                item.getTriggerUserId() == null ? "" : BusinessIds.businessOrEmpty(String.valueOf(item.getTriggerUserId())),
                                item.getRequestSummary() == null ? 0 : item.getRequestSummary().length(),
                                item.getResponseSummary() == null ? 0 : item.getResponseSummary().length(),
                                item.getLatencyMs(),
                                item.getStatus(),
                                item.getCreatedAt()
                        ))
                        .toList()
        );
    }

    /**
     * 调用日志的 patient 作用域解析（IDOR 防护，对齐 ImagingDetectionService）：
     * PATIENT 身份强制限定为本人 patientId（忽略入参）；DOCTOR/管理员按入参或全量；匿名拒绝。
     * 调用日志含 requestSummary（患者症状/用药文本等敏感数据），不得越权读取他人日志。
     */
    private Long resolvePatientScope(String patientId) {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null || !payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(payload)) {
            Long selfPatientId = payload.patientId();
            if (selfPatientId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联患者档案，无法查询调用日志");
            }
            return selfPatientId;
        }
        return StringUtils.hasText(patientId) ? BusinessIds.numericId(patientId) : null;
    }

    /** 是否 PATIENT 主角色 */
    private static boolean isPatient(JwtPayload p) {
        if (p == null) return false;
        if ("PATIENT".equals(p.primaryRole())) return true;
        return p.roles() != null && p.roles().contains("PATIENT");
    }

    private void enqueueOrSave(AiCallLogMessage message) {
        try {
            // 调用日志发到 log exchange（与审计日志共享，架构文档 §4.2），路由键 ai.calllog
            rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_LOG, MqConstants.RK_AI_CALL_LOG, message);
        } catch (RuntimeException ex) {
            saveFromMessage(message);
        }
    }

    private void save(String type, String patientId, String relatedId, String model, String requestSummary,
                      String responseSummary, String riskLevel, String status, long latencyMs, String errorMessage,
                      String callerService, String traceId, String triggerUserId, String requestId) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setLogNo(BusinessIds.next("AILOG"));
        entity.setCallType(type);
        entity.setPatientId(BusinessIds.numericId(patientId));
        entity.setRelatedId(relatedId);
        entity.setCallerService(StringUtils.hasText(callerService) ? callerService : "api");
        entity.setTriggerUserId(BusinessIds.numericId(triggerUserId));
        entity.setTraceId(StringUtils.hasText(traceId) ? traceId : currentTraceId());
        entity.setCostTokens(0);
        entity.setRequestId(StringUtils.hasText(requestId) ? requestId : BusinessIds.next("REQ"));
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
        String caller = attributes.getRequest().getHeader(AiHeaders.CALLER_SERVICE);
        return StringUtils.hasText(caller) ? caller : "api";
    }

    private static String triggerUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getHeader(AiHeaders.TRIGGER_USER_ID);
    }

    /**
     * 当前 traceId：优先 MDC（common-web TraceIdFilter 在请求阶段已填充），
     * 无则本地生成（MQ 消费等非 web 场景）。
     */
    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return "trace-" + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static String truncate(String value, int length) {
        if (value == null) {
            return null;
        }
        return value.length() <= length ? value : value.substring(0, length);
    }
}
