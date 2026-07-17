package com.medconsult.ai.service;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.TriageRequest;
import com.medconsult.ai.dto.AiModels.TriageResponse;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.knowledge.RiskRuleEngine;
import com.medconsult.ai.persistence.entity.AiTriageResultEntity;
import com.medconsult.ai.persistence.mapper.AiTriageResultMapper;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriageServiceInjectedRiskRuleTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void triageShouldUseInjectedRiskRuleEngineForEmergencyDecision() {
        bindPatientIdentity(100L);

        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        when(diseaseSearchService.extractIntent(anyString()))
                .thenReturn(new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of())));
        when(diseaseSearchService.search(anyString(), any(), eq(5))).thenReturn(List.of());

        AiTriageResultMapper triageResultMapper = mock(AiTriageResultMapper.class);
        AiCallLogService callLogService = mock(AiCallLogService.class);
        DoctorFeignClient doctorFeignClient = mock(DoctorFeignClient.class);
        when(doctorFeignClient.departmentNosWithDoctors()).thenReturn(Result.ok(List.of("DEP_EMERGENCY")));

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        when(riskRuleEngine.assess(contains("右下腹痛"), isNull()))
                .thenReturn(new RiskAssessment("HIGH", true, List.of("数据库高危规则")));

        TriageService triageService = new TriageService(
                diseaseSearchService,
                triageResultMapper,
                callLogService,
                aiProperties(),
                doctorFeignClient,
                redisTemplate,
                riskRuleEngine
        );

        TriageResponse response = triageService.triage(new TriageRequest(
                null,
                List.of("右下腹痛"),
                "2小时",
                "HIGH",
                null,
                null,
                List.of(),
                false,
                null
        ));

        assertTrue(response.emergencyRecommended());
        assertEquals("急诊科", response.recommendations().getFirst().departmentName());
        verify(riskRuleEngine).assess(contains("右下腹痛"), isNull());

        ArgumentCaptor<AiTriageResultEntity> entityCaptor = ArgumentCaptor.forClass(AiTriageResultEntity.class);
        verify(triageResultMapper).insert(entityCaptor.capture());
        assertEquals(1, entityCaptor.getValue().getEmergencyRecommended());

        verify(callLogService).success(eq("TRIAGE"), eq("100"), anyString(), eq("test-model"),
                contains("右下腹痛"), anyString(), eq("HIGH"), anyLong());
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
