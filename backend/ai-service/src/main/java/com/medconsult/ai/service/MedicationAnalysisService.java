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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class MedicationAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(MedicationAnalysisService.class);

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

    /**
     * 用药分析结果缓存：按处方内容 hash 缓存，30 分钟 TTL。
     * 相同处方组合（药名+剂量+频次）命中缓存，跳过 LLM 调用直接返回。
     * 缓存 key 只含处方内容，不含 patientId（同一处方对不同患者风险提示相同——
     * 患者特异性风险由 functionService 的禁忌检测动态注入，不随缓存固化）。
     */
    private static final String MED_CACHE_PREFIX = "medconsult:ai:medication:rx:";
    private static final Duration MED_CACHE_TTL = Duration.ofMinutes(30);

    private final OpenAiCompatibleClient llmClient;
    private final AiMedicationAnalysisMapper analysisMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;
    private final MedicationFunctionService functionService;
    private final StringRedisTemplate redisTemplate;

    public MedicationAnalysisService(OpenAiCompatibleClient llmClient,
                                     AiMedicationAnalysisMapper analysisMapper,
                                     AiProperties properties,
                                     AiCallLogService callLogService,
                                     MedicationFunctionService functionService,
                                     StringRedisTemplate redisTemplate) {
        this.llmClient = llmClient;
        this.analysisMapper = analysisMapper;
        this.properties = properties;
        this.callLogService = callLogService;
        this.functionService = functionService;
        this.redisTemplate = redisTemplate;
    }

    public MedicationAnalysisResponse analyze(MedicationAnalysisRequest request) {
        return analyze(request, null);
    }

    public MedicationAnalysisResponse analyzeStream(MedicationAnalysisRequest request, Consumer<String> tokenConsumer) {
        return analyze(request, tokenConsumer);
    }

    private MedicationAnalysisResponse analyze(MedicationAnalysisRequest request, Consumer<String> tokenConsumer) {
        // 缓存命中检查（仅非流式场景缓存；流式需逐 token 返回，不缓存）
        String cacheKey = null;
        if (tokenConsumer == null) {
            cacheKey = MED_CACHE_PREFIX + prescriptionsHash(request.prescriptions());
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null && !cached.isBlank()) {
                    log.info("[AI-CACHE] medication hit cache: prescriptionsHash={}", prescriptionsHash(request.prescriptions()));
                    return JsonUtils.MAPPER.readValue(cached, MedicationAnalysisResponse.class);
                }
            } catch (Exception e) {
                log.warn("[AI-CACHE] medication cache read failed, fallback to LLM: {}", e.getMessage());
            }
        }

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
        MedicationAnalysisResponse response = new MedicationAnalysisResponse(
                analysisNo,
                entity.getOverallRiskLevel(),
                listMap(result.get("contraindicationRisks")),
                listMap(result.get("interactionRisks")),
                listMap(result.get("reminders")),
                Boolean.TRUE.equals(request.returnFunctionTrace()) ? listMap(result.get("functionTrace")) : null
        );
        // 写入缓存（仅非流式场景）
        if (cacheKey != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(response), MED_CACHE_TTL);
            } catch (Exception e) {
                log.warn("[AI-CACHE] medication cache write failed: {}", e.getMessage());
            }
        }
        return response;
    }

    /**
     * 处方内容 hash（SHA-256，取前 32 字符）。
     * <p>同一处方组合（药名+剂量+频次+给药途径+天数）产生相同 hash，
     * 作为缓存 key 后缀。不含 patientId——患者特异性风险由 functionService 动态注入。
     */
    private static String prescriptionsHash(List<com.medconsult.ai.dto.AiModels.PrescriptionDto> prescriptions) {
        String json = JsonUtils.toJson(prescriptions);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (Exception e) {
            // SHA-256 一定存在，理论上不会进这里；降级用 hashCode
            return Integer.toHexString(json.hashCode());
        }
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
