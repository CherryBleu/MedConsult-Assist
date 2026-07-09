package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.common.BizException;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextResponse;
import com.medconsult.ai.dto.AiModels.SummaryConfirmRequest;
import com.medconsult.ai.dto.AiModels.SummaryConfirmResponse;
import com.medconsult.ai.persistence.entity.AiMedicalSummaryEntity;
import com.medconsult.ai.persistence.entity.MedicalRecordEntity;
import com.medconsult.ai.persistence.mapper.AiMedicalSummaryMapper;
import com.medconsult.ai.persistence.mapper.MedicalRecordMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class SummaryService {
    private static final String SUMMARY_PROMPT = """
            你是电子病历摘要助手。请根据病历内容生成 JSON，字段包含 chiefComplaint、diagnosis、treatmentPlan、medications、followUpAdvice。
            不要新增病历中没有依据的重要事实；内容不足时字段可为空数组或空字符串。
            """;

    private final OpenAiCompatibleClient llmClient;
    private final MedicalRecordMapper medicalRecordMapper;
    private final AiMedicalSummaryMapper summaryMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;

    public SummaryService(OpenAiCompatibleClient llmClient, MedicalRecordMapper medicalRecordMapper,
                          AiMedicalSummaryMapper summaryMapper, AiProperties properties, AiCallLogService callLogService) {
        this.llmClient = llmClient;
        this.medicalRecordMapper = medicalRecordMapper;
        this.summaryMapper = summaryMapper;
        this.properties = properties;
        this.callLogService = callLogService;
    }

    public MedicalRecordSummaryResponse summarizeRecord(MedicalRecordSummaryRequest request) {
        MedicalRecordEntity record = medicalRecordMapper.selectOne(new LambdaQueryWrapper<MedicalRecordEntity>()
                .eq(MedicalRecordEntity::getRecordNo, request.recordId())
                .last("limit 1"));
        if (record == null) {
            throw new BizException(404001, "病历不存在");
        }
        Map<String, Object> summary = summarizeText(recordText(record));
        String summaryNo = BusinessIds.next("SUM");
        AiMedicalSummaryEntity entity = new AiMedicalSummaryEntity();
        entity.setSummaryNo(summaryNo);
        entity.setRecordId(record.getId());
        entity.setSummaryType(request.summaryType() == null ? "STRUCTURED" : request.summaryType());
        entity.setSummaryContent(JsonUtils.toJson(summary));
        entity.setStatus(Boolean.TRUE.equals(request.saveDraft()) ? "AI_DRAFT" : "GENERATED");
        entity.setModelName(properties.llm().model());
        entity.setPromptVersion("v1");
        entity.setGeneratedAt(LocalDateTime.now());
        summaryMapper.insert(entity);
        callLogService.success("MEDICAL_RECORD_SUMMARY", String.valueOf(record.getPatientId()), summaryNo,
                properties.llm().model(), request.recordId(), JsonUtils.toJson(summary), null, 0);
        return new MedicalRecordSummaryResponse(summaryNo, request.recordId(), summary, entity.getStatus());
    }

    public MedicalRecordSummaryTextResponse summarizeTextRequest(MedicalRecordSummaryTextRequest request) {
        return new MedicalRecordSummaryTextResponse(summarizeText(request.recordText()), "GENERATED");
    }

    public SummaryConfirmResponse confirm(String recordId, SummaryConfirmRequest request) {
        AiMedicalSummaryEntity entity = summaryMapper.selectOne(new LambdaQueryWrapper<AiMedicalSummaryEntity>()
                .eq(AiMedicalSummaryEntity::getSummaryNo, request.summaryId())
                .last("limit 1"));
        if (entity == null) {
            throw new BizException(404001, "摘要不存在");
        }
        entity.setSummaryContent(JsonUtils.toJson(request.confirmedSummary()));
        entity.setConfirmedBy(BusinessIds.numericId(request.confirmedBy()));
        entity.setConfirmedAt(LocalDateTime.now());
        entity.setStatus("CONFIRMED");
        summaryMapper.updateById(entity);
        return new SummaryConfirmResponse(request.summaryId(), recordId, "CONFIRMED");
    }

    private Map<String, Object> summarizeText(String text) {
        JsonNode json = llmClient.chatJson(SUMMARY_PROMPT, text).orElseGet(() -> JsonUtils.readTree("""
                {"chiefComplaint":"","diagnosis":[],"treatmentPlan":"","medications":[],"followUpAdvice":""}
                """));
        return JsonUtils.MAPPER.convertValue(json, Map.class);
    }

    private static String recordText(MedicalRecordEntity record) {
        return "主诉：" + safe(record.getChiefComplaint())
                + "\n现病史：" + safe(record.getPresentIllness())
                + "\n既往史：" + safe(record.getPastHistory())
                + "\n体格检查：" + safe(record.getPhysicalExam())
                + "\n初步诊断：" + safe(record.getInitialDiagnosis())
                + "\n最终诊断：" + safe(record.getFinalDiagnosis())
                + "\n处方：" + safe(record.getPrescriptions())
                + "\n医嘱：" + safe(record.getDoctorAdvice());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
