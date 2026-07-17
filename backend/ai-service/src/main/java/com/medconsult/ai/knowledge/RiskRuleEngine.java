package com.medconsult.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.persistence.entity.HighRiskSymptomRuleEntity;
import com.medconsult.ai.persistence.entity.NegativeRuleEntity;
import com.medconsult.ai.persistence.entity.SymptomRuleEntity;
import com.medconsult.ai.persistence.mapper.HighRiskSymptomRuleMapper;
import com.medconsult.ai.persistence.mapper.NegativeRuleMapper;
import com.medconsult.ai.persistence.mapper.SymptomRuleMapper;
import com.medconsult.ai.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class RiskRuleEngine {
    private static final Logger log = LoggerFactory.getLogger(RiskRuleEngine.class);

    private static final List<String> CRITICAL_TERMS = List.of(
            "持续胸痛", "呼吸困难", "意识障碍", "昏厥", "大出血", "抽搐", "咯血", "剧烈头痛"
    );
    private static final List<String> MEDIUM_TERMS = List.of(
            "胸闷", "心悸", "高血压", "发热", "喘息", "呕吐", "腹痛"
    );

    private final SymptomRuleMapper symptomRuleMapper;
    private final HighRiskSymptomRuleMapper highRiskSymptomRuleMapper;
    private final NegativeRuleMapper negativeRuleMapper;
    private volatile RuleSnapshot activeSnapshot = RuleSnapshot.fallback();
    private volatile boolean databaseSnapshotLoaded;

    public RiskRuleEngine() {
        this(null, null, null);
    }

    @Autowired
    public RiskRuleEngine(SymptomRuleMapper symptomRuleMapper,
                          HighRiskSymptomRuleMapper highRiskSymptomRuleMapper,
                          NegativeRuleMapper negativeRuleMapper) {
        this.symptomRuleMapper = symptomRuleMapper;
        this.highRiskSymptomRuleMapper = highRiskSymptomRuleMapper;
        this.negativeRuleMapper = negativeRuleMapper;
    }

    public RiskAssessment assess(String message, PatientContext context) {
        RuleSnapshot snapshot = currentSnapshot();
        if (!snapshot.fallbackOnly()) {
            RiskAssessment databaseAssessment = assessWithDatabaseSnapshot(message, context, snapshot);
            if (databaseAssessment != null) {
                return databaseAssessment;
            }
            return contextRisk(context);
        }
        return fallbackAssess(message, context);
    }

    public synchronized void refreshRules() {
        if (!hasMappers()) {
            activeSnapshot = RuleSnapshot.fallback();
            databaseSnapshotLoaded = true;
            return;
        }
        try {
            RuleSnapshot snapshot = loadDatabaseSnapshot();
            activeSnapshot = snapshot.empty() ? RuleSnapshot.fallback() : snapshot;
            databaseSnapshotLoaded = true;
            log.info("Loaded symptom risk rules: symptomRules={} riskRules={} negativeWords={}",
                    activeSnapshot.symptomRules().size(),
                    activeSnapshot.riskRules().size(),
                    activeSnapshot.negativeWords().size());
        } catch (RuntimeException ex) {
            databaseSnapshotLoaded = true;
            log.warn("Failed to refresh symptom risk rules, keeping previous snapshot: {}", ex.getMessage());
        }
    }

    private RuleSnapshot currentSnapshot() {
        if (hasMappers() && !databaseSnapshotLoaded) {
            refreshRules();
        }
        return activeSnapshot;
    }

    private boolean hasMappers() {
        return symptomRuleMapper != null && highRiskSymptomRuleMapper != null && negativeRuleMapper != null;
    }

    private RuleSnapshot loadDatabaseSnapshot() {
        List<SymptomRule> symptomRules = symptomRuleMapper.selectList(enabledSymptomRuleQuery()).stream()
                .map(entity -> new SymptomRule(
                        Objects.toString(entity.getKeyword(), "").trim(),
                        Objects.toString(entity.getStandardSymptom(), "").trim()
                ))
                .filter(rule -> !rule.keyword().isBlank() && !rule.standardSymptom().isBlank())
                .toList();
        List<RiskComboRule> riskRules = highRiskSymptomRuleMapper.selectList(enabledHighRiskRuleQuery()).stream()
                .map(entity -> new RiskComboRule(
                        parseStringList(entity.getSymptomCombo()),
                        normalizeRiskLevel(entity.getRiskLevel()),
                        Objects.toString(entity.getAdvice(), "").trim()
                ))
                .filter(rule -> !rule.symptomCombo().isEmpty() && !rule.advice().isBlank())
                .sorted(Comparator.comparingInt((RiskComboRule rule) -> severity(rule.riskLevel())).reversed())
                .toList();
        List<String> negativeWords = negativeRuleMapper.selectList(enabledNegativeRuleQuery()).stream()
                .map(NegativeRuleEntity::getNegativeWords)
                .flatMap(json -> parseStringList(json).stream())
                .map(String::trim)
                .filter(word -> !word.isBlank())
                .distinct()
                .toList();
        return new RuleSnapshot(symptomRules, riskRules, negativeWords, false);
    }

    private static LambdaQueryWrapper<SymptomRuleEntity> enabledSymptomRuleQuery() {
        return new LambdaQueryWrapper<SymptomRuleEntity>()
                .eq(SymptomRuleEntity::getEnabled, 1);
    }

    private static LambdaQueryWrapper<HighRiskSymptomRuleEntity> enabledHighRiskRuleQuery() {
        return new LambdaQueryWrapper<HighRiskSymptomRuleEntity>()
                .eq(HighRiskSymptomRuleEntity::getEnabled, 1);
    }

    private static LambdaQueryWrapper<NegativeRuleEntity> enabledNegativeRuleQuery() {
        return new LambdaQueryWrapper<NegativeRuleEntity>()
                .eq(NegativeRuleEntity::getEnabled, 1);
    }

    private RiskAssessment assessWithDatabaseSnapshot(String message, PatientContext context, RuleSnapshot snapshot) {
        String text = Objects.toString(message, "");
        Set<String> matchedSymptoms = new LinkedHashSet<>();
        for (SymptomRule rule : snapshot.symptomRules()) {
            if (text.contains(rule.keyword()) && !isNegated(text, rule.keyword(), snapshot.negativeWords())) {
                matchedSymptoms.add(rule.standardSymptom());
            }
        }
        if (matchedSymptoms.isEmpty()) {
            return null;
        }
        for (RiskComboRule rule : snapshot.riskRules()) {
            if (matchedSymptoms.containsAll(rule.symptomCombo())) {
                return new RiskAssessment(
                        rule.riskLevel(),
                        "HIGH".equals(rule.riskLevel()),
                        List.of(rule.advice())
                );
            }
        }
        RiskAssessment contextRisk = contextRisk(context);
        if (!"LOW".equals(contextRisk.riskLevel())) {
            return contextRisk;
        }
        return new RiskAssessment("LOW", false, List.of());
    }

    private static boolean isNegated(String text, String keyword, List<String> negativeWords) {
        if (negativeWords.isEmpty()) {
            return false;
        }
        int fromIndex = 0;
        boolean foundKeyword = false;
        while (fromIndex < text.length()) {
            int index = text.indexOf(keyword, fromIndex);
            if (index < 0) {
                break;
            }
            foundKeyword = true;
            int windowStart = Math.max(0, index - 8);
            String prefixWindow = text.substring(windowStart, index);
            boolean negated = negativeWords.stream().anyMatch(prefixWindow::contains);
            if (!negated) {
                return false;
            }
            fromIndex = index + keyword.length();
        }
        return foundKeyword;
    }

    private RiskAssessment fallbackAssess(String message, PatientContext context) {
        String text = Objects.toString(message, "");
        List<String> reasons = new ArrayList<>();
        for (String term : CRITICAL_TERMS) {
            if (text.contains(term)) {
                reasons.add("命中高危症状：" + term);
            }
        }
        if (!reasons.isEmpty()) {
            return new RiskAssessment("HIGH", true, reasons);
        }
        for (String term : MEDIUM_TERMS) {
            if (text.contains(term)) {
                reasons.add("命中需尽快评估的症状：" + term);
            }
        }
        reasons.addAll(contextRisk(context).reasons());
        if (!reasons.isEmpty()) {
            return new RiskAssessment("MEDIUM", false, reasons);
        }
        return new RiskAssessment("LOW", false, List.of());
    }

    private RiskAssessment contextRisk(PatientContext context) {
        List<String> reasons = new ArrayList<>();
        if (context != null && context.pastMedicalHistory() != null
                && context.pastMedicalHistory().stream().anyMatch(item -> item.contains("高血压") || item.contains("冠心病"))) {
            reasons.add("合并心血管相关既往病史");
        }
        if (!reasons.isEmpty()) {
            return new RiskAssessment("MEDIUM", false, reasons);
        }
        return new RiskAssessment("LOW", false, List.of());
    }

    private static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("Ignore malformed symptom rule JSON: {}", json);
            return List.of();
        }
    }

    private static String normalizeRiskLevel(String riskLevel) {
        String value = Objects.toString(riskLevel, "").trim().toUpperCase();
        return switch (value) {
            case "HIGH", "MEDIUM" -> value;
            default -> "LOW";
        };
    }

    private static int severity(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private record SymptomRule(String keyword, String standardSymptom) {
    }

    private record RiskComboRule(List<String> symptomCombo, String riskLevel, String advice) {
        private RiskComboRule {
            symptomCombo = symptomCombo == null ? List.of() : List.copyOf(symptomCombo);
        }
    }

    private record RuleSnapshot(
            List<SymptomRule> symptomRules,
            List<RiskComboRule> riskRules,
            List<String> negativeWords,
            boolean fallbackOnly
    ) {
        private RuleSnapshot {
            symptomRules = symptomRules == null ? List.of() : List.copyOf(symptomRules);
            riskRules = riskRules == null ? List.of() : List.copyOf(riskRules);
            negativeWords = negativeWords == null ? List.of() : List.copyOf(negativeWords);
        }

        private static RuleSnapshot fallback() {
            return new RuleSnapshot(List.of(), List.of(), List.of(), true);
        }

        private boolean empty() {
            return symptomRules.isEmpty() || riskRules.isEmpty();
        }
    }
}
