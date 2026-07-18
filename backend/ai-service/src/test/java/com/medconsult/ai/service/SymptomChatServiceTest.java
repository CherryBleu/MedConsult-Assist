package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.RagOptions;
import com.medconsult.ai.dto.AiModels.SymptomChatRequest;
import com.medconsult.ai.dto.AiModels.SymptomChatResponse;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.knowledge.RiskRuleEngine;
import com.medconsult.ai.persistence.entity.AiChatMessageEntity;
import com.medconsult.ai.persistence.entity.AiChatSessionEntity;
import com.medconsult.ai.persistence.mapper.AiChatMessageMapper;
import com.medconsult.ai.persistence.mapper.AiChatSessionMapper;
import com.medconsult.ai.util.JsonUtils;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DoctorFeignClient;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SymptomChatServiceTest {

    private DiseaseSearchService diseaseSearchService;
    private AiChatSessionMapper chatSessionMapper;
    private AiChatMessageMapper chatMessageMapper;
    private AiCallLogService callLogService;
    private DoctorFeignClient doctorFeignClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RagReadinessService ragReadinessService;
    private RiskRuleEngine riskRuleEngine;
    private SymptomChatService service;

    @BeforeAll
    static void initializeMybatisLambdaMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "symptom-chat-test");
        TableInfoHelper.initTableInfo(assistant, AiChatSessionEntity.class);
        TableInfoHelper.initTableInfo(assistant, AiChatMessageEntity.class);
    }

    @BeforeEach
    void setUp() {
        diseaseSearchService = mock(DiseaseSearchService.class);
        chatSessionMapper = mock(AiChatSessionMapper.class);
        chatMessageMapper = mock(AiChatMessageMapper.class);
        callLogService = mock(AiCallLogService.class);
        doctorFeignClient = mock(DoctorFeignClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        ragReadinessService = mock(RagReadinessService.class);
        riskRuleEngine = mock(RiskRuleEngine.class);

        DiseaseIntent intent = new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of()));
        when(diseaseSearchService.extractIntent(anyString())).thenReturn(intent);
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt())).thenReturn(List.of());
        when(ragReadinessService.current()).thenReturn(readyRag());
        when(riskRuleEngine.assess(anyString(), any())).thenReturn(new RiskAssessment("LOW", false, List.of()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(doctorFeignClient.departmentNosWithDoctors()).thenReturn(Result.ok(List.of(
                "DEP_CARDIOLOGY", "DEP_RESPIRATORY", "DEP_PEDIATRICS", "DEP_EMERGENCY", "DEP_GENERAL"
        )));
        when(chatSessionMapper.insert(any(AiChatSessionEntity.class))).thenAnswer(invocation -> {
            AiChatSessionEntity session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(700L);
            }
            return 1;
        });
        when(chatMessageMapper.insert(any(AiChatMessageEntity.class))).thenReturn(1);

        service = new SymptomChatService(
                diseaseSearchService,
                aiProperties(),
                chatSessionMapper,
                chatMessageMapper,
                callLogService,
                doctorFeignClient,
                redisTemplate,
                ragReadinessService,
                riskRuleEngine
        );
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createSessionShouldPersistJwtOwnerAndReturnBusinessSession() {
        bindPatientIdentity(100L);

        SymptomChatService.SessionCreated created = service.createSession();

        ArgumentCaptor<AiChatSessionEntity> captor = ArgumentCaptor.forClass(AiChatSessionEntity.class);
        verify(chatSessionMapper).insert(captor.capture());
        AiChatSessionEntity persisted = captor.getValue();
        assertEquals(created.sessionId(), persisted.getSessionNo());
        assertTrue(created.sessionId().startsWith("CHAT"));
        assertEquals(100L, persisted.getPatientId());
        assertEquals("智能问诊会话", created.title());
        assertEquals("ACTIVE", created.status());
        assertEquals("[]", persisted.getContextSymptoms());
        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getUpdatedAt());
    }

    @Test
    void createSessionShouldRejectAccountWithoutPatientProfile() {
        bindPatientIdentity(null);

        BusinessException error = assertThrows(BusinessException.class, service::createSession);

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertTrue(error.getMessage().contains("未关联患者档案"));
        verifyNoInteractions(chatSessionMapper);
    }

    @Test
    void historyShouldReturnEmptyWhenSessionDoesNotExist() {
        bindPatientIdentity(100L);

        assertEquals(List.of(), service.getHistory("CHAT-missing"));

        verifyNoInteractions(chatMessageMapper);
    }

    @Test
    void historyShouldMapOwnedMessagesInMapperOrder() {
        bindPatientIdentity(100L);
        AiChatSessionEntity session = session(31L, 100L, "[]");
        when(chatSessionMapper.selectOne(any())).thenReturn(session);
        AiChatMessageEntity first = message("头痛两天", "建议观察");
        AiChatMessageEntity second = message("今天发热", "建议门诊评估");
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(first, second));
        ArgumentCaptor<Wrapper<AiChatSessionEntity>> sessionQuery = wrapperCaptor();
        ArgumentCaptor<Wrapper<AiChatMessageEntity>> messageQuery = wrapperCaptor();

        List<SymptomChatService.ChatHistoryItem> history = service.getHistory("CHAT-owned");

        assertEquals(List.of(
                new SymptomChatService.ChatHistoryItem("user", "头痛两天", "ai", "建议观察"),
                new SymptomChatService.ChatHistoryItem("user", "今天发热", "ai", "建议门诊评估")
        ), history);

        verify(chatSessionMapper).selectOne(sessionQuery.capture());
        LambdaQueryWrapper<AiChatSessionEntity> sessionWrapper =
                (LambdaQueryWrapper<AiChatSessionEntity>) sessionQuery.getValue();
        String sessionSql = sessionWrapper.getSqlSegment().toLowerCase();
        assertTrue(sessionSql.matches("(?s).*\\bsession_no\\s*=\\s*#\\{.+}.*"));
        assertEquals(1, sessionWrapper.getParamNameValuePairs().size());
        assertTrue(sessionWrapper.getParamNameValuePairs().containsValue("CHAT-owned"));

        verify(chatMessageMapper).selectList(messageQuery.capture());
        LambdaQueryWrapper<AiChatMessageEntity> messageWrapper =
                (LambdaQueryWrapper<AiChatMessageEntity>) messageQuery.getValue();
        String messageSql = messageWrapper.getSqlSegment().toLowerCase();
        assertTrue(messageSql.matches("(?s).*\\bsession_id\\s*=\\s*#\\{.+}.*"));
        assertEquals(1, messageWrapper.getParamNameValuePairs().size());
        assertTrue(messageWrapper.getParamNameValuePairs().containsValue(31L));
        assertTrue(messageSql.matches("(?s).*\\border\\s+by\\s+created_at\\s+asc\\b.*"));
    }

    @Test
    void historyShouldRejectForeignSession() {
        bindPatientIdentity(100L);
        when(chatSessionMapper.selectOne(any())).thenReturn(session(31L, 200L, "[]"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getHistory("CHAT-foreign"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertTrue(error.getMessage().contains("他人问诊历史"));
        verifyNoInteractions(chatMessageMapper);
    }

    @Test
    void historyShouldRejectSessionWithoutOwner() {
        bindPatientIdentity(100L);
        when(chatSessionMapper.selectOne(any())).thenReturn(session(31L, null, "[]"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getHistory("CHAT-owner-missing"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verifyNoInteractions(chatMessageMapper);
    }

    @Test
    void chatShouldRejectPatientIdThatDoesNotMatchJwtOwner() {
        bindPatientIdentity(100L);
        SymptomChatRequest request = request("CHAT-forbidden", "PATIENT-200", "头痛", null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.chat(request));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertTrue(error.getMessage().contains("禁止代他人"));
        verifyNoInteractions(diseaseSearchService, chatSessionMapper, chatMessageMapper, callLogService);
    }

    @Test
    void chatShouldReturnTraceableEmergencyAnswerAndPersistCompleteMessage() throws Exception {
        bindPatientIdentity(100L);
        DiseaseKnowledge cardiac = knowledge(
                "vec-1", "src-1", "心肌梗死", "冠状动脉急性堵塞",
                Map.of("cure_department", List.of("心内科", "急诊科", ""), "cause", "血栓"),
                "symptom, desc；symptom", "胸痛向左肩放射" + "证据".repeat(130), 0.96
        );
        DiseaseKnowledge respiratory = knowledge(
                "vec-2", "src-2", "", "",
                Map.of("cure_department", "呼吸内科", "prevent", "戒烟"),
                null, "疾病名称：肺炎\n疾病描述：感染导致发热和咳嗽", 0.82
        );
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), eq(7)))
                .thenReturn(List.of(cardiac, respiratory));
        PatientContext context = new PatientContext(65, "MALE", List.of(), List.of("高血压"), List.of());
        RiskAssessment highRisk = new RiskAssessment("HIGH", true, List.of("胸痛"));
        when(riskRuleEngine.assess("胸痛并呼吸困难", context)).thenReturn(highRisk);
        when(valueOperations.get(anyString())).thenReturn(
                "[\"DEP_CARDIOLOGY\",\"DEP_RESPIRATORY\",\"DEP_EMERGENCY\"]");

        SymptomChatResponse response = service.chat(request(
                "CHAT-high", "PATIENT-100", "胸痛并呼吸困难", context,
                new RagOptions("disease", 7, true)
        ));

        assertEquals("CHAT-high", response.sessionId());
        assertEquals("VECTOR_SEARCH_AND_RULE", response.answerSource());
        assertEquals(List.of("心肌梗死", "肺炎"), response.possibleCauses());
        assertEquals(List.of("急诊科", "心内科", "呼吸内科"), response.suggestedDepartments());
        assertEquals("HIGH", response.riskLevel());
        assertTrue(response.emergencyAdvice());
        assertTrue(response.answer().contains("立即到急诊"));
        assertTrue(response.answer().contains("心肌梗死"));
        assertEquals(2, response.vectorMatches().size());
        assertTrue(response.vectorMatches().getFirst().chunkText().length() <= 200);
        assertEquals(List.of("symptom", "desc", "cause", "cure_department"),
                response.citations().getFirst().matchedFields());
        assertTrue(response.citations().getFirst().snippet().length() <= 240);
        assertEquals(List.of("text", "prevent", "cure_department"),
                response.citations().get(1).matchedFields());

        ArgumentCaptor<AiChatSessionEntity> sessionCaptor = ArgumentCaptor.forClass(AiChatSessionEntity.class);
        verify(chatSessionMapper).insert(sessionCaptor.capture());
        assertEquals(100L, sessionCaptor.getValue().getPatientId());
        assertEquals(List.of("胸痛并呼吸困难"), readSymptoms(sessionCaptor.getValue().getContextSymptoms()));

        ArgumentCaptor<AiChatMessageEntity> messageCaptor = ArgumentCaptor.forClass(AiChatMessageEntity.class);
        verify(chatMessageMapper).insert(messageCaptor.capture());
        AiChatMessageEntity persisted = messageCaptor.getValue();
        assertEquals(700L, persisted.getSessionId());
        assertEquals(100L, persisted.getPatientId());
        assertEquals(response.answer(), persisted.getAiAnswer());
        assertEquals("HIGH", persisted.getRiskLevel());
        assertEquals(1, persisted.getEmergencyAdvice());
        assertEquals("test-model", persisted.getModelName());
        assertEquals("test-embedding", persisted.getQueryEmbeddingModel());
        assertEquals(2, JsonUtils.MAPPER.readTree(persisted.getCitations()).size());
        verify(doctorFeignClient, never()).departmentNosWithDoctors();
        verify(callLogService).success(eq("SYMPTOM_CHAT"), eq("100"), eq("CHAT-high"), eq("test-model"),
                eq("胸痛并呼吸困难"), eq(response.answer()), eq("HIGH"), anyLong());
    }

    @Test
    void chatShouldAppendSymptomsAndUseMediumRiskAdviceForExistingSession() throws Exception {
        bindPatientIdentity(100L);
        AiChatSessionEntity existing = session(42L, 100L, "[\"间歇性腹痛\"]");
        when(chatSessionMapper.selectOne(any())).thenReturn(existing);
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), eq(5)))
                .thenReturn(List.of(knowledge(
                        "vec-g", "src-g", "胃炎", "", Map.of(), "desc", "", 0.7
                )));
        when(riskRuleEngine.assess(anyString(), any())).thenReturn(
                new RiskAssessment("MEDIUM", false, List.of("症状持续")));

        SymptomChatResponse response = service.chat(request(
                "CHAT-existing", "", "持续恶心", null, null
        ));

        assertFalse(response.emergencyAdvice());
        assertEquals("MEDIUM", response.riskLevel());
        assertTrue(response.answer().contains("尽快到门诊"));
        assertTrue(response.answer().contains("相关专科"));
        assertEquals(List.of("间歇性腹痛", "持续恶心"), readSymptoms(existing.getContextSymptoms()));
        verify(chatSessionMapper).updateById(existing);
        verify(chatSessionMapper, never()).insert(any(AiChatSessionEntity.class));
    }

    @Test
    void chatShouldRecoverFromCorruptStoredContextAndUseLowRiskAdvice() throws Exception {
        bindPatientIdentity(100L);
        AiChatSessionEntity existing = session(42L, 100L, "not-json");
        when(chatSessionMapper.selectOne(any())).thenReturn(existing);
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt()))
                .thenReturn(List.of(knowledge(
                        "vec-p", "src-p", "儿童感冒", "上呼吸道感染",
                        Map.of("cure_department", "儿科"), "symptom", "发热咳嗽", 0.75
                )));

        SymptomChatResponse response = service.chat(request(
                "CHAT-corrupt", null, "低热咳嗽", null, null
        ));

        assertTrue(response.answer().contains("可短期观察"));
        assertEquals(List.of("儿科"), response.suggestedDepartments());
        assertEquals(List.of("低热咳嗽"), readSymptoms(existing.getContextSymptoms()));
        assertEquals(0, capturedMessage().getEmergencyAdvice());
    }

    @Test
    void chatShouldKeepOnlyTwentyMostRecentContextSymptoms() throws Exception {
        bindPatientIdentity(100L);
        List<String> previous = IntStream.range(0, 20).mapToObj(i -> "症状" + i).toList();
        AiChatSessionEntity existing = session(42L, 100L, JsonUtils.toJson(previous));
        when(chatSessionMapper.selectOne(any())).thenReturn(existing);
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt()))
                .thenReturn(List.of(knowledge("v", "s", "普通感冒", "", Map.of(), "", "", 0.6)));

        service.chat(request("CHAT-window", null, "症状20", null, null));

        List<String> stored = readSymptoms(existing.getContextSymptoms());
        assertEquals(20, stored.size());
        assertEquals("症状1", stored.getFirst());
        assertEquals("症状20", stored.getLast());
    }

    @Test
    void chatShouldHandleBlankContextMissingKnowledgeLabelsAndNoAvailableDepartments() throws Exception {
        bindPatientIdentity(100L);
        AiChatSessionEntity existing = session(42L, 100L, "  ");
        when(chatSessionMapper.selectOne(any())).thenReturn(existing);
        when(doctorFeignClient.departmentNosWithDoctors()).thenReturn(Result.ok(List.of()));
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt()))
                .thenReturn(List.of(knowledge(
                        "vec-unlabelled", "src-unlabelled", "", "",
                        Map.of("cure_department", "消化内科"), "symptom, ,desc", null, 0.55
                )));

        SymptomChatResponse response = service.chat(request(
                "CHAT-blank-context", null, "上腹不适", null, null
        ));

        assertEquals(List.of("上腹不适"), readSymptoms(existing.getContextSymptoms()));
        assertTrue(response.possibleCauses().isEmpty());
        assertTrue(response.suggestedDepartments().isEmpty());
        assertTrue(response.answer().contains("条目名称或描述缺失"));
    }

    @Test
    void chatShouldExplainRagReadinessFailureWhenNoKnowledgeMatches() {
        bindPatientIdentity(100L);
        RagReadinessService.RagReadiness degraded = new RagReadinessService.RagReadiness(
                false,
                LocalDateTime.now(),
                List.of(
                        new RagReadinessService.RagCheck("mongo", "DOWN", "ai.disease", 0, 100, "COUNT_BELOW_EXPECTED"),
                        new RagReadinessService.RagCheck("embedding", "UP", "test-embedding", 1536, 1536, "OK")
                )
        );
        when(ragReadinessService.current()).thenReturn(degraded);

        SymptomChatResponse response = service.chat(request(
                "CHAT-rag-down", null, "罕见症状", null, null
        ));

        assertEquals("VECTOR_SEARCH_AND_RULE_DEGRADED", response.answerSource());
        assertTrue(response.answer().contains("知识库自检未完全通过"));
        assertTrue(response.answer().contains("mongo:COUNT_BELOW_EXPECTED"));
        assertTrue(response.answer().contains("暂未检索到"));
        assertTrue(response.citations().isEmpty());
        assertTrue(response.vectorMatches().isEmpty());
    }

    @Test
    void chatShouldUseUnknownRagIssueWhenChecksAreUnavailable() {
        bindPatientIdentity(100L);
        when(ragReadinessService.current()).thenReturn(
                new RagReadinessService.RagReadiness(false, LocalDateTime.now(), null));

        SymptomChatResponse response = service.chat(request(
                "CHAT-rag-unknown", null, "没有命中", null, null
        ));

        assertTrue(response.answer().contains("（UNKNOWN）"));
    }

    @Test
    void chatShouldKeepKnowledgeDepartmentsWhenDepartmentLookupFails() {
        bindPatientIdentity(100L);
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt()))
                .thenReturn(List.of(knowledge(
                        "vec", "src", "心绞痛", "劳力性胸痛",
                        Map.of("cure_department", "心内科"), "desc", "劳力后胸痛", 0.8
                )));

        SymptomChatResponse response = service.chat(request(
                "CHAT-dept-down", null, "劳力后胸痛", null, null
        ));

        assertEquals(List.of("心内科"), response.suggestedDepartments());
        assertTrue(response.answer().contains("心内科"));
        verify(doctorFeignClient, never()).departmentNosWithDoctors();
    }

    @Test
    void chatShouldRecordFailureAndRethrowOriginalSearchError() {
        bindPatientIdentity(100L);
        IllegalStateException failure = new IllegalStateException("vector search failed");
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt())).thenThrow(failure);
        SymptomChatRequest request = request("CHAT-error", null, "头晕", null, null);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.chat(request));

        assertSame(failure, thrown);
        verify(callLogService).failed(eq("SYMPTOM_CHAT"), eq("100"), eq("CHAT-error"), eq("test-model"),
                eq("头晕"), anyLong(), eq(failure));
        verifyNoInteractions(chatMessageMapper);
    }

    @Test
    void chatShouldRejectForeignExistingSessionAndRecordFailedCall() {
        bindPatientIdentity(100L);
        when(chatSessionMapper.selectOne(any())).thenReturn(session(42L, 999L, "[]"));
        SymptomChatRequest request = request("CHAT-foreign", null, "头痛", null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.chat(request));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertTrue(error.getMessage().contains("他人问诊会话"));
        verify(callLogService).failed(eq("SYMPTOM_CHAT"), eq("100"), eq("CHAT-foreign"), eq("test-model"),
                eq("头痛"), anyLong(), eq(error));
        verifyNoInteractions(chatMessageMapper);
    }

    @Test
    void chatShouldRejectExistingSessionWithoutOwnerAndRecordFailedCall() {
        bindPatientIdentity(100L);
        when(chatSessionMapper.selectOne(any())).thenReturn(session(42L, null, "[]"));
        SymptomChatRequest request = request("CHAT-owner-missing", null, "头痛", null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.chat(request));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(callLogService).failed(eq("SYMPTOM_CHAT"), eq("100"), eq("CHAT-owner-missing"), eq("test-model"),
                eq("头痛"), anyLong(), eq(error));
        verifyNoInteractions(chatMessageMapper);
    }

    private AiChatMessageEntity capturedMessage() {
        ArgumentCaptor<AiChatMessageEntity> captor = ArgumentCaptor.forClass(AiChatMessageEntity.class);
        verify(chatMessageMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static SymptomChatRequest request(String sessionId, String patientId, String message,
                                              PatientContext context, RagOptions ragOptions) {
        return new SymptomChatRequest(sessionId, patientId, message, context, ragOptions);
    }

    private static AiChatSessionEntity session(Long id, Long patientId, String contextSymptoms) {
        AiChatSessionEntity session = new AiChatSessionEntity();
        session.setId(id);
        session.setSessionNo("CHAT-existing");
        session.setPatientId(patientId);
        session.setContextSymptoms(contextSymptoms);
        session.setStatus("ACTIVE");
        session.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        session.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return session;
    }

    private static AiChatMessageEntity message(String userMessage, String aiAnswer) {
        AiChatMessageEntity message = new AiChatMessageEntity();
        message.setUserMessage(userMessage);
        message.setAiAnswer(aiAnswer);
        return message;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private static DiseaseKnowledge knowledge(String vectorId, String sourceId, String diseaseName, String desc,
                                              Map<String, Object> metadata, String fieldName, String chunkText,
                                              double score) {
        return new DiseaseKnowledge(
                vectorId,
                sourceId,
                diseaseName,
                desc,
                List.of("头痛", "发热"),
                metadata,
                fieldName,
                chunkText,
                score,
                MatchSource.MILVUS_SEMANTIC
        );
    }

    private static RagReadinessService.RagReadiness readyRag() {
        return new RagReadinessService.RagReadiness(
                true,
                LocalDateTime.now(),
                List.of(new RagReadinessService.RagCheck("rag", "UP", "startup", 1, 1, "OK"))
        );
    }

    private static List<String> readSymptoms(String json) throws Exception {
        return JsonUtils.MAPPER.readValue(
                json,
                JsonUtils.MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, String.class)
        );
    }

    private static void bindPatientIdentity(Long patientId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, new JwtPayload(
                JwtPayload.SubjectType.USER,
                10L,
                null,
                "测试患者",
                List.of("PATIENT"),
                "PATIENT",
                patientId,
                null,
                null,
                "U202607180001",
                List.of("*"),
                "test-jti",
                4_102_444_800L
        ));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static AiProperties aiProperties() {
        return new AiProperties(
                new AiProperties.LlmProperties("http://localhost", "test", "test-model", 3),
                new AiProperties.EmbeddingProperties("http://localhost", "test", "test-embedding", 3),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
