package com.medconsult.ai.knowledge;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodingComplaintGuardTest {

    @Test
    void fixIfNeededShouldKeepOriginalIntentWhenUserTextHasNoReadableChinese() {
        DiseaseIntent llmResult = intent("编码异常", List.of("无法识别"), "model fallback");

        DiseaseIntent fixed = EncodingComplaintGuard.fixIfNeeded("persistent cough", llmResult);

        assertSame(llmResult, fixed);
    }

    @Test
    void fixIfNeededShouldKeepOriginalIntentWhenLlmResultIsNotEncodingComplaint() {
        DiseaseIntent llmResult = intent("支气管炎", List.of("咳嗽"), "呼吸道症状");

        DiseaseIntent fixed = EncodingComplaintGuard.fixIfNeeded("孩子咳嗽", llmResult);

        assertSame(llmResult, fixed);
    }

    @Test
    void fixIfNeededShouldInferChildWhoopingCoughMetadataFromReadableChinese() {
        DiseaseIntent llmResult = intent("编码异常", List.of("乱码"), "未能可靠提取");
        String userText = "小孩咳嗽像鸡叫，吐了，喘不上气，挂什么科，医保能报销吗，"
                + "怎么治疗，多少钱，要检查吗，吃什么药，忌口吗";

        DiseaseIntent fixed = EncodingComplaintGuard.fixIfNeeded(userText, llmResult);

        assertNotSame(llmResult, fixed);
        assertTrue(fixed.candidates().get(0).diseaseName().contains("百日咳"));
        assertTrue(fixed.toSearchInfo().symptoms().contains("儿童患者"));
        assertTrue(fixed.toSearchInfo().symptoms().contains("咳嗽"));
        assertTrue(fixed.toSearchInfo().symptoms().contains("咳后呕吐"));
        assertTrue(fixed.toSearchInfo().symptoms().contains("呼吸困难或喘息"));

        MetadataQuery query = fixed.metadataQuery();
        assertEquals(List.of(
                "cure_department",
                "yibao_status",
                "cure_way",
                "cure_lasttime",
                "cost_money",
                "check",
                "common_drug",
                "recommand_drug",
                "do_eat",
                "not_eat",
                "recommand_eat"), query.requestedFields());
        assertEquals(Map.of("cure_department", List.of("儿科")), query.filters());
    }

    @Test
    void fixIfNeededShouldUseGenericSymptomWhenReadableChineseHasNoKnownTerms() {
        DiseaseIntent llmResult = intent("乱码", List.of(), "模型无法可靠识别");

        DiseaseIntent fixed = EncodingComplaintGuard.fixIfNeeded("最近身体不舒服", llmResult);

        assertNotSame(llmResult, fixed);
        assertTrue(fixed.toSearchInfo().symptoms().contains("口语化症状描述"));
        assertEquals(List.of(), fixed.metadataQuery().requestedFields());
        assertEquals(Map.of(), fixed.metadataQuery().filters());
    }

    private static DiseaseIntent intent(String diseaseName, List<String> symptoms, String description) {
        return new DiseaseIntent(
                List.of(new DiseaseCandidate(diseaseName, symptoms, description)),
                new MetadataQuery(List.of(), Map.of()));
    }
}
