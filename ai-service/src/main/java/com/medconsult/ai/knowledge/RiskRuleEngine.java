package com.medconsult.ai.knowledge;

import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RiskRuleEngine {
    private static final List<String> CRITICAL_TERMS = List.of(
            "持续胸痛", "呼吸困难", "意识障碍", "昏厥", "大出血", "抽搐", "咯血", "剧烈头痛"
    );
    private static final List<String> MEDIUM_TERMS = List.of(
            "胸闷", "心悸", "高血压", "发热", "喘息", "呕吐", "腹痛"
    );

    public RiskAssessment assess(String message, PatientContext context) {
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
        if (context != null && context.pastMedicalHistory() != null
                && context.pastMedicalHistory().stream().anyMatch(item -> item.contains("高血压") || item.contains("冠心病"))) {
            reasons.add("合并心血管相关既往病史");
        }
        if (!reasons.isEmpty()) {
            return new RiskAssessment("MEDIUM", false, reasons);
        }
        return new RiskAssessment("LOW", false, List.of());
    }
}
