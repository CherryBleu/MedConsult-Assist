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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class SummaryService {
    private static final String SUMMARY_PROMPT = """
            You are an electronic medical record summarization assistant.
            Generate JSON only with fields:
            chiefComplaint, diagnosis, treatmentPlan, medications, followUpAdvice.
            Do not add important facts that are not supported by the record.
            Use empty arrays or empty strings when evidence is insufficient.
            """;

    private final OpenAiCompatibleClient llmClient;
    private final MedicalRecordFeignClient medicalRecordClient;
    private final AiMedicalSummaryMapper summaryMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;

    public SummaryService(OpenAiCompatibleClient llmClient, MedicalRecordFeignClient medicalRecordClient,
                          AiMedicalSummaryMapper summaryMapper, AiProperties properties, AiCallLogService callLogService) {
        this.llmClient = llmClient;
        this.medicalRecordClient = medicalRecordClient;
        this.summaryMapper = summaryMapper;
        this.properties = properties;
        this.callLogService = callLogService;
    }

    public MedicalRecordSummaryResponse summarizeRecord(MedicalRecordSummaryRequest request) {
        MedicalRecordFullDTO record = fetchRecord(resolveRecordId(request.recordId()));
        SummarySaveResult saved = summarizeAndSave(request, record, null);
        return new MedicalRecordSummaryResponse(
                saved.entity().getSummaryNo(),
                request.recordId(),
                saved.summary(),
                saved.entity().getStatus()
        );
    }

    /**
     * 按病历业务编号（recordNo）做摘要（对齐前端 /api/v1/ai/summary/by-record/{recordNo}）。
     * 内部先把 recordNo 转主键，再走通用摘要逻辑。
     */
    public MedicalRecordSummaryResponse summarizeByRecordNo(String recordNo) {
        Long recordId = resolveRecordId(recordNo);
        MedicalRecordFullDTO record = fetchRecord(recordId);
        MedicalRecordSummaryRequest wrapper = new MedicalRecordSummaryRequest(
                recordNo, null, Boolean.FALSE);
        SummarySaveResult saved = summarizeAndSave(wrapper, record, null);
        return new MedicalRecordSummaryResponse(
                saved.entity().getSummaryNo(),
                recordNo,
                saved.summary(),
                saved.entity().getStatus()
        );
    }

    public MedicalRecordSummaryResponse summarizeRecordStream(MedicalRecordSummaryRequest request, Consumer<String> tokenConsumer) {
        MedicalRecordFullDTO record = fetchRecord(resolveRecordId(request.recordId()));
        SummarySaveResult saved = summarizeAndSave(request, record, tokenConsumer);
        return new MedicalRecordSummaryResponse(
                saved.entity().getSummaryNo(),
                request.recordId(),
                saved.summary(),
                saved.entity().getStatus()
        );
    }

    public MedicalRecordSummaryTextResponse summarizeTextRequest(MedicalRecordSummaryTextRequest request) {
        return new MedicalRecordSummaryTextResponse(summarizeText(request.recordText()), "GENERATED");
    }

    public SummaryConfirmResponse confirm(String summaryId, SummaryConfirmRequest request) {
        AiMedicalSummaryEntity entity = summaryMapper.selectOne(new LambdaQueryWrapper<AiMedicalSummaryEntity>()
                .eq(AiMedicalSummaryEntity::getSummaryNo, summaryId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "summary not found");
        }
        entity.setSummaryContent(JsonUtils.toJson(request.confirmedSummary()));
        entity.setConfirmedBy(BusinessIds.numericId(request.confirmedBy()));
        entity.setConfirmedAt(LocalDateTime.now());
        entity.setStatus("CONFIRMED");
        summaryMapper.updateById(entity);
        return new SummaryConfirmResponse(summaryId, String.valueOf(entity.getRecordId()), "CONFIRMED");
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
        JsonNode json = llmClient.chatJson(SUMMARY_PROMPT, text).orElseGet(SummaryService::emptySummaryJson);
        return JsonUtils.MAPPER.convertValue(json, Map.class);
    }

    private SummarySaveResult summarizeAndSave(MedicalRecordSummaryRequest request,
                                               MedicalRecordFullDTO record,
                                               Consumer<String> tokenConsumer) {
        long started = System.currentTimeMillis();
        String text = recordText(record);
        JsonNode json = tokenConsumer == null
                ? llmClient.chatJson(SUMMARY_PROMPT, text).orElseGet(SummaryService::emptySummaryJson)
                : llmClient.chatJsonStream(SUMMARY_PROMPT, text, tokenConsumer).orElseGet(SummaryService::emptySummaryJson);
        Map<String, Object> summary = JsonUtils.MAPPER.convertValue(json, Map.class);

        String summaryNo = BusinessIds.next("SUM");
        AiMedicalSummaryEntity entity = new AiMedicalSummaryEntity();
        entity.setSummaryNo(summaryNo);
        entity.setRecordId(BusinessIds.numericId(request.recordId()));
        entity.setSummaryType(request.summaryType() == null ? "STRUCTURED" : request.summaryType());
        entity.setSummaryContent(JsonUtils.toJson(summary));
        entity.setStatus(Boolean.TRUE.equals(request.saveDraft()) ? "AI_DRAFT" : "GENERATED");
        entity.setModelName(properties.llm().model());
        entity.setPromptVersion("v1");
        entity.setGeneratedAt(LocalDateTime.now());
        summaryMapper.insert(entity);

        callLogService.success("MEDICAL_RECORD_SUMMARY", record.patientId() == null ? null : String.valueOf(record.patientId()),
                summaryNo, properties.llm().model(), request.recordId(), JsonUtils.toJson(summary), null,
                System.currentTimeMillis() - started);
        return new SummarySaveResult(entity, summary);
    }

    private static JsonNode emptySummaryJson() {
        return JsonUtils.readTree("""
                {"chiefComplaint":"","diagnosis":[],"treatmentPlan":"","medications":[],"followUpAdvice":""}
                """);
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
