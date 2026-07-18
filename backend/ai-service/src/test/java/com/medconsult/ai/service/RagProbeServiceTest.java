package com.medconsult.ai.service;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.knowledge.RiskRuleEngine;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagProbeServiceTest {

    @Test
    void runProbesShouldPassWhenExpectedDiseaseFieldsAndHighRiskMatch() {
        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        RagProbeService service = new RagProbeService(diseaseSearchService, riskRuleEngine);
        DiseaseIntent intent = new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of()));
        when(diseaseSearchService.extractIntent(anyString())).thenReturn(intent);
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, String.class);
            if (query.contains("胸痛")) {
                return List.of(knowledge("心肌梗死", "symptom, ,cure_department", "胸痛、大汗、呼吸困难"));
            }
            if (query.contains("鸡叫")) {
                return List.of(knowledge("百日咳", "symptom,cure_department", "阵发性痉挛性咳嗽、咳后呕吐"));
            }
            return List.of(knowledge("急性支气管炎", "symptom,cure_department", "咳嗽、咳痰、发热"));
        });
        when(riskRuleEngine.assess(anyString(), any())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, String.class);
            if (query.contains("胸痛")) {
                return new RiskAssessment("HIGH", true, List.of("命中高危症状：持续胸痛"));
            }
            if (query.contains("发热")) {
                return new RiskAssessment("MEDIUM", false, List.of("命中需尽快评估的症状：发热"));
            }
            return new RiskAssessment("LOW", false, List.of());
        });

        RagProbeService.ProbeRun run = service.runProbes();

        assertTrue(run.passed());
        assertEquals(3, run.probes().size());
        assertTrue(run.probes().stream().allMatch(probe -> "UP".equals(probe.status())));
    }

    @Test
    void runProbesShouldFailWithActionableReasonsWhenRetrievalOrRiskMisses() {
        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        RagProbeService service = new RagProbeService(diseaseSearchService, riskRuleEngine);
        DiseaseIntent intent = new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of()));
        when(diseaseSearchService.extractIntent(anyString())).thenReturn(intent);
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt())).thenReturn(List.of());
        when(riskRuleEngine.assess(anyString(), any())).thenReturn(new RiskAssessment("LOW", false, List.of()));

        RagProbeService.ProbeRun run = service.runProbes();

        assertFalse(run.passed());
        assertTrue(run.probes().stream().anyMatch(probe ->
                probe.reasonCodes().contains("RESULT_COUNT_BELOW_MIN")
                        && probe.reasonCodes().contains("EXPECTED_DISEASE_MISSING")));
        assertTrue(run.probes().stream().anyMatch(probe ->
                probe.probeId().equals("CARDIAC_EMERGENCY")
                        && probe.reasonCodes().contains("RISK_LEVEL_MISMATCH")
                        && probe.reasonCodes().contains("EMERGENCY_ADVICE_MISSING")));
    }

    @Test
    void runProbesShouldReportProbeExceptionsAsDownReasons() {
        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        RagProbeService service = new RagProbeService(diseaseSearchService, riskRuleEngine);
        when(diseaseSearchService.extractIntent(anyString())).thenThrow(new IllegalStateException("milvus unavailable"));

        RagProbeService.ProbeRun run = service.runProbes();

        assertFalse(run.passed());
        assertEquals(3, run.probes().size());
        assertTrue(run.probes().stream().allMatch(probe -> "DOWN".equals(probe.status())));
        assertTrue(run.probes().stream().allMatch(probe ->
                probe.reasonCodes().contains("PROBE_EXCEPTION:IllegalStateException")));
    }

    @Test
    void runProbesShouldHandleSparseKnowledgeAndMissingRiskWithoutCrashing() {
        DiseaseSearchService diseaseSearchService = mock(DiseaseSearchService.class);
        RiskRuleEngine riskRuleEngine = mock(RiskRuleEngine.class);
        RagProbeService service = new RagProbeService(diseaseSearchService, riskRuleEngine);
        DiseaseIntent intent = new DiseaseIntent(List.of(), new MetadataQuery(List.of(), Map.of()));
        Map<String, Object> sparseMetadata = new HashMap<>();
        sparseMetadata.put(null, "ignored");
        sparseMetadata.put("", "ignored");
        sparseMetadata.put("   ", "ignored");
        sparseMetadata.put("cure_department", List.of("呼吸内科"));
        when(diseaseSearchService.extractIntent(anyString())).thenReturn(intent);
        when(diseaseSearchService.search(anyString(), any(DiseaseIntent.class), anyInt())).thenReturn(List.of(
                sparseKnowledge("", "DISEASE_JSON:肺炎", "symptom", List.of("发热"), sparseMetadata),
                sparseKnowledge("   ", "   ", null, null, null)
        ));
        when(riskRuleEngine.assess(anyString(), any())).thenReturn(null);

        RagProbeService.ProbeRun run = service.runProbes();

        assertFalse(run.passed());
        assertTrue(run.probes().stream().anyMatch(probe ->
                probe.probeId().equals("RESPIRATORY_COUGH")
                        && probe.matchedDiseases().contains("DISEASE_JSON:肺炎")
                        && probe.matchedFields().contains("cure_department")
                        && probe.riskLevel().isBlank()
                        && probe.reasonCodes().contains("RISK_LEVEL_MISMATCH")));
        assertTrue(run.probes().stream().anyMatch(probe ->
                probe.probeId().equals("CARDIAC_EMERGENCY")
                        && !probe.emergencyAdvice()
                        && probe.reasonCodes().contains("EMERGENCY_ADVICE_MISSING")));
    }

    private static DiseaseKnowledge knowledge(String diseaseName, String fieldName, String chunkText) {
        return new DiseaseKnowledge(
                "vec-" + diseaseName,
                "DISEASE_JSON:" + diseaseName,
                diseaseName,
                diseaseName + "描述",
                List.of("咳嗽"),
                Map.of("cure_department", List.of("呼吸内科")),
                fieldName,
                chunkText,
                0.91,
                MatchSource.MILVUS_SEMANTIC
        );
    }

    private static DiseaseKnowledge sparseKnowledge(String diseaseName,
                                                    String sourceId,
                                                    String fieldName,
                                                    List<String> symptoms,
                                                    Map<String, Object> metadata) {
        return new DiseaseKnowledge(
                "vec-sparse",
                sourceId,
                diseaseName,
                null,
                symptoms,
                metadata,
                fieldName,
                "",
                0.42,
                MatchSource.MILVUS_SEMANTIC
        );
    }
}
