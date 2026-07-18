package com.medconsult.ai.service;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.TriageRequest;
import com.medconsult.ai.dto.AiModels.TriageResponse;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.knowledge.RiskRuleEngine;
import com.medconsult.ai.persistence.entity.AiTriageResultEntity;
import com.medconsult.ai.persistence.mapper.AiTriageResultMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DoctorFeignClient;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriageServiceTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void triageShouldRejectUserWithoutPatientProfile() {
        bindPatientIdentity(null);
        AiTriageResultMapper triageResultMapper = mock(AiTriageResultMapper.class);
        TriageService service = triageService(
                mock(DiseaseSearchService.class),
                triageResultMapper,
                mock(AiCallLogService.class),
                mock(DoctorFeignClient.class),
                redisTemplate(null),
                mock(RiskRuleEngine.class)
        );

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.triage(request(List.of("咳嗽"), "三天")));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(triageResultMapper, times(0)).insert(any());
    }

    @Test
    void triageShouldFilterKnowledgeDepartmentsByDoctorAvailabilityAndPersistJwtPatient() {
        bindPatientIdentity(42L);
        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        DiseaseIntent intent = new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of()));
        when(diseaseSearchService.extractIntent(anyString())).thenReturn(intent);
        when(diseaseSearchService.search(anyString(), eq(intent), eq(5))).thenReturn(List.of(
                knowledge("肺炎", Map.of("cure_department", List.of("呼吸内科", "儿科", "心内科")))
        ));
        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        when(riskRuleEngine.assess(anyString(), isNull()))
                .thenReturn(new RiskAssessment("LOW", false, List.of("无高危症状")));
        DoctorFeignClient doctorFeignClient = mock(DoctorFeignClient.class);
        when(doctorFeignClient.departmentNosWithDoctors())
                .thenReturn(Result.ok(List.of("DEP_RESPIRATORY", "DEP_PEDIATRICS")));
        StringRedisTemplate redisTemplate = redisTemplate(null);
        AiTriageResultMapper triageResultMapper = mock(AiTriageResultMapper.class);
        AiCallLogService callLogService = mock(AiCallLogService.class);
        TriageService service = triageService(diseaseSearchService, triageResultMapper, callLogService,
                doctorFeignClient, redisTemplate, riskRuleEngine);

        TriageResponse response = service.triage(request(List.of("咳嗽", "发热"), "三天"));

        assertFalse(response.emergencyRecommended());
        assertEquals(List.of("DEP_RESPIRATORY", "DEP_PEDIATRICS"),
                response.recommendations().stream().map(item -> item.departmentId()).toList());
        ArgumentCaptor<AiTriageResultEntity> entityCaptor = ArgumentCaptor.forClass(AiTriageResultEntity.class);
        verify(triageResultMapper).insert(entityCaptor.capture());
        assertEquals(42L, entityCaptor.getValue().getPatientId());
        assertEquals("三天", entityCaptor.getValue().getDuration());
        verify(callLogService).success(eq("TRIAGE"), eq("42"), anyString(), eq("test-model"),
                anyString(), anyString(), eq("LOW"), anyLong());
        verify(redisTemplate.opsForValue()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void triageShouldUseCachedDoctorDepartmentsWithoutFeignCall() {
        bindPatientIdentity(77L);
        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        DiseaseIntent intent = new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of()));
        when(diseaseSearchService.extractIntent(anyString())).thenReturn(intent);
        when(diseaseSearchService.search(anyString(), eq(intent), eq(5))).thenReturn(List.of(
                knowledge("心律失常", Map.of("cure_department", "心内科"))
        ));
        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        when(riskRuleEngine.assess(anyString(), isNull()))
                .thenReturn(new RiskAssessment("LOW", false, List.of()));
        DoctorFeignClient doctorFeignClient = mock(DoctorFeignClient.class);
        TriageService service = triageService(diseaseSearchService, mock(AiTriageResultMapper.class),
                mock(AiCallLogService.class), doctorFeignClient, redisTemplate("[\"DEP_CARDIOLOGY\"]"),
                riskRuleEngine);

        TriageResponse response = service.triage(request(List.of("心悸"), "一天"));

        assertEquals("DEP_CARDIOLOGY", response.recommendations().getFirst().departmentId());
        assertEquals("心内科", response.recommendations().getFirst().departmentName());
        verify(doctorFeignClient, times(0)).departmentNosWithDoctors();
    }

    @Test
    void triageShouldFallbackToFirstAvailableDepartmentWhenMatchesAreFilteredOut() {
        bindPatientIdentity(88L);
        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        DiseaseIntent intent = new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of()));
        when(diseaseSearchService.extractIntent(anyString())).thenReturn(intent);
        when(diseaseSearchService.search(anyString(), eq(intent), eq(5))).thenReturn(List.of(
                knowledge("肺炎", Map.of("cure_department", List.of("呼吸内科")))
        ));
        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        when(riskRuleEngine.assess(anyString(), isNull()))
                .thenReturn(new RiskAssessment("LOW", false, List.of()));
        TriageService service = triageService(diseaseSearchService, mock(AiTriageResultMapper.class),
                mock(AiCallLogService.class), mock(DoctorFeignClient.class), redisTemplate("[\"DEP_PEDIATRICS\"]"),
                riskRuleEngine);

        TriageResponse response = service.triage(request(List.of("咳嗽"), "三天"));

        assertEquals("DEP_PEDIATRICS", response.recommendations().getFirst().departmentId());
        assertEquals("儿科", response.recommendations().getFirst().departmentName());
        assertEquals(1, response.recommendations().getFirst().priority());
    }

    private static TriageService triageService(DiseaseSearchService diseaseSearchService,
                                               AiTriageResultMapper triageResultMapper,
                                               AiCallLogService callLogService,
                                               DoctorFeignClient doctorFeignClient,
                                               StringRedisTemplate redisTemplate,
                                               RiskRuleEngine riskRuleEngine) {
        return new TriageService(
                diseaseSearchService,
                triageResultMapper,
                callLogService,
                aiProperties(),
                doctorFeignClient,
                redisTemplate,
                riskRuleEngine
        );
    }

    private static TriageRequest request(List<String> symptoms, String duration) {
        return new TriageRequest(null, symptoms, duration, "LOW", null, null, List.of(), false, null);
    }

    private static DiseaseKnowledge knowledge(String diseaseName, Map<String, Object> metadata) {
        return new DiseaseKnowledge(
                "vector-" + diseaseName,
                "DISEASE_JSON:" + diseaseName,
                diseaseName,
                diseaseName + "描述",
                List.of("咳嗽"),
                metadata,
                "cure_department",
                "疾病名称：" + diseaseName,
                0.82,
                MatchSource.MONGODB_NAME_EXACT
        );
    }

    private static StringRedisTemplate redisTemplate(String cachedValue) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedValue);
        return redisTemplate;
    }

    private static void bindPatientIdentity(Long patientId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, new JwtPayload(
                JwtPayload.SubjectType.USER,
                10L,
                null,
                "patient",
                List.of("PATIENT"),
                "PATIENT",
                patientId,
                null,
                null,
                "U202607170001",
                List.of("*"),
                "test-jti",
                4_102_444_800L
        ));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static AiProperties aiProperties() {
        return new AiProperties(
                new AiProperties.LlmProperties("http://localhost", "test", "test-model", 3),
                null,
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
