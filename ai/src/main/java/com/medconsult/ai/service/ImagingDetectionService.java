package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.common.BizException;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.ImagingDetectionRequest;
import com.medconsult.ai.dto.AiModels.ImagingDetectionResponse;
import com.medconsult.ai.dto.AiModels.ImagingReviewRequest;
import com.medconsult.ai.dto.AiModels.ImagingReviewResponse;
import com.medconsult.ai.persistence.entity.AiImagingDetectionEntity;
import com.medconsult.ai.persistence.mapper.AiImagingDetectionMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ImagingDetectionService {
    private static final String PROMPT = """
            你是影像报告异常初筛助手。请根据报告文本和影像类型生成 JSON：
            {"abnormalDetected":true,"findings":[{"abnormalType":"","location":"","riskLevel":"LOW","confidence":0.0,"suggestion":""}]}
            结果仅作初筛参考。
            """;

    private final OpenAiCompatibleClient llmClient;
    private final AiImagingDetectionMapper detectionMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;

    public ImagingDetectionService(OpenAiCompatibleClient llmClient, AiImagingDetectionMapper detectionMapper,
                                   AiProperties properties, AiCallLogService callLogService) {
        this.llmClient = llmClient;
        this.detectionMapper = detectionMapper;
        this.properties = properties;
        this.callLogService = callLogService;
    }

    public ImagingDetectionResponse detect(ImagingDetectionRequest request) {
        long started = System.currentTimeMillis();
        Map<String, Object> result = llmClient.chatJson(PROMPT, JsonUtils.toJson(request))
                .map(node -> JsonUtils.MAPPER.convertValue(node, Map.class))
                .orElseGet(() -> fallback(request.reportText()));
        String detectionNo = BusinessIds.next("IMG");
        List<Map<String, Object>> findings = listMap(result.get("findings"));
        boolean abnormal = Boolean.TRUE.equals(result.get("abnormalDetected")) || !findings.isEmpty();
        AiImagingDetectionEntity entity = new AiImagingDetectionEntity();
        entity.setDetectionNo(detectionNo);
        entity.setPatientId(BusinessIds.numericId(request.patientId()));
        entity.setRecordId(BusinessIds.numericId(request.recordId()));
        entity.setImageType(request.imageType());
        entity.setReportText(request.reportText());
        entity.setImageUrls(JsonUtils.toJson(request.imageUrls()));
        entity.setStatus("COMPLETED");
        entity.setAbnormalDetected(abnormal ? 1 : 0);
        entity.setFindings(JsonUtils.toJson(findings));
        entity.setExternalProvider(request.externalModel() == null ? properties.imaging().provider() : request.externalModel().provider());
        entity.setExternalModel(request.externalModel() == null ? properties.imaging().model() : request.externalModel().model());
        entity.setLatencyMs((int) (System.currentTimeMillis() - started));
        entity.setReviewStatus("UNREVIEWED");
        entity.setCreatedAt(LocalDateTime.now());
        detectionMapper.insert(entity);
        callLogService.success("IMAGING_DETECTION", request.patientId(), detectionNo, entity.getExternalModel(),
                request.reportText(), JsonUtils.toJson(result), findings.isEmpty() ? "LOW" : string(findings.getFirst().get("riskLevel")),
                System.currentTimeMillis() - started);
        return new ImagingDetectionResponse(detectionNo, "COMPLETED", abnormal, findings,
                "AI 结果仅作为初步筛查参考，不能替代医生诊断。");
    }

    public ImagingDetectionResponse get(String detectionId) {
        AiImagingDetectionEntity entity = findByNo(detectionId);
        return new ImagingDetectionResponse(
                entity.getDetectionNo(),
                entity.getStatus(),
                entity.getAbnormalDetected() != null && entity.getAbnormalDetected() == 1,
                JsonUtils.toListMap(entity.getFindings()),
                "AI 结果仅作为初步筛查参考，不能替代医生诊断。"
        );
    }

    public ImagingReviewResponse review(String detectionId, ImagingReviewRequest request) {
        AiImagingDetectionEntity entity = findByNo(detectionId);
        entity.setReviewStatus("REVIEWED");
        entity.setReviewedBy(BusinessIds.numericId(request.reviewedBy()));
        entity.setReviewComment(request.reviewComment());
        entity.setReviewedAt(LocalDateTime.now());
        detectionMapper.updateById(entity);
        return new ImagingReviewResponse(detectionId, "REVIEWED");
    }

    private AiImagingDetectionEntity findByNo(String detectionId) {
        AiImagingDetectionEntity entity = detectionMapper.selectOne(new LambdaQueryWrapper<AiImagingDetectionEntity>()
                .eq(AiImagingDetectionEntity::getDetectionNo, detectionId)
                .last("limit 1"));
        if (entity == null) {
            throw new BizException(404001, "影像检测结果不存在");
        }
        return entity;
    }

    private static Map<String, Object> fallback(String reportText) {
        boolean abnormal = reportText != null && (reportText.contains("结节") || reportText.contains("异常") || reportText.contains("占位"));
        return Map.of(
                "abnormalDetected", abnormal,
                "findings", abnormal
                        ? List.of(Map.of("abnormalType", "REPORT_ABNORMAL", "location", "", "riskLevel", "LOW", "confidence", 0.6, "suggestion", "建议医生复核报告并结合影像原片判断。"))
                        : List.of()
        );
    }

    private static List<Map<String, Object>> listMap(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
        }
        return List.of();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
