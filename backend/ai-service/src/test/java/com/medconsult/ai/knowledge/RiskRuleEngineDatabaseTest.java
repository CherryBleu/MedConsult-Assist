package com.medconsult.ai.knowledge;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.persistence.entity.HighRiskSymptomRuleEntity;
import com.medconsult.ai.persistence.entity.NegativeRuleEntity;
import com.medconsult.ai.persistence.entity.SymptomRuleEntity;
import com.medconsult.ai.persistence.mapper.HighRiskSymptomRuleMapper;
import com.medconsult.ai.persistence.mapper.NegativeRuleMapper;
import com.medconsult.ai.persistence.mapper.SymptomRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskRuleEngineDatabaseTest {

    @Test
    void databaseRuleShouldDriveRiskAssessmentBeforeFallbackRules() {
        SymptomRuleMapper symptomRuleMapper = mock(SymptomRuleMapper.class);
        HighRiskSymptomRuleMapper highRiskRuleMapper = mock(HighRiskSymptomRuleMapper.class);
        NegativeRuleMapper negativeRuleMapper = mock(NegativeRuleMapper.class);
        when(symptomRuleMapper.selectList(any())).thenReturn(List.of(symptomRule("右下腹痛", "急腹症风险")));
        when(highRiskRuleMapper.selectList(any())).thenReturn(List.of(highRiskRule("[\"急腹症风险\"]", "HIGH", "疑似急腹症，请立即就医")));
        when(negativeRuleMapper.selectList(any())).thenReturn(List.of());

        RiskAssessment assessment = new RiskRuleEngine(symptomRuleMapper, highRiskRuleMapper, negativeRuleMapper)
                .assess("右下腹痛并伴随恶心", null);

        assertTrue(assessment.emergencyAdvice());
        assertTrue(assessment.riskLevel().equals("HIGH"));
        assertTrue(assessment.reasons().stream().anyMatch(reason -> reason.contains("疑似急腹症")));
    }

    @ParameterizedTest
    @CsvSource({
            "持续胸痛并且呼吸困难,HIGH,true",
            "胸闷伴随心悸,MEDIUM,false",
            "轻微疲劳,LOW,false"
    })
    void emptyDatabaseRulesShouldKeepHardcodedFallbackBehavior(String message, String expectedRiskLevel,
                                                              boolean expectedEmergencyAdvice) {
        SymptomRuleMapper symptomRuleMapper = mock(SymptomRuleMapper.class);
        HighRiskSymptomRuleMapper highRiskRuleMapper = mock(HighRiskSymptomRuleMapper.class);
        NegativeRuleMapper negativeRuleMapper = mock(NegativeRuleMapper.class);
        when(symptomRuleMapper.selectList(any())).thenReturn(List.of());
        when(highRiskRuleMapper.selectList(any())).thenReturn(List.of());
        when(negativeRuleMapper.selectList(any())).thenReturn(List.of());

        RiskAssessment assessment = new RiskRuleEngine(symptomRuleMapper, highRiskRuleMapper, negativeRuleMapper)
                .assess(message, null);

        assertEquals(expectedRiskLevel, assessment.riskLevel());
        assertEquals(expectedEmergencyAdvice, assessment.emergencyAdvice());
    }

    @Test
    void firstDatabaseRefreshFailureShouldUseHardcodedFallbackBehavior() {
        SymptomRuleMapper symptomRuleMapper = mock(SymptomRuleMapper.class);
        HighRiskSymptomRuleMapper highRiskRuleMapper = mock(HighRiskSymptomRuleMapper.class);
        NegativeRuleMapper negativeRuleMapper = mock(NegativeRuleMapper.class);
        when(symptomRuleMapper.selectList(any())).thenThrow(new IllegalStateException("database unavailable"));

        RiskAssessment assessment = new RiskRuleEngine(symptomRuleMapper, highRiskRuleMapper, negativeRuleMapper)
                .assess("胸闷伴随心悸", null);

        assertEquals("MEDIUM", assessment.riskLevel());
        assertFalse(assessment.emergencyAdvice());
    }

    @Test
    void negativeRuleShouldSuppressNegatedSymptomKeyword() {
        SymptomRuleMapper symptomRuleMapper = mock(SymptomRuleMapper.class);
        HighRiskSymptomRuleMapper highRiskRuleMapper = mock(HighRiskSymptomRuleMapper.class);
        NegativeRuleMapper negativeRuleMapper = mock(NegativeRuleMapper.class);
        when(symptomRuleMapper.selectList(any())).thenReturn(List.of(symptomRule("胸痛", "胸痛")));
        when(highRiskRuleMapper.selectList(any())).thenReturn(List.of(highRiskRule("[\"胸痛\"]", "HIGH", "胸痛高危")));
        when(negativeRuleMapper.selectList(any())).thenReturn(List.of(negativeRule("[\"没有\", \"否认\"]", "WINDOW")));

        RiskAssessment assessment = new RiskRuleEngine(symptomRuleMapper, highRiskRuleMapper, negativeRuleMapper)
                .assess("没有胸痛，也没有呼吸困难", null);

        assertFalse(assessment.emergencyAdvice());
        assertFalse(assessment.riskLevel().equals("HIGH"));
    }

    @Test
    void databaseLowMatchShouldNotDowngradeHardcodedCriticalEmergencyTerms() {
        SymptomRuleMapper symptomRuleMapper = mock(SymptomRuleMapper.class);
        HighRiskSymptomRuleMapper highRiskRuleMapper = mock(HighRiskSymptomRuleMapper.class);
        NegativeRuleMapper negativeRuleMapper = mock(NegativeRuleMapper.class);
        when(symptomRuleMapper.selectList(any())).thenReturn(List.of(symptomRule("轻微头晕", "轻症头晕")));
        when(highRiskRuleMapper.selectList(any())).thenReturn(List.of(highRiskRule("[\"急腹症风险\"]", "HIGH", "疑似急腹症")));
        when(negativeRuleMapper.selectList(any())).thenReturn(List.of());

        RiskAssessment assessment = new RiskRuleEngine(symptomRuleMapper, highRiskRuleMapper, negativeRuleMapper)
                .assess("轻微头晕，但持续胸痛并伴随呼吸困难", null);

        assertEquals("HIGH", assessment.riskLevel());
        assertTrue(assessment.emergencyAdvice());
        assertTrue(assessment.reasons().stream().anyMatch(reason -> reason.contains("持续胸痛")));
    }

    @Test
    void refreshFailureShouldKeepPreviousDatabaseSnapshot() {
        SymptomRuleMapper symptomRuleMapper = mock(SymptomRuleMapper.class);
        HighRiskSymptomRuleMapper highRiskRuleMapper = mock(HighRiskSymptomRuleMapper.class);
        NegativeRuleMapper negativeRuleMapper = mock(NegativeRuleMapper.class);
        when(symptomRuleMapper.selectList(any()))
                .thenReturn(List.of(symptomRule("右下腹痛", "急腹症风险")))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(highRiskRuleMapper.selectList(any())).thenReturn(List.of(highRiskRule("[\"急腹症风险\"]", "HIGH", "疑似急腹症")));
        when(negativeRuleMapper.selectList(any())).thenReturn(List.of());
        RiskRuleEngine engine = new RiskRuleEngine(symptomRuleMapper, highRiskRuleMapper, negativeRuleMapper);

        RiskAssessment first = engine.assess("右下腹痛", null);
        engine.refreshRules();
        RiskAssessment second = engine.assess("右下腹痛", null);

        assertTrue(first.emergencyAdvice());
        assertTrue(second.emergencyAdvice());
        assertTrue(second.reasons().stream().anyMatch(reason -> reason.contains("疑似急腹症")));
    }

    private static SymptomRuleEntity symptomRule(String keyword, String standardSymptom) {
        SymptomRuleEntity entity = new SymptomRuleEntity();
        entity.setKeyword(keyword);
        entity.setStandardSymptom(standardSymptom);
        entity.setEnabled(1);
        return entity;
    }

    private static HighRiskSymptomRuleEntity highRiskRule(String symptomCombo, String riskLevel, String advice) {
        HighRiskSymptomRuleEntity entity = new HighRiskSymptomRuleEntity();
        entity.setSymptomCombo(symptomCombo);
        entity.setRiskLevel(riskLevel);
        entity.setAdvice(advice);
        entity.setEnabled(1);
        return entity;
    }

    private static NegativeRuleEntity negativeRule(String negativeWords, String matchStrategy) {
        NegativeRuleEntity entity = new NegativeRuleEntity();
        entity.setNegativeWords(negativeWords);
        entity.setMatchStrategy(matchStrategy);
        entity.setEnabled(1);
        return entity;
    }
}
