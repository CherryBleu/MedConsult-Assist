package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextResponse;
import com.medconsult.ai.dto.AiModels.SummaryConfirmRequest;
import com.medconsult.ai.dto.AiModels.SummaryConfirmResponse;
import com.medconsult.ai.persistence.entity.AiMedicalSummaryEntity;
import com.medconsult.ai.persistence.mapper.AiMedicalSummaryMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.MedicalRecordFeignClient;
import com.medconsult.common.feign.dto.MedicalRecordFullDTO;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class SummaryService {
    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final String SUMMARY_PROMPT = """
            You are an electronic medical record summarization assistant.
            Generate JSON only with fields:
            chiefComplaint, diagnosis, treatmentPlan, medications, followUpAdvice.
            Do not add important facts that are not supported by the record.
            Use empty arrays or empty strings when evidence is insufficient.
            """;

    /** 病历摘要结果缓存：按 recordNo 缓存，30 分钟 TTL。同一病历重复生成命中缓存，跳过 LLM 调用。 */
    private static final String SUMMARY_CACHE_PREFIX = "medconsult:ai:summary:record:";
    private static final Duration SUMMARY_CACHE_TTL = Duration.ofMinutes(30);
    private static final String PROMPT_VERSION = "v1";
    private static final String MEDICAL_RECORD_SERVICE = "medical-record-service";

    private final OpenAiCompatibleClient llmClient;
    private final MedicalRecordFeignClient medicalRecordClient;
    private final AiMedicalSummaryMapper summaryMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;
    private final StringRedisTemplate redisTemplate;

    public SummaryService(OpenAiCompatibleClient llmClient, MedicalRecordFeignClient medicalRecordClient,
                          AiMedicalSummaryMapper summaryMapper, AiProperties properties, AiCallLogService callLogService,
                          StringRedisTemplate redisTemplate) {
        this.llmClient = llmClient;
        this.medicalRecordClient = medicalRecordClient;
        this.summaryMapper = summaryMapper;
        this.properties = properties;
        this.callLogService = callLogService;
        this.redisTemplate = redisTemplate;
    }

    public MedicalRecordSummaryResponse summarizeRecord(MedicalRecordSummaryRequest request, JwtPayload actor) {
        requireMedicalRecordService(actor);
        return summarizeResolvedRecord(request, actor, null, true);
    }

    /**
     * 按病历业务编号（recordNo）做摘要（对齐前端 /api/v1/ai/summary/by-record/{recordNo}）。
     * 内部先把 recordNo 转主键，再走通用摘要逻辑。
     *
     * <p><b>缓存</b>：按 recordNo 缓存摘要结果（30 分钟 TTL）。同一病历重复生成命中缓存，
     * 跳过 LLM 调用直接返回，避免医生反复点"生成"触发重复 LLM 调用（LLM 是计费且耗时操作）。
     * 病历内容更新后缓存自动失效：recordText 变化时 LLM 会生成新摘要覆盖写库，
     * 下次请求因缓存命中返回旧摘要——这是可接受的（病历更新频率低，30 分钟 TTL 足够）。
     */
    public MedicalRecordSummaryResponse summarizeByRecordNo(String recordNo) {
        long started = System.currentTimeMillis();
        Long recordId = resolveRecordId(recordNo);
        MedicalRecordFullDTO record = fetchRecord(recordId);
        JwtPayload actor = SecurityContext.getPayload();
        authorizeUserRecord(actor, record);
        String canonicalRecordNo = canonicalRecordNo(recordId, record);
        String cacheKey = cacheKey(canonicalRecordNo);
        String sourceFingerprint = sourceFingerprint(recordId, canonicalRecordNo, record);
        // 缓存命中检查
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                CachedSummary cachedSummary = JsonUtils.MAPPER.readValue(cached, CachedSummary.class);
                if (recordId.equals(cachedSummary.recordId())
                        && sourceFingerprint.equals(cachedSummary.sourceFingerprint())) {
                    log.info("[AI-CACHE] summary hit cache: recordNo={}", canonicalRecordNo);
                    logCacheHit(canonicalRecordNo, record.patientId(), cachedSummary, started);
                    return new MedicalRecordSummaryResponse(
                            cachedSummary.summaryNo(), canonicalRecordNo,
                            cachedSummary.summary(), cachedSummary.status());
                }
            }
        } catch (Exception e) {
            log.warn("[AI-CACHE] summary cache read failed, fallback to LLM: {}", e.getMessage());
        }

        // wrapper.recordId 必须传解析后的真实主键（纯数字字符串），不能用 recordNo：
        // summarizeAndSave 第 181 行用 BusinessIds.numericId(request.recordId()) 提取数字，
        // recordNo 是 base36（含字母 MR...），numericId 返回 null → setRecordId(null)
        // → INSERT ai_medical_summary 时 record_id NOT NULL 列报 500。
        MedicalRecordSummaryRequest wrapper = new MedicalRecordSummaryRequest(
                String.valueOf(recordId), null, Boolean.FALSE);
        SummarySaveResult saved = summarizeAndSave(wrapper, recordId, record, null);
        MedicalRecordSummaryResponse response = new MedicalRecordSummaryResponse(
                saved.entity().getSummaryNo(),
                canonicalRecordNo,
                saved.summary(),
                saved.entity().getStatus()
        );
        // 写入缓存（异步友好：写失败不影响主流程）
        try {
            String patientId = record.patientId() == null ? null : String.valueOf(record.patientId());
            redisTemplate.opsForValue().set(cacheKey,
                    JsonUtils.toJson(new CachedSummary(
                            response.summaryId(), response.summary(), response.status(), patientId,
                            recordId, sourceFingerprint)),
                    SUMMARY_CACHE_TTL);
        } catch (Exception e) {
            log.warn("[AI-CACHE] summary cache write failed: {}", e.getMessage());
        }
        return response;
    }

    /** 摘要缓存结构（序列化存 Redis） */
    private record CachedSummary(String summaryNo, Map<String, Object> summary, String status,
                                 String patientId, Long recordId, String sourceFingerprint) {}

    private void logCacheHit(String recordNo, Long patientId, CachedSummary cachedSummary, long started) {
        try {
            callLogService.success("MEDICAL_RECORD_SUMMARY",
                    patientId == null ? null : String.valueOf(patientId), cachedSummary.summaryNo(),
                    llmModel(), recordNo, JsonUtils.toJson(cachedSummary.summary()), null,
                    System.currentTimeMillis() - started, AiCallLogService.AiCallLogMetrics.cacheHitMetrics());
        } catch (RuntimeException ex) {
            log.warn("[AI-CACHE] summary cache-hit log failed: {}", ex.getMessage());
        }
    }

    public MedicalRecordSummaryResponse summarizeRecordStream(MedicalRecordSummaryRequest request,
                                                              JwtPayload actor,
                                                              Consumer<String> tokenConsumer) {
        return summarizeResolvedRecord(request, actor, tokenConsumer, false);
    }

    public MedicalRecordSummaryResponse summarizeInternalRecordStream(MedicalRecordSummaryRequest request,
                                                                      JwtPayload actor,
                                                                      Consumer<String> tokenConsumer) {
        requireMedicalRecordService(actor);
        return summarizeResolvedRecord(request, actor, tokenConsumer, true);
    }

    public MedicalRecordSummaryTextResponse summarizeTextRequest(MedicalRecordSummaryTextRequest request) {
        requireDoctor(SecurityContext.getPayload());
        return new MedicalRecordSummaryTextResponse(summarizeText(request.recordText()), "GENERATED");
    }

    public SummaryConfirmResponse confirm(String summaryId, SummaryConfirmRequest request) {
        JwtPayload actor = requireDoctor(SecurityContext.getPayload());
        AiMedicalSummaryEntity entity = summaryMapper.selectOne(new LambdaQueryWrapper<AiMedicalSummaryEntity>()
                .eq(AiMedicalSummaryEntity::getSummaryNo, summaryId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "summary not found");
        }
        MedicalRecordFullDTO record = fetchRecord(entity.getRecordId());
        if (!actor.doctorId().equals(record.doctorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "doctor does not own this medical record");
        }
        entity.setSummaryContent(JsonUtils.toJson(request.confirmedSummary()));
        entity.setConfirmedBy(actor.doctorId());
        entity.setConfirmedAt(LocalDateTime.now());
        entity.setStatus("CONFIRMED");
        summaryMapper.updateById(entity);
        invalidateCache(canonicalRecordNo(entity.getRecordId(), record));
        invalidateCache(String.valueOf(entity.getRecordId()));
        return new SummaryConfirmResponse(summaryId, String.valueOf(entity.getRecordId()), "CONFIRMED");
    }

    private MedicalRecordSummaryResponse summarizeResolvedRecord(MedicalRecordSummaryRequest request,
                                                                 JwtPayload actor,
                                                                 Consumer<String> tokenConsumer,
                                                                 boolean internal) {
        Long recordId = resolveRecordId(request.recordId());
        MedicalRecordFullDTO record = fetchRecord(recordId);
        if (!internal) {
            authorizeUserRecord(actor, record);
        }
        SummarySaveResult saved = summarizeAndSave(request, recordId, record, tokenConsumer);
        return new MedicalRecordSummaryResponse(
                saved.entity().getSummaryNo(), canonicalRecordNo(recordId, record),
                saved.summary(), saved.entity().getStatus());
    }

    private static void authorizeUserRecord(JwtPayload actor, MedicalRecordFullDTO record) {
        if (actor == null || !actor.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "user login is required");
        }
        if (hasActiveRole(actor, "PATIENT")) {
            if (actor.patientId() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "patient identity is incomplete");
            }
            if (actor.patientId().equals(record.patientId())) {
                return;
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "patient does not own this medical record");
        }
        if (hasActiveRole(actor, "DOCTOR")) {
            if (actor.doctorId() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "doctor identity is incomplete");
            }
            if (actor.doctorId().equals(record.doctorId())) {
                return;
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "doctor does not own this medical record");
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "current role cannot access medical record summaries");
    }

    private static JwtPayload requireDoctor(JwtPayload actor) {
        if (actor == null || !actor.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "doctor login is required");
        }
        if (!hasActiveRole(actor, "DOCTOR")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "doctor role is required");
        }
        if (actor.doctorId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "doctor identity is incomplete");
        }
        return actor;
    }

    private static void requireMedicalRecordService(JwtPayload actor) {
        if (actor == null || !actor.isService()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "service identity is required");
        }
        if (!MEDICAL_RECORD_SERVICE.equals(actor.serviceCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "service cannot generate medical record summaries");
        }
    }

    private static boolean hasActiveRole(JwtPayload actor, String role) {
        if (StringUtils.hasText(actor.primaryRole())) {
            return role.equals(actor.primaryRole());
        }
        return actor.hasRole(role);
    }

    private static String canonicalRecordNo(Long recordId, MedicalRecordFullDTO record) {
        return StringUtils.hasText(record.recordNo()) ? record.recordNo() : String.valueOf(recordId);
    }

    private static String cacheKey(String recordNo) {
        return SUMMARY_CACHE_PREFIX + recordNo;
    }

    private void invalidateCache(String recordNo) {
        try {
            redisTemplate.delete(cacheKey(recordNo));
        } catch (RuntimeException ex) {
            log.warn("[AI-CACHE] summary cache delete failed: recordNo={}, error={}",
                    recordNo, ex.getMessage());
        }
    }

    private String sourceFingerprint(Long recordId, String recordNo, MedicalRecordFullDTO record) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, String.valueOf(recordId));
            updateDigest(digest, recordNo);
            updateDigest(digest, String.valueOf(record.patientId()));
            updateDigest(digest, String.valueOf(record.doctorId()));
            updateDigest(digest, recordText(record));
            updateDigest(digest, llmModel());
            updateDigest(digest, SUMMARY_PROMPT);
            updateDigest(digest, PROMPT_VERSION);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private String llmModel() {
        return properties.llm() == null ? "" : properties.llm().model();
    }

    private MedicalRecordFullDTO fetchRecord(Long recordId) {
        try {
            Result<MedicalRecordFullDTO> result = medicalRecordClient.getFullRecord(recordId);
            if (result == null || result.data() == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "medical record not found");
            }
            return result.data();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "medical record not found");
        }
    }

    /**
     * 把病历业务编号（recordNo，形如 MR1K2J3M4N）解析为 BIGINT 主键。
     *
     * <p>record_no 是 {@code "MR" + base36}（{@link com.medconsult.common.feign.client.MedicalRecordFeignClient#resolveId}
     * 服务端 {@code generateRecordNo()} 生成），含字母 A-Z，不能用十进制正则反解。
     * 改为调 medical-record-service 的 /internal/medical-records/no/{recordNo}/id 端点拿真实主键，
     * 下游查不到时抛 NOT_FOUND（经 FeignErrorDecoder 回传）。
     *
     * <p>同时兼容入参已是纯数字主键的场景（内部直调）：纯数字直接 parseLong，省一次 RPC。
     */
    private Long resolveRecordId(String recordNoOrId) {
        if (recordNoOrId == null || recordNoOrId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "病历编号不能为空");
        }
        // 纯数字视为主键直传（兼容内部调用方传 id 的场景）
        Long parsed = BusinessIds.numericId(recordNoOrId);
        // 注意：numericId 用 (\d+)$ 末尾匹配，MR1K2J3M4N 末尾若含字母会返回 null；
        // 即便末尾恰好是数字（如 MR123），parse 出来也不等于真实雪花主键，故只接受"整串纯数字"。
        if (parsed != null && recordNoOrId.equals(String.valueOf(parsed))) {
            return parsed;
        }
        // recordNo（base36）→ 调下游反查主键
        try {
            Result<com.medconsult.common.feign.dto.EntityIdDTO> res =
                    medicalRecordClient.resolveId(recordNoOrId);
            if (res == null || res.data() == null || res.data().id() == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "病历不存在: " + recordNoOrId);
            }
            return res.data().id();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "病历不存在: " + recordNoOrId);
        }
    }

    private Map<String, Object> summarizeText(String text) {
        // LLM 不可用时不再静默兜底空 JSON（会导致前端显示空摘要表假装成功），
        // 改为抛业务异常，让前端走 catch 分支显示明确错误提示。
        JsonNode json = llmClient.chatJson(SUMMARY_PROMPT, text)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_EXTERNAL_FAILED,
                        "病历摘要生成失败：AI 服务暂不可用，请稍后重试或联系管理员检查 LLM 配置"));
        return JsonUtils.MAPPER.convertValue(json, Map.class);
    }

    private SummarySaveResult summarizeAndSave(MedicalRecordSummaryRequest request,
                                               Long recordId,
                                               MedicalRecordFullDTO record,
                                               Consumer<String> tokenConsumer) {
        long started = System.currentTimeMillis();
        String text = recordText(record);
        // LLM 不可用时不再静默兜底空 JSON（会导致前端显示空摘要表假装成功），
        // 改为抛业务异常，让前端走 catch 分支显示明确错误提示。
        // 用户要求"不允许返回空值"：LLM 是摘要的唯一数据源，不可用时必须报错而非返回空内容。
        JsonNode json = tokenConsumer == null
                ? llmClient.chatJson(SUMMARY_PROMPT, text)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_EXTERNAL_FAILED,
                            "病历摘要生成失败：AI 服务暂不可用，请稍后重试或联系管理员检查 LLM 配置"))
                : llmClient.chatJsonStream(SUMMARY_PROMPT, text, tokenConsumer)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_EXTERNAL_FAILED,
                            "病历摘要生成失败：AI 服务暂不可用，请稍后重试或联系管理员检查 LLM 配置"));
        Map<String, Object> summary = JsonUtils.MAPPER.convertValue(json, Map.class);

        String summaryNo = BusinessIds.next("SUM");
        AiMedicalSummaryEntity entity = new AiMedicalSummaryEntity();
        entity.setSummaryNo(summaryNo);
        entity.setRecordId(recordId);
        entity.setSummaryType(request.summaryType() == null ? "STRUCTURED" : request.summaryType());
        entity.setSummaryContent(JsonUtils.toJson(summary));
        entity.setStatus(Boolean.TRUE.equals(request.saveDraft()) ? "AI_DRAFT" : "GENERATED");
        entity.setModelName(llmModel());
        entity.setPromptVersion(PROMPT_VERSION);
        entity.setGeneratedAt(LocalDateTime.now());
        summaryMapper.insert(entity);

        callLogService.success("MEDICAL_RECORD_SUMMARY", record.patientId() == null ? null : String.valueOf(record.patientId()),
                summaryNo, llmModel(), request.recordId(), JsonUtils.toJson(summary), null,
                System.currentTimeMillis() - started);
        return new SummarySaveResult(entity, summary);
    }

    private static String recordText(MedicalRecordFullDTO record) {
        // 诊断合并初步诊断 + 最终诊断：草稿病历可能只有 initialDiagnosis（finalDiagnosis 在
        // updateDraft 时才写），只取 finalDiagnosis 会导致 LLM 看不到诊断 → 返回空 diagnosis。
        List<String> allDiagnoses = new java.util.ArrayList<>();
        if (record.initialDiagnosis() != null) allDiagnoses.addAll(record.initialDiagnosis());
        if (record.finalDiagnosis() != null) allDiagnoses.addAll(record.finalDiagnosis());
        return "chiefComplaint: " + safe(record.chiefComplaint())
                + "\npresentIllness: " + safe(record.presentIllness())
                + "\npastHistory: " + safe(record.pastHistory())
                + "\nphysicalExam: " + safe(record.physicalExam())
                + "\ndiagnosis: " + JsonUtils.toJson(allDiagnoses)
                + "\ndoctorAdvice: " + safe(record.doctorAdvice());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record SummarySaveResult(AiMedicalSummaryEntity entity, Map<String, Object> summary) {
    }
}
