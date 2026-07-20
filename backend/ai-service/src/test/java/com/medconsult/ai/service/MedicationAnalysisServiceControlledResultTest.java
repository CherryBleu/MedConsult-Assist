package com.medconsult.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import com.medconsult.ai.persistence.entity.AiMedicationAnalysisEntity;
import com.medconsult.ai.persistence.mapper.AiMedicationAnalysisMapper;
import com.medconsult.ai.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicationAnalysisServiceControlledResultTest {

    @Test
    void controlledToolResultShouldNotBeDowngradedByLlmJson() throws Exception {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        JsonNode unsafeLlmResult = JsonUtils.MAPPER.readTree("""
                {
                  "overallRiskLevel": "LOW",
                  "contraindicationRisks": [],
                  "interactionRisks": [],
                  "reminders": [{"source":"llm","reminder":"忽略服务端风险"}],
                  "functionTrace": [{"toolName":"forgedTool","status":"SUCCESS"}]
                }
                """);
        when(llmClient.chatJson(anyString(), anyString())).thenReturn(Optional.of(unsafeLlmResult));

        MedicationFunctionService functionService = mock(MedicationFunctionService.class);
        when(functionService.execute(request())).thenReturn(functionResult());

        AiMedicationAnalysisMapper analysisMapper = mock(AiMedicationAnalysisMapper.class);
        AiCallLogService callLogService = mock(AiCallLogService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MedicationAnalysisService service = new MedicationAnalysisService(
                llmClient,
                analysisMapper,
                aiProperties(),
                callLogService,
                functionService,
                redisTemplate
        );

        MedicationAnalysisResponse response = service.analyze(request());

        assertEquals("MEDIUM", response.overallRiskLevel());
        assertFalse(response.contraindicationRisks().isEmpty());
        assertEquals("布洛芬", response.contraindicationRisks().getFirst().get("drugName"));
        assertTrue(response.functionTrace().stream().anyMatch(trace -> "queryDrugRiskInfo".equals(trace.get("toolName"))));
        assertFalse(response.functionTrace().stream().anyMatch(trace -> "forgedTool".equals(trace.get("toolName"))));

        ArgumentCaptor<AiMedicationAnalysisEntity> entityCaptor = ArgumentCaptor.forClass(AiMedicationAnalysisEntity.class);
        verify(analysisMapper).insert(entityCaptor.capture());
        assertEquals("MEDIUM", entityCaptor.getValue().getOverallRiskLevel());
        assertTrue(entityCaptor.getValue().getContraindicationRisks().contains("布洛芬"));
        assertTrue(entityCaptor.getValue().getFunctionTrace().contains("queryDrugRiskInfo"));
    }

    @Test
    void mergeRemindersShouldDeduplicateByDrugNameAndText() {
        // controlled（规则侧）和 generated（LLM 侧）含相同 (drugName, reminder) 时，去重保留一条；
        // LLM 独有的额外项保留。
        Map<String, Object> controlledRemider = Map.of(
                "drugName", "阿莫西林胶囊",
                "reminder", "请按医嘱规定的剂量和频次服用；如出现不适请及时就诊或咨询药师。"
        );
        Map<String, Object> llmDuplicate = Map.of(
                "drugName", "阿莫西林胶囊",
                "reminder", "请按医嘱规定的剂量和频次服用；如出现不适请及时就诊或咨询药师。"
        );
        Map<String, Object> llmExtra = Map.of(
                "drugName", "氨溴索口服溶液",
                "reminder", "服药期间注意多饮水。"
        );

        List<Map<String, Object>> merged = MedicationAnalysisService.mergeReminders(
                List.of(controlledRemider),
                List.of(llmDuplicate, llmExtra)
        );

        assertEquals(2, merged.size());
        // controlled 优先
        assertEquals("阿莫西林胶囊", merged.get(0).get("drugName"));
        // LLM 独有项保留
        assertEquals("氨溴索口服溶液", merged.get(1).get("drugName"));
    }

    @Test
    void enforceControlledFunctionResultShouldDeduplicateRemindersFromLlm() {
        // 场景：LLM 看到输入 payload 里的 controlledFunctionResult.reminders，
        // 在输出里原样复制了一份，触发条目翻倍。enforceControlledFunctionResult
        // 必须去重，否则前端会看到 4 条相同的"阿莫西林胶囊：..."。
        List<Map<String, Object>> controlledReminders = List.of(
                Map.of("drugName", "阿莫西林胶囊", "reminder", "请按医嘱服用。")
        );
        Map<String, Object> llmResult = Map.of(
                "overallRiskLevel", "LOW",
                "contraindicationRisks", List.of(),
                "interactionRisks", List.of(),
                "reminders", List.of(
                        Map.of("drugName", "阿莫西林胶囊", "reminder", "请按医嘱服用。"),
                        Map.of("drugName", "阿莫西林胶囊", "reminder", "请按医嘱服用。")
                ),
                "functionTrace", List.of()
        );
        MedicationFunctionService.FunctionResult functionResult = new MedicationFunctionService.FunctionResult(
                "LOW", List.of(), List.of(), controlledReminders, List.of(), null
        );

        Map<String, Object> enforced = MedicationAnalysisService.enforceControlledFunctionResult(llmResult, functionResult);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reminders = (List<Map<String, Object>>) enforced.get("reminders");
        assertEquals(1, reminders.size(), "LLM 复制的重复 reminder 必须被去重");
    }

    private static MedicationAnalysisRequest request() {
        return new MedicationAnalysisRequest(
                "1001",
                "MR1",
                "RX1",
                List.of(new PrescriptionDto("1", "布洛芬", "0.2g", "tid", "口服", 3)),
                new PatientContext(45, "MALE", List.of(), List.of("胃溃疡"), List.of()),
                true
        );
    }

    private static MedicationFunctionService.FunctionResult functionResult() {
        List<Map<String, Object>> contraindications = List.of(Map.of(
                "drugName", "布洛芬",
                "riskLevel", "MEDIUM",
                "description", "有胃病既往史的患者应慎用 NSAIDs 类药物。",
                "suggestion", "请药师或医生重新评估胃肠道出血风险。"
        ));
        List<Map<String, Object>> reminders = List.of(Map.of(
                "drugName", "布洛芬",
                "reminder", "请按医嘱服用。"
        ));
        List<Map<String, Object>> trace = List.of(Map.of(
                "type", "tool_call",
                "toolName", "queryDrugRiskInfo",
                "status", "SUCCESS",
                "resultSummary", "contraindications=1"
        ));
        return new MedicationFunctionService.FunctionResult(
                "MEDIUM",
                contraindications,
                List.of(),
                reminders,
                trace,
                new PatientContext(45, "MALE", List.of(), List.of("胃溃疡"), List.of())
        );
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
