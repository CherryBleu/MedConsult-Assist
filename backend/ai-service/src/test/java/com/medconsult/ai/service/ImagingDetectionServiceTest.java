package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.medconsult.ai.persistence.entity.AiImageDetectionEntity;
import com.medconsult.ai.persistence.entity.AiReportTextAnalysisEntity;
import com.medconsult.ai.persistence.mapper.AiImageDetectionMapper;
import com.medconsult.ai.persistence.mapper.AiReportTextAnalysisMapper;
import com.medconsult.ai.service.FileUploadService.DetectionFileResolution;
import com.medconsult.ai.util.JsonUtils;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.mq.LocalMessage;
import com.medconsult.common.mq.LocalMessageMapper;
import com.medconsult.common.mq.MqConstants;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImagingDetectionServiceTest {
    private OpenAiCompatibleClient llmClient;
    private AiReportTextAnalysisMapper reportMapper;
    private AiImageDetectionMapper imageMapper;
    private AiCallLogService callLogService;
    private LocalMessageMapper localMessageMapper;
    private MedicalImageFetcher imageFetcher;
    private MedicalVisionClient visionClient;
    private FileUploadService fileUploadService;
    private ImagingDetectionService service;

    @BeforeAll
    static void initializeMybatisLambdaMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "imaging-test"),
                AiImageDetectionEntity.class);
    }

    @BeforeEach
    void setUp() {
        llmClient = mock(OpenAiCompatibleClient.class);
        reportMapper = mock(AiReportTextAnalysisMapper.class);
        imageMapper = mock(AiImageDetectionMapper.class);
        callLogService = mock(AiCallLogService.class);
        localMessageMapper = mock(LocalMessageMapper.class);
        imageFetcher = mock(MedicalImageFetcher.class);
        visionClient = mock(MedicalVisionClient.class);
        fileUploadService = mock(FileUploadService.class);
        service = new ImagingDetectionService(
                llmClient,
                reportMapper,
                imageMapper,
                properties(),
                callLogService,
                localMessageMapper,
                JsonUtils.MAPPER,
                imageFetcher,
                visionClient,
                fileUploadService
        );
    }

    @AfterEach
    void cleanRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void analyzeReportShouldPersistModelFindingsAndExternalModelSelection() {
        when(llmClient.chatJson(anyString(), anyString())).thenReturn(Optional.of(JsonUtils.readTree("""
                {"abnormalDetected":false,"findings":[
                  {"abnormalType":"NODULE","riskLevel":"HIGH","confidence":0.91},
                  "ignored-non-object"
                ]}
                """)));
        ArgumentCaptor<AiReportTextAnalysisEntity> entityCaptor =
                ArgumentCaptor.forClass(AiReportTextAnalysisEntity.class);
        when(reportMapper.insert(entityCaptor.capture())).thenReturn(1);
        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);

        ReportAnalysisResponse response = service.analyzeReport(new ReportAnalysisRequest(
                "PAT1001",
                "REC2002",
                "ATT3003",
                "CT",
                "Small pulmonary nodule.",
                new ExternalModelDto("external-provider", "external-model")
        ));

        assertTrue(response.analysisId().startsWith("RPT"));
        assertEquals("COMPLETED", response.status());
        assertTrue(response.abnormalDetected());
        assertEquals(1, response.findings().size());
        assertTrue(response.disclaimer().contains("cannot replace"));

        AiReportTextAnalysisEntity entity = entityCaptor.getValue();
        assertEquals(response.analysisId(), entity.getAnalysisNo());
        assertEquals(1001L, entity.getPatientId());
        assertEquals(2002L, entity.getRecordId());
        assertEquals(3003L, entity.getAttachmentId());
        assertEquals("CT", entity.getReportType());
        assertEquals("Small pulmonary nodule.", entity.getReportText());
        assertEquals(1, entity.getAbnormalDetected());
        assertEquals("external-provider", entity.getExternalProvider());
        assertEquals("external-model", entity.getExternalModel());
        assertEquals("UNREVIEWED", entity.getReviewStatus());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertNotNull(entity.getLatencyMs());
        assertEquals("HIGH", JsonUtils.toListMap(entity.getFindings()).getFirst().get("riskLevel"));

        verify(llmClient).chatJson(anyString(), requestCaptor.capture());
        Map<String, Object> modelRequest = JsonUtils.toMap(requestCaptor.getValue());
        assertEquals("CT", modelRequest.get("reportType"));
        assertEquals("Small pulmonary nodule.", modelRequest.get("reportText"));
        verify(callLogService).success(
                eq("REPORT_ANALYSIS"), eq("PAT1001"), eq(response.analysisId()), eq("external-model"),
                eq("Small pulmonary nodule."), anyString(), eq("HIGH"), anyLong());
    }

    @Test
    void analyzeReportShouldUseKeywordFallbackWhenModelReturnsNoResult() {
        when(llmClient.chatJson(anyString(), anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<AiReportTextAnalysisEntity> entityCaptor =
                ArgumentCaptor.forClass(AiReportTextAnalysisEntity.class);
        when(reportMapper.insert(entityCaptor.capture())).thenReturn(1);

        ReportAnalysisResponse response = service.analyzeReport(new ReportAnalysisRequest(
                "PAT7", "REC8", null, "XRAY", "Abnormal opacity requires review", null));

        assertTrue(response.abnormalDetected());
        assertEquals("REPORT_ABNORMAL", response.findings().getFirst().get("abnormalType"));
        assertEquals("default-provider", entityCaptor.getValue().getExternalProvider());
        assertEquals("default-imaging-model", entityCaptor.getValue().getExternalModel());
        verify(callLogService).success(
                eq("REPORT_ANALYSIS"), eq("PAT7"), eq(response.analysisId()), eq("default-imaging-model"),
                eq("Abnormal opacity requires review"), anyString(), eq("LOW"), anyLong());
    }

    @Test
    void analyzeReportShouldTreatNullPayloadFieldsAsNormalFallbackInput() {
        when(llmClient.chatJson(anyString(), anyString())).thenReturn(Optional.empty());
        when(reportMapper.insert(any(AiReportTextAnalysisEntity.class))).thenReturn(1);
        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);

        ReportAnalysisResponse response = service.analyzeReport(
                new ReportAnalysisRequest(null, null, null, null, null, null));

        assertFalse(response.abnormalDetected());
        assertTrue(response.findings().isEmpty());
        verify(llmClient).chatJson(anyString(), requestCaptor.capture());
        Map<String, Object> payload = JsonUtils.toMap(requestCaptor.getValue());
        assertEquals("", payload.get("reportType"));
        assertEquals("", payload.get("reportText"));
        assertEquals(Map.of(), payload.get("externalModel"));
    }

    @Test
    void submitImageDetectionShouldPersistPendingTaskAndEnqueueCurrentTrace() throws Exception {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 101L));
        MDC.put("traceId", "trace-imaging-123");
        ArgumentCaptor<AiImageDetectionEntity> entityCaptor =
                ArgumentCaptor.forClass(AiImageDetectionEntity.class);
        when(imageMapper.insert(entityCaptor.capture())).thenReturn(1);
        ArgumentCaptor<LocalMessage> outboxCaptor = ArgumentCaptor.forClass(LocalMessage.class);

        List<String> fileIds = List.of("FILE-1", "FILE-2");
        List<String> locators = List.of(
                "minio://medical-images/study/one.dcm",
                "minio://medical-images/study/two.dcm");
        when(fileUploadService.resolveDetectionFiles(fileIds, 101L, 202L))
                .thenReturn(new DetectionFileResolution(locators, 101L, 202L));

        ImageDetectionResponse response = service.submitImageDetection(new ImageDetectionRequest(
                "PAT101", "REC202", "MRI", fileIds, null, " ", null));

        assertTrue(response.detectionId().startsWith("IMG"));
        assertEquals("PENDING", response.status());
        assertFalse(response.abnormalDetected());
        assertTrue(response.findings().isEmpty());

        AiImageDetectionEntity entity = entityCaptor.getValue();
        assertEquals(response.detectionId(), entity.getDetectionNo());
        assertEquals(101L, entity.getPatientId());
        assertEquals(202L, entity.getRecordId());
        assertEquals("MRI", entity.getImageType());
        assertEquals(locators, JsonUtils.toStringList(entity.getImageUrls()));
        assertEquals("MINIO", entity.getStorageType());
        assertEquals(100L, entity.getSubmittedByUserId());
        assertEquals(null, entity.getSubmittedByServiceCode());
        assertEquals("PENDING", entity.getStatus());
        assertEquals(0, entity.getAbnormalDetected());
        assertEquals("default-provider", entity.getExternalProvider());
        assertEquals("default-imaging-model", entity.getExternalModel());

        verify(localMessageMapper).insert(outboxCaptor.capture());
        LocalMessage outbox = outboxCaptor.getValue();
        assertEquals(MqConstants.EXCHANGE_AI_IMAGING, outbox.getExchange());
        assertEquals(MqConstants.RK_AI_IMAGE_DETECT, outbox.getRoutingKey());
        assertEquals(LocalMessage.STATUS_PENDING, outbox.getStatus());
        assertEquals(0, outbox.getRetryCount());
        assertEquals("imaging-detect:" + response.detectionId(), outbox.getMessageNo());
        assertTrue(outbox.getMessageNo().length() <= 64);
        JsonNode payload = JsonUtils.MAPPER.readTree(outbox.getPayloadJson());
        assertEquals(response.detectionId(), payload.get("detectionId").asText());
        assertEquals("trace-imaging-123", payload.get("traceId").asText());
        verify(callLogService).success(
                eq("IMAGING_DETECTION"), eq("PAT101"), eq(response.detectionId()),
                eq("default-imaging-model"), eq(JsonUtils.toJson(locators)), eq("PENDING"), isNull(), eq(0L));
        verify(imageFetcher).validateSources(locators);
    }

    @Test
    void submitImageDetectionShouldDeriveMissingTaskContextFromFiles() {
        bindPayload(userPayload(222L, "DOCTOR", List.of("DOCTOR"), null, 900L));
        List<String> fileIds = List.of("FILE-1");
        List<String> locators = List.of("minio://medical-images/study/one.dcm");
        when(fileUploadService.resolveDetectionFiles(fileIds, null, null))
                .thenReturn(new DetectionFileResolution(locators, 42L, 81L));
        ArgumentCaptor<AiImageDetectionEntity> entityCaptor =
                ArgumentCaptor.forClass(AiImageDetectionEntity.class);
        when(imageMapper.insert(entityCaptor.capture())).thenReturn(1);

        service.submitImageDetection(new ImageDetectionRequest(
                null, null, "CT", fileIds, null, null, null));

        AiImageDetectionEntity entity = entityCaptor.getValue();
        assertEquals(42L, entity.getPatientId());
        assertEquals(81L, entity.getRecordId());
        assertEquals(222L, entity.getSubmittedByUserId());
        assertEquals(null, entity.getSubmittedByServiceCode());
    }

    @Test
    void submitImageDetectionShouldRejectUserIdentityWithoutOwnershipId() {
        bindPayload(userPayload(null, "DOCTOR", List.of("DOCTOR"), null, 900L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitImageDetection(new ImageDetectionRequest(
                        null, null, "CT", null, null, null, null)));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        verifyNoInteractions(fileUploadService, imageMapper, localMessageMapper);
    }

    @Test
    void submitImageDetectionShouldRejectServiceIdentityWithoutOwnershipCode() {
        bindPayload(servicePayload(" "));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitImageDetection(new ImageDetectionRequest(
                        null, null, "CT", null, null, null, null)));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        verifyNoInteractions(fileUploadService, imageMapper, localMessageMapper);
    }

    @Test
    void submitImageDetectionShouldRejectFileContextMismatchBeforeInsertOrPublish() {
        bindPayload(userPayload(222L, "DOCTOR", List.of("DOCTOR"), null, 900L));
        List<String> fileIds = List.of("FILE-1");
        when(fileUploadService.resolveDetectionFiles(fileIds, 43L, 82L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN,
                        "file patientId does not match requested task patientId"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitImageDetection(new ImageDetectionRequest(
                        "PAT43", "REC82", "CT", fileIds, null, null, null)));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verifyNoInteractions(imageMapper, localMessageMapper);
    }

    @Test
    void submitImageDetectionShouldLeaveTaskPendingWhenOutboxIsEnqueued() {
        bindPayload(servicePayload());
        List<String> statusesAtInsert = new ArrayList<>();
        when(imageMapper.insert(any(AiImageDetectionEntity.class))).thenAnswer(invocation -> {
            statusesAtInsert.add(invocation.<AiImageDetectionEntity>getArgument(0).getStatus());
            return 1;
        });
        ArgumentCaptor<LocalMessage> outboxCaptor = ArgumentCaptor.forClass(LocalMessage.class);

        ImageDetectionResponse response = service.submitImageDetection(new ImageDetectionRequest(
                "PAT11", "REC22", "CT", null,
                List.of("https://files.example.test/study.dcm?X-Amz-Signature=secret"), "MINIO",
                new ExternalModelDto("hospital", "vision-v2")
        ));

        assertEquals(List.of("PENDING"), statusesAtInsert);
        assertEquals("PENDING", response.status());
        assertFalse(response.abnormalDetected());
        verify(imageMapper, never()).updateById(any());
        verify(localMessageMapper).insert(outboxCaptor.capture());
        assertEquals("imaging-detect:" + response.detectionId(), outboxCaptor.getValue().getMessageNo());
        verify(callLogService, never()).failed(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void submitImageDetectionShouldGenerateTraceWhenMdcIsBlank() throws Exception {
        bindPayload(servicePayload());
        when(imageMapper.insert(any(AiImageDetectionEntity.class))).thenReturn(1);
        ArgumentCaptor<LocalMessage> outboxCaptor = ArgumentCaptor.forClass(LocalMessage.class);

        service.submitImageDetection(new ImageDetectionRequest(
                null, null, "XRAY", null, List.of("https://files.example.test/xray.jpg"), null,
                new ExternalModelDto(" ", " ")
        ));

        verify(localMessageMapper).insert(outboxCaptor.capture());
        JsonNode payload = JsonUtils.MAPPER.readTree(outboxCaptor.getValue().getPayloadJson());
        assertTrue(payload.get("traceId").asText().matches("trace-[0-9a-f]{32}"));
    }

    @Test
    void submitImageDetectionShouldRejectUserSuppliedUrlAndCrossPatientId() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 42L));

        BusinessException directUrl = assertThrows(BusinessException.class,
                () -> service.submitImageDetection(new ImageDetectionRequest(
                        "PAT42", null, "CT", null,
                        List.of("https://files.example.test/signed.dcm?X-Amz-Signature=secret"), null, null)));
        BusinessException crossPatient = assertThrows(BusinessException.class,
                () -> service.submitImageDetection(new ImageDetectionRequest(
                        "PAT99", null, "CT", List.of("FILE-1"), null, null, null)));

        assertEquals(ErrorCode.PARAM_ERROR, directUrl.getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, crossPatient.getErrorCode());
        verifyNoInteractions(fileUploadService, localMessageMapper);
    }

    @Test
    void submitImageDetectionShouldRejectMissingSourcesAndTooManyLegacyImages() {
        bindPayload(servicePayload());
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.submitImageDetection(new ImageDetectionRequest(
                        null, null, "CT", null, null, null, null)));
        List<String> tooMany = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> "https://files.example.test/" + i + ".dcm")
                .toList();
        doThrow(new IllegalArgumentException("at most 8 images are allowed"))
                .when(imageFetcher).validateLegacyHttpSources(tooMany);

        IllegalArgumentException excessive = assertThrows(IllegalArgumentException.class,
                () -> service.submitImageDetection(new ImageDetectionRequest(
                        null, null, "CT", null, tooMany, null, null)));

        assertEquals(ErrorCode.PARAM_ERROR, missing.getErrorCode());
        assertEquals("at most 8 images are allowed", excessive.getMessage());
        verifyNoInteractions(fileUploadService, localMessageMapper);
    }

    @Test
    void processImageDetectionShouldPersistProcessingThenCompletedModelResult() {
        AiImageDetectionEntity entity = imageEntity("PENDING");
        List<String> operationalSources = legacySourcesWithCredentials();
        entity.setImageUrls(JsonUtils.toJson(operationalSources));
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        MedicalImagePayload first = image(operationalSources.get(0), "AQ==");
        MedicalImagePayload second = image(operationalSources.get(1), "Ag==");
        when(imageFetcher.fetch(operationalSources))
                .thenReturn(List.of(first, second));
        when(visionClient.detect("CT", List.of(first, second))).thenReturn(Optional.of(JsonUtils.readTree("""
                {"abnormalDetected":false,"findings":[
                  {"abnormalType":"NODULE","riskLevel":"HIGH","location":"left lung"}
                ]}
                """)));
        List<String> persistedStatuses = captureImageStatusesOnUpdate();

        service.processImageDetection("IMG-1");

        assertEquals(List.of("PROCESSING", "COMPLETED"), persistedStatuses);
        assertEquals("COMPLETED", entity.getStatus());
        assertEquals(1, entity.getAbnormalDetected());
        assertEquals("HIGH", JsonUtils.toListMap(entity.getFindings()).getFirst().get("riskLevel"));
        assertNotNull(entity.getLatencyMs());
        assertEquals(operationalSources, JsonUtils.toStringList(entity.getImageUrls()));
        verify(callLogService).success(
                eq("IMAGING_DETECTION"), eq("42"), eq("IMG-1"), eq("vision-model"),
                eq(redactedLegacySourcesJson()), anyString(), eq("HIGH"), anyLong());
    }

    @Test
    void processImageDetectionShouldFailInsteadOfPersistingNormalWhenVisionReturnsEmpty() {
        AiImageDetectionEntity entity = imageEntity("PENDING");
        List<String> operationalSources = legacySourcesWithCredentials();
        entity.setImageUrls(JsonUtils.toJson(operationalSources));
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        MedicalImagePayload image = image(operationalSources.getFirst(), "AQID");
        when(imageFetcher.fetch(operationalSources)).thenReturn(List.of(image));
        when(visionClient.detect("CT", List.of(image))).thenReturn(Optional.empty());
        List<String> persistedStatuses = captureImageStatusesOnUpdate();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.processImageDetection("IMG-1"));

        assertEquals("vision model returned no result", error.getMessage());
        assertEquals(List.of("PROCESSING", "FAILED"), persistedStatuses);
        assertEquals("FAILED", entity.getStatus());
        assertEquals(0, entity.getAbnormalDetected());
        assertTrue(JsonUtils.toListMap(entity.getFindings()).isEmpty());
        assertEquals(operationalSources, JsonUtils.toStringList(entity.getImageUrls()));
        verify(callLogService).failed(
                eq("IMAGING_DETECTION"), eq("42"), eq("IMG-1"), eq("vision-model"),
                eq(redactedLegacySourcesJson()), anyLong(), same(error));
    }

    @Test
    void processImageDetectionShouldKeepAbnormalFlagWhenModelFindingsShapeIsInvalid() {
        AiImageDetectionEntity entity = imageEntity("PENDING");
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        when(imageFetcher.fetch(any())).thenReturn(List.of());
        when(visionClient.detect("CT", List.of())).thenReturn(Optional.of(JsonUtils.readTree(
                "{\"abnormalDetected\":true,\"findings\":\"invalid\"}")));
        captureImageStatusesOnUpdate();

        service.processImageDetection("IMG-1");

        assertEquals(1, entity.getAbnormalDetected());
        assertTrue(JsonUtils.toListMap(entity.getFindings()).isEmpty());
    }

    @Test
    void processImageDetectionShouldMarkFailedLogAndRethrowFetchFailure() {
        AiImageDetectionEntity entity = imageEntity("PENDING");
        List<String> operationalSources = legacySourcesWithCredentials();
        entity.setImageUrls(JsonUtils.toJson(operationalSources));
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        IllegalStateException fetchFailure = new IllegalStateException("image fetch failed: HTTP 404");
        when(imageFetcher.fetch(operationalSources)).thenThrow(fetchFailure);
        List<String> persistedStatuses = captureImageStatusesOnUpdate();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.processImageDetection("IMG-1"));

        assertSame(fetchFailure, error);
        assertEquals(List.of("PROCESSING", "FAILED"), persistedStatuses);
        assertEquals("FAILED", entity.getStatus());
        assertEquals(operationalSources, JsonUtils.toStringList(entity.getImageUrls()));
        verifyNoInteractions(visionClient);
        verify(callLogService).failed(
                eq("IMAGING_DETECTION"), eq("42"), eq("IMG-1"), eq("vision-model"),
                eq(redactedLegacySourcesJson()), anyLong(), same(fetchFailure));
    }

    @Test
    void processImageDetectionShouldIgnoreAlreadyCompletedTask() {
        AiImageDetectionEntity entity = imageEntity("COMPLETED");
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        service.processImageDetection("IMG-1");

        verify(imageMapper, never()).updateById(any());
        verifyNoInteractions(imageFetcher, visionClient, callLogService);
    }

    @Test
    void getImageDetectionShouldMapPersistedDetailAndRejectMissingId() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 42L));
        AiImageDetectionEntity entity = imageEntity("COMPLETED");
        entity.setAbnormalDetected(1);
        entity.setFindings("[{\"riskLevel\":\"MEDIUM\",\"location\":\"right lung\"}]");
        entity.setReviewStatus("REVIEWED");
        entity.setReviewResult("CONFIRMED");
        entity.setReviewComment("Reviewed against original study");
        entity.setReviewedBy(900L);
        entity.setReviewedAt(LocalDateTime.of(2026, 7, 18, 12, 30));
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(entity)
                .thenReturn(null);

        ImageDetectionResponse response = service.getImageDetection("IMG-1");
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getImageDetection("IMG-MISSING"));

        assertEquals("IMG-1", response.detectionId());
        assertEquals("COMPLETED", response.status());
        assertTrue(response.abnormalDetected());
        assertEquals("right lung", response.findings().getFirst().get("location"));
        assertEquals("REVIEWED", response.reviewStatus());
        assertEquals("CONFIRMED", response.reviewResult());
        assertEquals("Reviewed against original study", response.reviewComment());
        assertEquals(900L, response.reviewedBy());
        assertEquals(LocalDateTime.of(2026, 7, 18, 12, 30), response.reviewedAt());
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void getImageDetectionShouldRejectPatientReadingAnotherPatientsDetection() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 43L));
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(imageEntity("COMPLETED"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getImageDetection("IMG-1"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void getImageDetectionShouldAllowAuthenticatedServiceForInternalWorkflow() {
        bindPayload(servicePayload());
        AiImageDetectionEntity entity = imageEntity("COMPLETED");
        entity.setSubmittedByUserId(null);
        entity.setSubmittedByServiceCode("medical-record-service");
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        ImageDetectionResponse response = service.getImageDetection("IMG-1");

        assertEquals("IMG-1", response.detectionId());
    }

    @Test
    void getImageDetectionShouldRejectCrossDoctorAndCrossServiceAccess() {
        AiImageDetectionEntity doctorTask = imageEntity("COMPLETED");
        doctorTask.setSubmittedByUserId(222L);
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(doctorTask);
        bindPayload(userPayload(111L, "DOCTOR", List.of("DOCTOR"), null, 901L));

        BusinessException crossDoctor = assertThrows(BusinessException.class,
                () -> service.getImageDetection("IMG-1"));

        AiImageDetectionEntity serviceTask = imageEntity("COMPLETED");
        serviceTask.setSubmittedByUserId(null);
        serviceTask.setSubmittedByServiceCode("medical-record-service");
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(serviceTask);
        bindPayload(servicePayload("outpatient-service"));

        BusinessException crossService = assertThrows(BusinessException.class,
                () -> service.getImageDetection("IMG-2"));

        assertEquals(ErrorCode.FORBIDDEN, crossDoctor.getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, crossService.getErrorCode());
    }

    @Test
    void listByPatientShouldRequireUserIdentity() {
        BusinessException anonymousError = assertThrows(BusinessException.class,
                () -> service.listByPatient("PAT42"));
        bindPayload(servicePayload());
        BusinessException serviceError = assertThrows(BusinessException.class,
                () -> service.listByPatient("PAT42"));

        assertEquals(ErrorCode.UNAUTHORIZED, anonymousError.getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED, serviceError.getErrorCode());
        verify(imageMapper, never()).selectList(any());
    }

    @Test
    void listByPatientShouldForcePatientToOwnPatientIdIgnoringRequestedId() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 42L));
        AiImageDetectionEntity ownResult = imageEntity("COMPLETED");
        ownResult.setAbnormalDetected(null);
        when(imageMapper.selectList(any())).thenReturn(List.of(ownResult));
        ArgumentCaptor<Wrapper<AiImageDetectionEntity>> queryCaptor = wrapperCaptor();

        List<ImageDetectionResponse> results = service.listByPatient("PAT999");

        assertEquals(1, results.size());
        assertFalse(results.getFirst().abnormalDetected());
        verify(imageMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<AiImageDetectionEntity> query =
                (LambdaQueryWrapper<AiImageDetectionEntity>) queryCaptor.getValue();
        String sqlSegment = query.getSqlSegment().toLowerCase();
        assertTrue(query.getParamNameValuePairs().containsValue(42L));
        assertFalse(query.getParamNameValuePairs().containsValue(999L));
        assertTrue(sqlSegment.contains("order by"));
        assertTrue(sqlSegment.contains("limit 200"));
    }

    @Test
    void listByPatientShouldUsePrimaryDoctorWhenSecondaryPatientRoleExists() {
        bindPayload(userPayload(222L, "DOCTOR", List.of("DOCTOR", "PATIENT"), null, 900L));
        when(imageMapper.selectList(any())).thenReturn(List.of());
        ArgumentCaptor<Wrapper<AiImageDetectionEntity>> queryCaptor = wrapperCaptor();

        service.listByPatient("PAT999");

        verify(imageMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<AiImageDetectionEntity> query =
                (LambdaQueryWrapper<AiImageDetectionEntity>) queryCaptor.getValue();
        query.getSqlSegment();
        assertTrue(query.getParamNameValuePairs().containsValue(999L));
        assertTrue(query.getParamNameValuePairs().containsValue(222L));
    }

    @Test
    void listByPatientShouldUsePrimaryAdminWhenSecondaryPatientRoleExists() {
        bindPayload(userPayload(333L, "HOSPITAL_ADMIN",
                List.of("HOSPITAL_ADMIN", "PATIENT"), 42L, null));
        when(imageMapper.selectList(any())).thenReturn(List.of());
        ArgumentCaptor<Wrapper<AiImageDetectionEntity>> queryCaptor = wrapperCaptor();

        service.listByPatient("PAT999");

        verify(imageMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<AiImageDetectionEntity> query =
                (LambdaQueryWrapper<AiImageDetectionEntity>) queryCaptor.getValue();
        query.getSqlSegment();
        assertTrue(query.getParamNameValuePairs().containsValue(999L));
        assertFalse(query.getParamNameValuePairs().containsValue(42L));
        assertFalse(query.getParamNameValuePairs().containsValue(333L));
    }

    @Test
    void listByPatientShouldAllowDoctorFilterOrUnfilteredHistory() {
        bindPayload(userPayload("DOCTOR", null, null));
        when(imageMapper.selectList(any())).thenReturn(List.of());
        ArgumentCaptor<Wrapper<AiImageDetectionEntity>> queryCaptor = wrapperCaptor();

        service.listByPatient("PAT77");
        service.listByPatient(" ");

        verify(imageMapper, org.mockito.Mockito.times(2)).selectList(queryCaptor.capture());
        LambdaQueryWrapper<AiImageDetectionEntity> filtered =
                (LambdaQueryWrapper<AiImageDetectionEntity>) queryCaptor.getAllValues().get(0);
        LambdaQueryWrapper<AiImageDetectionEntity> unfiltered =
                (LambdaQueryWrapper<AiImageDetectionEntity>) queryCaptor.getAllValues().get(1);
        filtered.getSqlSegment();
        unfiltered.getSqlSegment();
        assertTrue(filtered.getParamNameValuePairs().containsValue(77L));
        assertTrue(filtered.getParamNameValuePairs().containsValue(100L));
        assertTrue(unfiltered.getParamNameValuePairs().containsValue(100L));
        assertEquals(1, unfiltered.getParamNameValuePairs().size());
    }

    @Test
    void listByPatientShouldRejectUnsupportedUserRoleBeforeQuery() {
        bindPayload(userPayload("PHARMACY_ADMIN", List.of("PHARMACY_ADMIN"), null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listByPatient("PAT42"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(imageMapper, never()).selectList(any());
    }

    @Test
    void reviewImageDetectionShouldPersistReviewerAndComment() {
        bindPayload(userPayload(100L, "DOCTOR", List.of("DOCTOR"), null, 900L));
        AiImageDetectionEntity entity = imageEntity("COMPLETED");
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        ImagingReviewResponse response = service.reviewImageDetection(
                "IMG-1", new AiReviewRequest("DOC900", "CONFIRMED", "Reviewed against original study"));

        assertEquals("IMG-1", response.detectionId());
        assertEquals("REVIEWED", response.reviewStatus());
        assertEquals("CONFIRMED", response.reviewResult());
        assertEquals("Reviewed against original study", response.reviewComment());
        assertEquals(900L, response.reviewedBy());
        assertEquals(entity.getReviewedAt(), response.reviewedAt());
        assertEquals("REVIEWED", entity.getReviewStatus());
        assertEquals(900L, entity.getReviewedBy());
        assertEquals("CONFIRMED", entity.getReviewResult());
        assertEquals("Reviewed against original study", entity.getReviewComment());
        assertNotNull(entity.getReviewedAt());
        verify(imageMapper).updateById(same(entity));
    }

    @Test
    void reviewImageDetectionShouldIgnoreForgedReviewerAndRejectPatientReviewer() {
        AiImageDetectionEntity entity = imageEntity("COMPLETED");
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        bindPayload(userPayload(100L, "DOCTOR", List.of("DOCTOR"), null, 777L));

        service.reviewImageDetection(
                "IMG-1", new AiReviewRequest("DOC999999", "REJECTED", "Not diagnostic"));

        assertEquals(777L, entity.getReviewedBy());
        assertEquals("REJECTED", entity.getReviewResult());

        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 42L));
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> service.reviewImageDetection(
                        "IMG-1", new AiReviewRequest("DOC777", "CONFIRMED", null)));
        assertEquals(ErrorCode.FORBIDDEN, forbidden.getErrorCode());
    }

    @Test
    void reviewImageDetectionShouldRejectDoctorWhoDidNotSubmitTask() {
        bindPayload(userPayload(111L, "DOCTOR", List.of("DOCTOR"), null, 901L));
        AiImageDetectionEntity entity = imageEntity("COMPLETED");
        entity.setSubmittedByUserId(222L);
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reviewImageDetection(
                        "IMG-1", new AiReviewRequest(null, "REJECTED", "Cross-doctor review")));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(imageMapper, never()).updateById(entity);
    }

    @Test
    void reviewReportAnalysisShouldPersistReviewerAndRejectMissingReport() {
        bindPayload(userPayload(101L, "DOCTOR", List.of("DOCTOR"), null, 901L));
        AiReportTextAnalysisEntity entity = new AiReportTextAnalysisEntity();
        entity.setAnalysisNo("RPT-1");
        when(reportMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(entity)
                .thenReturn(null);

        ImagingReviewResponse response = service.reviewReportAnalysis(
                "RPT-1", new AiReviewRequest("DOC901", "REJECTED", "Needs repeat imaging"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reviewReportAnalysis(
                        "RPT-MISSING", new AiReviewRequest("DOC901", "REJECTED", null)));

        assertEquals("RPT-1", response.detectionId());
        assertEquals("REVIEWED", response.reviewStatus());
        assertEquals("REJECTED", response.reviewResult());
        assertEquals("Needs repeat imaging", response.reviewComment());
        assertEquals(901L, response.reviewedBy());
        assertEquals(entity.getReviewedAt(), response.reviewedAt());
        assertEquals("REVIEWED", entity.getReviewStatus());
        assertEquals(901L, entity.getReviewedBy());
        assertEquals("REJECTED", entity.getReviewResult());
        assertEquals("Needs repeat imaging", entity.getReviewComment());
        assertNotNull(entity.getReviewedAt());
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
        verify(reportMapper).updateById(same(entity));
    }

    private List<String> captureImageStatusesOnUpdate() {
        List<String> statuses = new ArrayList<>();
        when(imageMapper.updateById(any(AiImageDetectionEntity.class))).thenAnswer(invocation -> {
            statuses.add(invocation.<AiImageDetectionEntity>getArgument(0).getStatus());
            return 1;
        });
        return statuses;
    }

    private static AiImageDetectionEntity imageEntity(String status) {
        AiImageDetectionEntity entity = new AiImageDetectionEntity();
        entity.setDetectionNo("IMG-1");
        entity.setPatientId(42L);
        entity.setRecordId(81L);
        entity.setSubmittedByUserId(100L);
        entity.setImageType("CT");
        entity.setImageUrls(JsonUtils.toJson(List.of(
                "https://minio/one.dcm", "https://minio/two.dcm")));
        entity.setStorageType("MINIO");
        entity.setStatus(status);
        entity.setAbnormalDetected(0);
        entity.setFindings("[]");
        entity.setExternalProvider("vision-provider");
        entity.setExternalModel("vision-model");
        entity.setReviewStatus("UNREVIEWED");
        return entity;
    }

    private static MedicalImagePayload image(String sourceUrl, String base64) {
        return new MedicalImagePayload(
                sourceUrl, "application/dicom", 3, "data:application/dicom;base64," + base64);
    }

    private static List<String> legacySourcesWithCredentials() {
        return List.of(
                "https://legacy-user:legacy-password@files.example.test/study.dcm?X-Amz-Signature=secret",
                "minio://medical-images/study/two.dcm");
    }

    private static String redactedLegacySourcesJson() {
        return JsonUtils.toJson(List.of(
                "https://files.example.test/study.dcm",
                "minio://medical-images/study/two.dcm"));
    }

    private static AiProperties properties() {
        return new AiProperties(
                new AiProperties.LlmProperties("http://llm", "key", "llm-model", 2),
                null, null, null, null,
                new AiProperties.ImagingProperties("default-provider", "default-imaging-model"),
                new AiProperties.VisionProperties("http://vision", "key", "vision-model", 2, 1024),
                null, null, null, null
        );
    }

    private static void bindPayload(JwtPayload payload) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, payload);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static JwtPayload userPayload(String primaryRole, List<String> roles, Long patientId) {
        return userPayload(100L, primaryRole, roles, patientId, null);
    }

    private static JwtPayload userPayload(Long userId, String primaryRole, List<String> roles,
                                          Long patientId, Long doctorId) {
        return new JwtPayload(
                JwtPayload.SubjectType.USER,
                userId,
                null,
                "test-user",
                roles,
                primaryRole,
                patientId,
                doctorId,
                null,
                "U100",
                List.of("*"),
                "jti-user",
                9999999999L
        );
    }

    private static JwtPayload servicePayload() {
        return servicePayload("medical-record-service");
    }

    private static JwtPayload servicePayload(String serviceCode) {
        return new JwtPayload(
                JwtPayload.SubjectType.SERVICE,
                null,
                serviceCode,
                serviceCode,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of("ai:invoke"),
                "jti-" + serviceCode,
                9999999999L
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Wrapper<AiImageDetectionEntity>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }
}
