package com.medconsult.ai.knowledge;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeComponentTest {
    @Test
    void oralChickenLikeCoughShouldPreferPertussis() {
        String query = QueryExpander.expand(
                "小孩经常咳嗽，声音像鸡叫一样",
                new DiseaseCandidate("", List.of(), "未能可靠提取")
        );

        DiseaseCandidate pertussis = new DiseaseCandidate(
                "百日咳",
                List.of("鸡鸣样吸气声", "痉挛性咳嗽", "咳后呕吐"),
                "典型表现为阵发性痉挛性咳嗽，咳嗽末伴鸡鸣样吸气声。"
        );
        DiseaseCandidate conversionDisorder = new DiseaseCandidate(
                "转换性障碍",
                List.of("情感暴发", "感觉障碍"),
                "与精神心理因素相关。"
        );

        assertTrue(FastCleanMatcher.weightedScore(query, pertussis) > FastCleanMatcher.weightedScore(query, conversionDisorder));
    }

    @Test
    void criticalSymptomShouldRecommendEmergency() {
        RiskAssessment assessment = new RiskRuleEngine().assess("持续胸痛并且呼吸困难", null);
        assertTrue(assessment.emergencyAdvice());
        assertTrue(assessment.riskLevel().equals("HIGH"));
    }
}
