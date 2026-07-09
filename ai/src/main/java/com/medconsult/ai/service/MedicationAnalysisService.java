package com.medconsult.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import com.medconsult.ai.persistence.entity.AiMedicationAnalysisEntity;
import com.medconsult.ai.persistence.mapper.AiMedicationAnalysisMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MedicationAnalysisService {
    private static final String PROMPT = """
            你是用药安全分析助手。请基于处方、过敏史、既往史和当前用药生成 JSON：
            {
              "overallRiskLevel":"LOW|MEDIUM|HIGH|CRITICAL",
              "contraindicationRisks":[],
              "interactionRisks":[],
              "reminders":[],
              "functionTrace":[]
            }
            不要自动修改处方，只给风险提醒。
            """;

    private final OpenAiCompatibleClient llmClient;
    private final AiMedicationAnalysisMapper analysisMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;

    public MedicationAnalysisService(OpenAiCompatibleClient llmClient, AiMedicationAnalysisMapper analysisMapper,
                                     AiProperties properties, AiCallLogService callLogService) {
        this.llmClient = llmClient;
        this.analysisMapper = analysisMapper;
        this.properties = properties;
        this.callLogService = callLogService;
    }

    public MedicationAnalysisResponse analyze(MedicationAnalysisRequest request) {
        long started = System.currentTimeMillis();
        Map<String, Object> fallback = ruleBased(request);
        Map<String, Object> result = llmClient.chatJson(PROMPT, JsonUtils.toJson(request))
                .map(node -> JsonUtils.MAPPER.convertValue(node, Map.class))
                .orElse(fallback);
        String analysisNo = BusinessIds.next("MA");
        AiMedicationAnalysisEntity entity = new AiMedicationAnalysisEntity();
        entity.setAnalysisNo(analysisNo);
        entity.setPatientId(BusinessIds.numericId(request.patientId()));
        entity.setRecordId(BusinessIds.numericId(request.recordId()));
        entity.setPrescriptions(JsonUtils.toJson(request.prescriptions()));
        entity.setOverallRiskLevel(string(result.getOrDefault("overallRiskLevel", "LOW")));
        entity.setContraindicationRisks(JsonUtils.toJson(result.getOrDefault("contraindicationRisks", List.of())));
        entity.setInteractionRisks(JsonUtils.toJson(result.getOrDefault("interactionRisks", List.of())));
        entity.setReminders(JsonUtils.toJson(result.getOrDefault("reminders", List.of())));
        entity.setFunctionTrace(JsonUtils.toJson(result.getOrDefault("functionTrace", List.of())));
        entity.setModelName(properties.llm().model());
        entity.setCreatedAt(LocalDateTime.now());
        analysisMapper.insert(entity);
        callLogService.success("MEDICATION_ANALYSIS", request.patientId(), analysisNo, properties.llm().model(),
                JsonUtils.toJson(request.prescriptions()), JsonUtils.toJson(result), entity.getOverallRiskLevel(),
                System.currentTimeMillis() - started);
        return new MedicationAnalysisResponse(
                analysisNo,
                entity.getOverallRiskLevel(),
                listMap(result.get("contraindicationRisks")),
                listMap(result.get("interactionRisks")),
                listMap(result.get("reminders")),
                Boolean.TRUE.equals(request.returnFunctionTrace()) ? listMap(result.get("functionTrace")) : null
        );
    }

    private Map<String, Object> ruleBased(MedicationAnalysisRequest request) {
        List<Map<String, Object>> contraindications = new ArrayList<>();
        List<Map<String, Object>> interactions = new ArrayList<>();
        List<Map<String, Object>> reminders = new ArrayList<>();
        for (PrescriptionDto prescription : request.prescriptions()) {
            if (containsAny(prescription.drugName(), "布洛芬", "阿司匹林")
                    && request.patientContext() != null
                    && request.patientContext().pastMedicalHistory() != null
                    && request.patientContext().pastMedicalHistory().stream().anyMatch(item -> item.contains("胃"))) {
                contraindications.add(Map.of(
                        "drugName", prescription.drugName(),
                        "riskLevel", "MEDIUM",
                        "description", "胃部疾病患者使用 NSAIDs 需谨慎。",
                        "suggestion", "建议医生评估胃肠道风险。"
                ));
            }
            reminders.add(Map.of(
                    "drugName", prescription.drugName(),
                    "reminder", "请按医嘱剂量和频次使用，若出现不适及时联系医生。"
            ));
        }
        if (drugNames(request).contains("布洛芬") && drugNames(request).contains("阿司匹林")) {
            interactions.add(Map.of(
                    "drugA", "布洛芬",
                    "drugB", "阿司匹林",
                    "riskLevel", "MEDIUM",
                    "description", "合用可能增加胃肠道不良反应风险。"
            ));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallRiskLevel", contraindications.isEmpty() && interactions.isEmpty() ? "LOW" : "MEDIUM");
        result.put("contraindicationRisks", contraindications);
        result.put("interactionRisks", interactions);
        result.put("reminders", reminders);
        result.put("functionTrace", List.of(Map.of("functionName", "localMedicationRules", "resultSummary", "完成本地用药规则分析")));
        return result;
    }

    private static String drugNames(MedicationAnalysisRequest request) {
        return request.prescriptions().stream().map(PrescriptionDto::drugName).reduce("", (a, b) -> a + " " + b);
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text != null && text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<Map<String, Object>> listMap(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
        }
        if (value instanceof JsonNode node) {
            return JsonUtils.MAPPER.convertValue(node, List.class);
        }
        return List.of();
    }
}
