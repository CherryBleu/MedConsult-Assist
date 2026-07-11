package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.client.MedicalImageFetcher;
import com.medconsult.ai.client.MedicalImageFetcher.MedicalImagePayload;
import com.medconsult.ai.client.MedicalVisionClient;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.AiReviewRequest;
import com.medconsult.ai.dto.AiModels.ExternalModelDto;
import com.medconsult.ai.dto.AiModels.ImageDetectionRequest;
import com.medconsult.ai.dto.AiModels.ImageDetectionResponse;
import com.medconsult.ai.dto.AiModels.ImagingReviewResponse;
import com.medconsult.ai.dto.AiModels.ReportAnalysisRequest;
import com.medconsult.ai.dto.AiModels.ReportAnalysisResponse;
import com.medconsult.ai.mq.ImageDetectionTaskMessage;
import com.medconsult.ai.persistence.entity.AiImageDetectionEntity;
import com.medconsult.ai.persistence.entity.AiReportTextAnalysisEntity;
import com.medconsult.ai.persistence.mapper.AiImageDetectionMapper;
import com.medconsult.ai.persistence.mapper.AiReportTextAnalysisMapper;
import com.medconsult.ai.security.AiHeaders;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.mq.MqConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ImagingDetectionService {
    private static final Logger log = LoggerFactory.getLogger(ImagingDetectionService.class);

    private static final String PROMPT = """
            You are a medical report abnormality screening assistant.
            Generate JSON only:
            {"abnormalDetected":true,"findings":[{"abnormalType":"","location":"","riskLevel":"LOW","confidence":0.0,"suggestion":""}]}
            The result is only for preliminary screening reference.
            """;

    private final OpenAiCompatibleClient llmClient;
    private final AiReportTextAnalysisMapper reportTextAnalysisMapper;
    private final AiImageDetectionMapper imageDetectionMapper;
    private final AiProperties properties;
    private final AiCallLogService callLogService;
    private final RabbitTemplate rabbitTemplate;
    private final MedicalImageFetcher imageFetcher;
    private final MedicalVisionClient visionClient;

    public ImagingDetectionService(OpenAiCompatibleClient llmClient,
                                   AiReportTextAnalysisMapper reportTextAnalysisMapper,
                                   AiImageDetectionMapper imageDetectionMapper,
                                   AiProperties properties,
                                   AiCallLogService callLogService,
                                   RabbitTemplate rabbitTemplate,
                                   MedicalImageFetcher imageFetcher,
                                   MedicalVisionClient visionClient) {
        this.llmClient = llmClient;
        this.reportTextAnalysisMapper = reportTextAnalysisMapper;
        this.imageDetectionMapper = imageDetectionMapper;
        this.properties = properties;
        this.callLogService = callLogService;
        this.rabbitTemplate = rabbitTemplate;
        this.imageFetcher = imageFetcher;
        this.visionClient = visionClient;
    }

    public ReportAnalysisResponse analyzeReport(ReportAnalysisRequest request) {
        long started = System.currentTimeMillis();
        Map<String, Object> result = analyzeTextPayload(request.reportText(), request.reportType(), request.externalModel());
        String analysisNo = BusinessIds.next("RPT");
        List<Map<String, Object>> findings = listMap(result.get("findings"));
        boolean abnormal = Boolean.TRUE.equals(result.get("abnormalDetected")) || !findings.isEmpty();
        LocalDateTime now = LocalDateTime.now();

        AiReportTextAnalysisEntity entity = new AiReportTextAnalysisEntity();
        entity.setAnalysisNo(analysisNo);
        entity.setPatientId(BusinessIds.numericId(request.patientId()));
        entity.setRecordId(BusinessIds.numericId(request.recordId()));
        entity.setAttachmentId(BusinessIds.numericId(request.attachmentId()));
        entity.setReportType(request.reportType());
        entity.setReportText(request.reportText());
        entity.setStatus("COMPLETED");
        entity.setAbnormalDetected(abnormal ? 1 : 0);
        entity.setFindings(JsonUtils.toJson(findings));
        entity.setExternalProvider(provider(request.externalModel()));
        entity.setExternalModel(model(request.externalModel()));
        entity.setLatencyMs((int) (System.currentTimeMillis() - started));
        entity.setReviewStatus("UNREVIEWED");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        reportTextAnalysisMapper.insert(entity);

        callLogService.success("REPORT_ANALYSIS", request.patientId(), analysisNo, entity.getExternalModel(),
                request.reportText(), JsonUtils.toJson(result), firstRisk(findings), System.currentTimeMillis() - started);
        return new ReportAnalysisResponse(analysisNo, "COMPLETED", abnormal, findings, disclaimer());
    }

    public ImageDetectionResponse submitImageDetection(ImageDetectionRequest request) {
        String detectionNo = BusinessIds.next("IMG");
        LocalDateTime now = LocalDateTime.now();
        AiImageDetectionEntity entity = new AiImageDetectionEntity();
        entity.setDetectionNo(detectionNo);
        entity.setPatientId(BusinessIds.numericId(request.patientId()));
        entity.setRecordId(BusinessIds.numericId(request.recordId()));
        entity.setImageType(request.imageType());
        entity.setImageUrls(JsonUtils.toJson(request.imageUrls()));
        entity.setStorageType(request.storageType() == null || request.storageType().isBlank() ? "OSS" : request.storageType());
        entity.setStatus("PENDING");
        entity.setAbnormalDetected(0);
        entity.setFindings(JsonUtils.toJson(List.of()));
        entity.setExternalProvider(provider(request.externalModel()));
        entity.setExternalModel(model(request.externalModel()));
        entity.setReviewStatus("UNREVIEWED");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        imageDetectionMapper.insert(entity);

        callLogService.success("IMAGING_DETECTION", request.patientId(), detectionNo, entity.getExternalModel(),
                JsonUtils.toJson(request.imageUrls()), "PENDING", null, 0);
        try {
            // 影像检测任务发到 ai.imaging exchange（架构文档 §4.2），路由键 ai.image.detect
            rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_AI_IMAGING, MqConstants.RK_AI_IMAGE_DETECT,
                    new ImageDetectionTaskMessage(detectionNo, currentTraceId()));
        } catch (RuntimeException ex) {
            log.warn("submit image detection task failed, detectionNo={}", detectionNo, ex);
            callLogService.failed("IMAGING_DETECTION_QUEUE", request.patientId(), detectionNo, entity.getExternalModel(),
                    JsonUtils.toJson(request.imageUrls()), 0, ex);
        }
        return new ImageDetectionResponse(detectionNo, "PENDING", false, List.of(), disclaimer());
    }

    public void processImageDetection(String detectionId) {
        long started = System.currentTimeMillis();
        AiImageDetectionEntity entity = findImageByNo(detectionId);
        if ("COMPLETED".equals(entity.getStatus())) {
            return;
        }
        entity.setStatus("PROCESSING");
        entity.setUpdatedAt(LocalDateTime.now());
        imageDetectionMapper.updateById(entity);
        try {
            Map<String, Object> result = analyzeImagePayload(entity);
            List<Map<String, Object>> findings = listMap(result.get("findings"));
            boolean abnormal = Boolean.TRUE.equals(result.get("abnormalDetected")) || !findings.isEmpty();
            entity.setStatus("COMPLETED");
            entity.setAbnormalDetected(abnormal ? 1 : 0);
            entity.setFindings(JsonUtils.toJson(findings));
            entity.setLatencyMs((int) (System.currentTimeMillis() - started));
            entity.setUpdatedAt(LocalDateTime.now());
            imageDetectionMapper.updateById(entity);
            callLogService.success("IMAGING_DETECTION", BusinessIds.businessOrEmpty(String.valueOf(entity.getPatientId())), detectionId,
                    entity.getExternalModel(), entity.getImageUrls(), JsonUtils.toJson(result), firstRisk(findings),
                    System.currentTimeMillis() - started);
        } catch (RuntimeException ex) {
            entity.setStatus("FAILED");
            entity.setUpdatedAt(LocalDateTime.now());
            imageDetectionMapper.updateById(entity);
            callLogService.failed("IMAGING_DETECTION", BusinessIds.businessOrEmpty(String.valueOf(entity.getPatientId())), detectionId,
                    entity.getExternalModel(), entity.getImageUrls(), System.currentTimeMillis() - started, ex);
            throw ex;
        }
    }

    public ImageDetectionResponse getImageDetection(String detectionId) {
        AiImageDetectionEntity entity = findImageByNo(detectionId);
        return toResponse(entity);
    }

    /**
     * 按患者查询影像检测列表（对齐前端 /api/v1/ai/imaging-detection/list）。
     */
    public List<ImageDetectionResponse> listByPatient(String patientId) {
        LambdaQueryWrapper<AiImageDetectionEntity> wrapper = new LambdaQueryWrapper<AiImageDetectionEntity>()
                .orderByDesc(AiImageDetectionEntity::getCreatedAt);
        if (StringUtils.hasText(patientId)) {
            wrapper.eq(AiImageDetectionEntity::getPatientId, BusinessIds.numericId(patientId));
        }
        return imageDetectionMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    public ImagingReviewResponse reviewImageDetection(String detectionId, AiReviewRequest request) {
        AiImageDetectionEntity entity = findImageByNo(detectionId);
        entity.setReviewStatus("REVIEWED");
        entity.setReviewedBy(BusinessIds.numericId(request.reviewedBy()));
        entity.setReviewComment(request.reviewComment());
        entity.setReviewedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        imageDetectionMapper.updateById(entity);
        return new ImagingReviewResponse(detectionId, "REVIEWED");
    }

    public ImagingReviewResponse reviewReportAnalysis(String analysisId, AiReviewRequest request) {
        AiReportTextAnalysisEntity entity = findReportByNo(analysisId);
        entity.setReviewStatus("REVIEWED");
        entity.setReviewedBy(BusinessIds.numericId(request.reviewedBy()));
        entity.setReviewComment(request.reviewComment());
        entity.setReviewedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        reportTextAnalysisMapper.updateById(entity);
        return new ImagingReviewResponse(analysisId, "REVIEWED");
    }

    private ImageDetectionResponse toResponse(AiImageDetectionEntity entity) {
        return new ImageDetectionResponse(
                entity.getDetectionNo(),
                entity.getStatus(),
                entity.getAbnormalDetected() != null && entity.getAbnormalDetected() == 1,
                JsonUtils.toListMap(entity.getFindings()),
                disclaimer()
        );
    }

    private AiImageDetectionEntity findImageByNo(String detectionId) {
        AiImageDetectionEntity entity = imageDetectionMapper.selectOne(new LambdaQueryWrapper<AiImageDetectionEntity>()
                .eq(AiImageDetectionEntity::getDetectionNo, detectionId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "image detection result not found");
        }
        return entity;
    }

    private AiReportTextAnalysisEntity findReportByNo(String analysisId) {
        AiReportTextAnalysisEntity entity = reportTextAnalysisMapper.selectOne(new LambdaQueryWrapper<AiReportTextAnalysisEntity>()
                .eq(AiReportTextAnalysisEntity::getAnalysisNo, analysisId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "report analysis result not found");
        }
        return entity;
    }

    private Map<String, Object> analyzeTextPayload(String reportText, String reportType, ExternalModelDto externalModel) {
        Map<String, Object> payload = Map.of(
                "reportType", reportType == null ? "" : reportType,
                "reportText", reportText == null ? "" : reportText,
                "externalModel", externalModel == null ? Map.of() : externalModel
        );
        return llmClient.chatJson(PROMPT, JsonUtils.toJson(payload))
                .map(node -> JsonUtils.MAPPER.convertValue(node, Map.class))
                .orElseGet(() -> fallback(reportText));
    }

    private Map<String, Object> analyzeImagePayload(AiImageDetectionEntity entity) {
        List<MedicalImagePayload> images = imageFetcher.fetch(JsonUtils.toStringList(entity.getImageUrls()));
        return visionClient.detect(entity.getImageType(), images)
                .map(node -> JsonUtils.MAPPER.convertValue(node, Map.class))
                .orElseGet(() -> imageFallback(images));
    }

    private String provider(ExternalModelDto externalModel) {
        return externalModel == null || externalModel.provider() == null || externalModel.provider().isBlank()
                ? properties.imaging().provider()
                : externalModel.provider();
    }

    private String model(ExternalModelDto externalModel) {
        return externalModel == null || externalModel.model() == null || externalModel.model().isBlank()
                ? properties.imaging().model()
                : externalModel.model();
    }

    private static Map<String, Object> fallback(String reportText) {
        boolean abnormal = reportText != null
                && (reportText.toLowerCase().contains("abnormal")
                || reportText.contains("结节")
                || reportText.contains("异常")
                || reportText.contains("占位"));
        return Map.of(
                "abnormalDetected", abnormal,
                "findings", abnormal
                        ? List.of(Map.of("abnormalType", "REPORT_ABNORMAL", "location", "", "riskLevel", "LOW",
                        "confidence", 0.6, "suggestion", "Please ask a doctor to review the report and original image."))
                        : List.of()
        );
    }

    private static Map<String, Object> imageFallback(List<MedicalImagePayload> images) {
        return Map.of(
                "abnormalDetected", false,
                "findings", List.of(),
                "imageFiles", images.stream()
                        .map(image -> Map.of(
                                "sourceUrl", image.sourceUrl(),
                                "contentType", image.contentType(),
                                "byteSize", image.byteSize()
                        ))
                        .toList()
        );
    }

    private static List<Map<String, Object>> listMap(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
        }
        return List.of();
    }

    private static String firstRisk(List<Map<String, Object>> findings) {
        return findings.isEmpty() ? "LOW" : string(findings.getFirst().get("riskLevel"));
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String disclaimer() {
        return "AI result is only for preliminary screening reference and cannot replace a doctor's diagnosis.";
    }

    /** 当前 traceId：优先 MDC（web 请求阶段已填充），无则本地生成（MQ 消费场景） */
    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return "trace-" + java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
