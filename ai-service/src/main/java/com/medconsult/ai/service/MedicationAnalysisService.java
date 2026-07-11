package com.medconsult.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.persistence.entity.AiMedicationAnalysisEntity;
import com.medconsult.ai.persistence.mapper.AiMedicationAnalysisMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class MedicationAnalysisService {
    private static final String PROMPT = """
            You are a medication safety assistant.
            Use only the prescription, patient context, and controlledFunctionResult provided in the payload.
            Return JSON only:
            {
              "overallRiskLevel":"LOW|MEDIUM|HIGH|CRITICAL",
              "contraindicationRisks":[],
              "interactionRisks":[],
              "reminders":[],
              "functionTrace":[]
            }
            Do not change the prescription. Only provide risk reminders.
            """;

    private final OpenAiCompatibleClient llmClient;
    private final AiMedicationAnalysisMapper analysisMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;
    private final MedicationFunctionService functionService;

    public MedicationAnalysisService(OpenAiCompatibleClient llmClient,
                                     AiMedicationAnalysisMapper analysisMapper,
                                     AiProperties properties,
                                     AiCallLogService callLogService,
                                     MedicationFunctionService functionService) {
        this.llmClient = llmClient;
        this.analysisMapper = analysisMapper;
        this.properties = properties;
        this.callLogService = callLogService;
        this.functionService = functionService;
    }

    public MedicationAnalysisResponse analyze(MedicationAnalysisRequest request) {
        return analyze(request, null);
    }

    public MedicationAnalysisResponse analyzeStream(MedicationAnalysisRequest request, Consumer<String> tokenConsumer) {
        return analyze(request, tokenConsumer);
    }

    private MedicationAnalysisResponse analyze(MedicationAnalysisRequest request, Consumer<String> tokenConsumer) {
        long started = System.currentTimeMillis();
        MedicationFunctionService.FunctionResult functionResult = functionService.execute(request);
        Map<String, Object> fallback = resultMap(functionResult);
        Map<String, Object> llmPayload = new LinkedHashMap<>();
        llmPayload.put("prescriptions", request.prescriptions());
        llmPayload.put("patientContext", functionResult.patientContext());
        llmPayload.put("controlledFunctionResult", fallback);

        Map<String, Object> result = (tokenConsumer == null
                ? llmClient.chatJson(PROMPT, JsonUtils.toJson(llmPayload))
                : llmClient.chatJsonStream(PROMPT, JsonUtils.toJson(llmPayload), tokenConsumer))
                .map(node -> JsonUtils.MAPPER.convertValue(node, Map.class))
                .orElse(fallback);
        result.putIfAbsent("functionTrace", functionResult.functionTrace());

        String analysisNo = BusinessIds.next("MA");
        AiMedicationAnalysisEntity entity = new AiMedicationAnalysisEntity();
        entity.setAnalysisNo(analysisNo);
        entity.setPatientId(BusinessIds.numericId(request.patientId()));
        entity.setRecordId(BusinessIds.numericId(request.recordId()));
        entity.setPrescriptionId(BusinessIds.numericId(request.prescriptionId()));
        entity.setPrescriptions(JsonUtils.toJson(request.prescriptions()));
        entity.setOverallRiskLevel(string(result.getOrDefault("overallRiskLevel", functionResult.overallRiskLevel())));
        entity.setContraindicationRisks(JsonUtils.toJson(result.getOrDefault("contraindicationRisks", List.of())));
        entity.setInteractionRisks(JsonUtils.toJson(result.getOrDefault("interactionRisks", List.of())));
        entity.setReminders(JsonUtils.toJson(result.getOrDefault("reminders", List.of())));
        entity.setFunctionTrace(JsonUtils.toJson(result.getOrDefault("functionTrace", functionResult.functionTrace())));
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

    private static Map<String, Object> resultMap(MedicationFunctionService.FunctionResult functionResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallRiskLevel", functionResult.overallRiskLevel());
        result.put("contraindicationRisks", functionResult.contraindicationRisks());
        result.put("interactionRisks", functionResult.interactionRisks());
        result.put("reminders", functionResult.reminders());
        result.put("functionTrace", functionResult.functionTrace());
        return result;
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
