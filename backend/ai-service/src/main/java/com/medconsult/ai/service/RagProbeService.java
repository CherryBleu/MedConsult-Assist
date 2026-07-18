package com.medconsult.ai.service;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.knowledge.RiskRuleEngine;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 固定症状 RAG 探针。
 *
 * <p>Readiness 只证明依赖存在和数据规模接近预期；探针用于验证真实问诊输入能召回
 * 可解释证据，并且高危症状仍由规则层提权。
 */
@Service
public class RagProbeService {
    private static final List<ProbeDefinition> PROBES = List.of(
            new ProbeDefinition(
                    "RESPIRATORY_COUGH",
                    "咳嗽有痰三天，伴发热，应该挂什么科",
                    5,
                    List.of("急性支气管炎", "肺炎"),
                    List.of("symptom", "cure_department"),
                    "MEDIUM",
                    false,
                    1
            ),
            new ProbeDefinition(
                    "PERTUSSIS_CHILD",
                    "孩子咳嗽像鸡叫，咳后呕吐，应该挂什么科",
                    5,
                    List.of("百日咳"),
                    List.of("symptom", "cure_department"),
                    null,
                    false,
                    1
            ),
            new ProbeDefinition(
                    "CARDIAC_EMERGENCY",
                    "持续胸痛伴呼吸困难和大汗",
                    5,
                    List.of("急性心肌梗死", "心肌梗死", "心绞痛", "急性冠脉综合征"),
                    List.of("symptom", "cure_department"),
                    "HIGH",
                    true,
                    1
            )
    );

    private final DiseaseSearchService diseaseSearchService;
    private final RiskRuleEngine riskRuleEngine;

    public RagProbeService(DiseaseSearchService diseaseSearchService, RiskRuleEngine riskRuleEngine) {
        this.diseaseSearchService = diseaseSearchService;
        this.riskRuleEngine = riskRuleEngine;
    }

    public ProbeRun runProbes() {
        List<ProbeResult> results = PROBES.stream().map(this::runProbe).toList();
        boolean passed = results.stream().allMatch(result -> "UP".equals(result.status()));
        return new ProbeRun(passed, LocalDateTime.now(), results);
    }

    private ProbeResult runProbe(ProbeDefinition probe) {
        try {
            DiseaseIntent intent = diseaseSearchService.extractIntent(probe.query());
            List<DiseaseKnowledge> knowledge = diseaseSearchService.search(probe.query(), intent, probe.topK());
            RiskAssessment risk = riskRuleEngine.assess(probe.query(), null);

            List<String> matchedDiseases = matchedDiseaseNames(knowledge);
            List<String> matchedFields = matchedFields(knowledge);
            List<String> reasonCodes = reasonCodes(probe, knowledge, matchedDiseases, matchedFields, risk);
            String status = reasonCodes.isEmpty() ? "UP" : "DOWN";
            return new ProbeResult(
                    probe.probeId(),
                    probe.query(),
                    status,
                    knowledge.size(),
                    matchedDiseases,
                    matchedFields,
                    risk == null ? "" : Objects.toString(risk.riskLevel(), ""),
                    risk != null && risk.emergencyAdvice(),
                    reasonCodes
            );
        } catch (RuntimeException ex) {
            return new ProbeResult(
                    probe.probeId(),
                    probe.query(),
                    "DOWN",
                    0,
                    List.of(),
                    List.of(),
                    "",
                    false,
                    List.of("PROBE_EXCEPTION:" + ex.getClass().getSimpleName())
            );
        }
    }

    private static List<String> reasonCodes(ProbeDefinition probe,
                                            List<DiseaseKnowledge> knowledge,
                                            List<String> matchedDiseases,
                                            List<String> matchedFields,
                                            RiskAssessment risk) {
        List<String> reasons = new ArrayList<>();
        if (knowledge.size() < probe.minResults()) {
            reasons.add("RESULT_COUNT_BELOW_MIN");
        }
        if (!matchesAnyExpectedDisease(matchedDiseases, probe.expectedDiseaseNames())) {
            reasons.add("EXPECTED_DISEASE_MISSING");
        }
        for (String requiredField : probe.requiredFields()) {
            if (matchedFields.stream().noneMatch(field -> field.equalsIgnoreCase(requiredField))) {
                reasons.add("EXPECTED_FIELD_MISSING:" + requiredField);
            }
        }
        if (probe.expectedRiskLevel() != null && !probe.expectedRiskLevel().equalsIgnoreCase(
                risk == null ? "" : Objects.toString(risk.riskLevel(), ""))) {
            reasons.add("RISK_LEVEL_MISMATCH");
        }
        if (probe.requireEmergencyAdvice() && (risk == null || !risk.emergencyAdvice())) {
            reasons.add("EMERGENCY_ADVICE_MISSING");
        }
        return reasons;
    }

    private static List<String> matchedDiseaseNames(List<DiseaseKnowledge> knowledge) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (DiseaseKnowledge item : knowledge) {
            String name = firstNonBlank(item.diseaseName(), item.sourceId());
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private static List<String> matchedFields(List<DiseaseKnowledge> knowledge) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        for (DiseaseKnowledge item : knowledge) {
            splitFields(item.fieldName()).forEach(fields::add);
            if (item.symptoms() != null && !item.symptoms().isEmpty()) {
                fields.add("symptom");
            }
            Map<String, Object> metadata = item.metadata();
            if (metadata != null) {
                metadata.keySet().stream()
                        .filter(field -> field != null && !field.isBlank())
                        .forEach(fields::add);
            }
        }
        return new ArrayList<>(fields);
    }

    private static List<String> splitFields(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return List.of();
        }
        return List.of(fieldName.split("[,，;；]")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static boolean matchesAnyExpectedDisease(List<String> matchedDiseases, List<String> expectedDiseases) {
        if (expectedDiseases.isEmpty()) {
            return true;
        }
        for (String matched : matchedDiseases) {
            for (String expected : expectedDiseases) {
                if (containsNormalized(matched, expected) || containsNormalized(expected, matched)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsNormalized(String left, String right) {
        String normalizedLeft = Objects.toString(left, "").trim().toLowerCase(Locale.ROOT);
        String normalizedRight = Objects.toString(right, "").trim().toLowerCase(Locale.ROOT);
        return !normalizedLeft.isBlank() && !normalizedRight.isBlank() && normalizedLeft.contains(normalizedRight);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record ProbeDefinition(
            String probeId,
            String query,
            int topK,
            List<String> expectedDiseaseNames,
            List<String> requiredFields,
            String expectedRiskLevel,
            boolean requireEmergencyAdvice,
            int minResults
    ) {
    }

    public record ProbeRun(boolean passed, LocalDateTime checkedAt, List<ProbeResult> probes) {
    }

    public record ProbeResult(
            String probeId,
            String query,
            String status,
            int resultCount,
            List<String> matchedDiseases,
            List<String> matchedFields,
            String riskLevel,
            boolean emergencyAdvice,
            List<String> reasonCodes
    ) {
    }
}
