package com.medconsult.ai.knowledge;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QueryExpander {
    private static final Map<String, List<String>> ORAL_MEDICAL_TERMS = Map.ofEntries(
            Map.entry("鸡叫", List.of("鸡鸣样吸气声", "阵发性痉挛性咳嗽", "百日咳", "儿童百日咳", "咳后呕吐")),
            Map.entry("像鸡", List.of("鸡鸣样吸气声", "百日咳")),
            Map.entry("小孩", List.of("儿童", "小儿", "儿科")),
            Map.entry("孩子", List.of("儿童", "小儿", "儿科")),
            Map.entry("咳到吐", List.of("咳后呕吐", "阵发性咳嗽", "痉挛性咳嗽")),
            Map.entry("经常咳嗽", List.of("反复咳嗽", "慢性咳嗽", "阵发性咳嗽")),
            Map.entry("老是咳", List.of("反复咳嗽", "慢性咳嗽", "阵发性咳嗽")),
            Map.entry("喘不上气", List.of("呼吸困难", "喘息"))
    );

    private QueryExpander() {
    }

    public static String expand(String userText, DiseaseCandidate llmInfo) {
        List<String> parts = new ArrayList<>();
        parts.add(Objects.toString(userText, ""));
        parts.add(llmInfo.searchText());

        String cleanUserText = Objects.toString(userText, "");
        ORAL_MEDICAL_TERMS.forEach((oral, medicalTerms) -> {
            if (cleanUserText.contains(oral)) {
                parts.addAll(medicalTerms);
            }
        });

        if (looksLikeLlmFailed(llmInfo)) {
            parts.add("口语化症状描述 家长描述 儿童呼吸道症状 鉴别诊断");
        }
        return String.join(" ", parts);
    }

    private static boolean looksLikeLlmFailed(DiseaseCandidate info) {
        String text = info.searchText();
        return info.diseaseName().isBlank()
                || text.contains("乱码")
                || text.contains("编码异常")
                || text.contains("无法可靠识别")
                || text.contains("未能可靠提取");
    }
}
