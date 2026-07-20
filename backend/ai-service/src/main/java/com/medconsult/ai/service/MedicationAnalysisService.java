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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
     * 用药分析结果缓存：按“患者 + 患者上下文 + 处方内容” hash 缓存，30 分钟 TTL。
     *
     * <p>用药风险具备患者特异性（过敏史、既往史、当前用药会改变禁忌/相互作用），
     * 整响应缓存绝不能只按处方命中，否则同一处方可能跨患者复用风险结论。
     */
    private static final String MED_CACHE_PREFIX = "medconsult:ai:medication:v2:";
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
        long started = System.currentTimeMillis();
        // 缓存命中检查（仅非流式场景缓存；流式需逐 token 返回，不缓存）。
        // key 已纳入 patientId/patientContext，避免同处方跨患者复用风险结论。
        String cacheKey = null;
        if (tokenConsumer == null) {
            cacheKey = medicationCacheKeyFor(request);
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null && !cached.isBlank()) {
                    log.info("[AI-CACHE] medication hit cache: key={}", cacheKey);
                    MedicationAnalysisResponse cachedResponse =
                            JsonUtils.MAPPER.readValue(cached, MedicationAnalysisResponse.class);
                    logCacheHit(request, cachedResponse, started);
                    return cachedResponse;
                }
            } catch (Exception e) {
                log.warn("[AI-CACHE] medication cache read failed, fallback to LLM: {}", e.getMessage());
            }
        }

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
        result = enforceControlledFunctionResult(result, functionResult);

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

    static String medicationCacheKeyFor(MedicationAnalysisRequest request) {
        Map<String, Object> cacheIdentity = new LinkedHashMap<>();
        cacheIdentity.put("schema", "medication-cache-v2");
        cacheIdentity.put("patientId", request.patientId());
        cacheIdentity.put("patientContext", request.patientContext());
        cacheIdentity.put("prescriptions", request.prescriptions());
        return MED_CACHE_PREFIX + stableHash(JsonUtils.toJson(cacheIdentity));
    }

    private void logCacheHit(MedicationAnalysisRequest request, MedicationAnalysisResponse cachedResponse, long started) {
        try {
            callLogService.success("MEDICATION_ANALYSIS", request.patientId(), cachedResponse.analysisId(),
                    properties.llm().model(), JsonUtils.toJson(request.prescriptions()),
                    JsonUtils.toJson(cachedResponse), cachedResponse.overallRiskLevel(),
                    System.currentTimeMillis() - started, AiCallLogService.AiCallLogMetrics.cacheHitMetrics());
        } catch (RuntimeException ex) {
            log.warn("[AI-CACHE] medication cache-hit log failed: {}", ex.getMessage());
        }
    }

    /**
     * 稳定 hash（SHA-256，取前 32 字符），用于缓存 key 后缀。
     */
    private static String stableHash(String json) {
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

    // package-private 以便单元测试直接覆盖"controlled 覆盖 + reminders 去重"逻辑。
    static Map<String, Object> enforceControlledFunctionResult(
            Map<String, Object> llmResult,
            MedicationFunctionService.FunctionResult functionResult
    ) {
        Map<String, Object> result = new LinkedHashMap<>(llmResult == null ? Map.of() : llmResult);
        result.put("overallRiskLevel", maxRiskLevel(functionResult.overallRiskLevel(), string(result.get("overallRiskLevel"))));
        result.put("contraindicationRisks", functionResult.contraindicationRisks());
        result.put("interactionRisks", functionResult.interactionRisks());
        result.put("reminders", mergeReminders(functionResult.reminders(), listMap(result.get("reminders"))));
        result.put("functionTrace", functionResult.functionTrace());
        return result;
    }

    private static String maxRiskLevel(String controlledRiskLevel, String llmRiskLevel) {
        return severity(llmRiskLevel) > severity(controlledRiskLevel) ? llmRiskLevel : controlledRiskLevel;
    }

    private static int severity(String riskLevel) {
        return switch (string(riskLevel).toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    // package-private 以便单元测试直接覆盖去重逻辑（无需反射）。
    static List<Map<String, Object>> mergeReminders(List<Map<String, Object>> controlled,
                                                    List<Map<String, Object>> generated) {
        // LLM 看到 payload 里包含 controlledFunctionResult.reminders 时会原样复制返回，
        // 简单 addAll 会让每种药品的提醒文本翻倍（用户视角：4 条相同的"阿莫西林胶囊：..."）。
        // 按 (drugName, reminder 文本) 元组去重，controlled 优先保留（规则侧是 source of truth）。
        LinkedHashMap<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        if (controlled != null) {
            for (Map<String, Object> item : controlled) {
                deduped.putIfAbsent(reminderKey(item), item);
            }
        }
        if (generated != null) {
            for (Map<String, Object> item : generated) {
                deduped.putIfAbsent(reminderKey(item), item);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private static String reminderKey(Map<String, Object> item) {
        if (item == null) {
            return "|";
        }
        String drugName = String.valueOf(item.getOrDefault("drugName", "")).trim().toLowerCase(Locale.ROOT);
        String reminder = String.valueOf(item.getOrDefault("reminder", item.getOrDefault("content", item.getOrDefault("message", "")))).trim().toLowerCase(Locale.ROOT);
        return drugName + "\u0001" + reminder;
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
