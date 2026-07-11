package com.medconsult.ai.knowledge;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EncodingComplaintGuard {
    private EncodingComplaintGuard() {
    }

    public static DiseaseIntent fixIfNeeded(String userText, DiseaseIntent llmResult) {
        if (!hasReadableChinese(userText) || !isEncodingComplaint(llmResult)) {
            return llmResult;
        }
        return inferFromReadableChinese(userText);
    }

    private static boolean hasReadableChinese(String text) {
        return Objects.toString(text, "").codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static boolean isEncodingComplaint(DiseaseIntent result) {
        String text = result.searchText();
        return text.contains("编码异常")
                || text.contains("乱码")
                || text.contains("无法可靠识别")
                || text.contains("未能可靠提取");
    }

    private static DiseaseIntent inferFromReadableChinese(String userText) {
        List<String> symptoms = new ArrayList<>();
        String diseaseName = "待鉴别";
        String description = "用户输入为可读中文，已按症状语义继续检索。";

        if (containsAny(userText, "鸡叫", "鸡鸣", "像鸡")) {
            diseaseName = "百日咳";
            symptoms.addAll(List.of("鸡鸣样吸气声", "阵发性痉挛性咳嗽"));
            description = "儿童出现反复咳嗽并伴类似鸡鸣样吸气声，需重点鉴别百日咳等呼吸道疾病。";
        }
        if (containsAny(userText, "小孩", "孩子", "儿童", "宝宝")) {
            symptoms.add("儿童患者");
        }
        if (userText.contains("咳")) {
            symptoms.add("咳嗽");
        }
        if (containsAny(userText, "吐", "呕")) {
            symptoms.add("咳后呕吐");
        }
        if (containsAny(userText, "喘不上气", "喘", "憋")) {
            symptoms.add("呼吸困难或喘息");
        }
        if (symptoms.isEmpty()) {
            symptoms.add("口语化症状描述");
        }
        List<DiseaseCandidate> candidates = new ArrayList<>();
        candidates.add(new DiseaseCandidate(diseaseName, symptoms.stream().distinct().toList(), description));
        candidates.add(new DiseaseCandidate("急性支气管炎", List.of("咳嗽", "呼吸道症状"), "需结合发热、咳痰等表现鉴别。"));
        candidates.add(new DiseaseCandidate("上呼吸道感染", List.of("咳嗽", "鼻塞", "流涕"), "需结合鼻塞、流涕、咽痛等表现鉴别。"));
        return new DiseaseIntent(candidates, inferMetadataQuery(userText));
    }

    private static MetadataQuery inferMetadataQuery(String userText) {
        List<String> requestedFields = new ArrayList<>();
        Map<String, List<String>> filters = new HashMap<>();
        if (containsAny(userText, "挂什么科", "看什么科", "哪个科", "什么科", "科室", "挂号")) {
            requestedFields.add("cure_department");
        }
        if (containsAny(userText, "医保", "报销")) {
            requestedFields.add("yibao_status");
        }
        if (containsAny(userText, "怎么治", "治疗", "治法", "多久能好")) {
            requestedFields.add("cure_way");
            requestedFields.add("cure_lasttime");
        }
        if (containsAny(userText, "多少钱", "费用", "花费", "贵不贵")) {
            requestedFields.add("cost_money");
        }
        if (containsAny(userText, "检查", "查什么", "化验")) {
            requestedFields.add("check");
        }
        if (containsAny(userText, "吃什么药", "用药", "药")) {
            requestedFields.add("common_drug");
            requestedFields.add("recommand_drug");
        }
        if (containsAny(userText, "吃什么", "忌口", "饮食", "能不能吃")) {
            requestedFields.add("do_eat");
            requestedFields.add("not_eat");
            requestedFields.add("recommand_eat");
        }
        if (containsAny(userText, "小孩", "孩子", "儿童", "宝宝")) {
            filters.put("cure_department", List.of("儿科"));
        }
        return new MetadataQuery(requestedFields.stream().distinct().toList(), filters);
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
