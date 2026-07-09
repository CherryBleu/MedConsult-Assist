package com.medconsult.ai.service;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.AvailableScheduleDto;
import com.medconsult.ai.dto.AiModels.TriageRecommendationDto;
import com.medconsult.ai.dto.AiModels.TriageRequest;
import com.medconsult.ai.dto.AiModels.TriageResponse;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.knowledge.RiskRuleEngine;
import com.medconsult.ai.persistence.entity.AiTriageResultEntity;
import com.medconsult.ai.persistence.mapper.AiTriageResultMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TriageService {
    private final DiseaseSearchService diseaseSearchService;
    private final AiTriageResultMapper triageResultMapper;
    private final AiCallLogService callLogService;
    private final AiProperties properties;
    private final RiskRuleEngine riskRuleEngine = new RiskRuleEngine();

    public TriageService(DiseaseSearchService diseaseSearchService, AiTriageResultMapper triageResultMapper,
                         AiCallLogService callLogService, AiProperties properties) {
        this.diseaseSearchService = diseaseSearchService;
        this.triageResultMapper = triageResultMapper;
        this.callLogService = callLogService;
        this.properties = properties;
    }

    public TriageResponse triage(TriageRequest request) {
        long started = System.currentTimeMillis();
        String text = String.join("、", request.symptoms()) + " " + (request.duration() == null ? "" : request.duration());
        DiseaseIntent intent = diseaseSearchService.extractIntent(text);
        List<DiseaseKnowledge> knowledge = diseaseSearchService.search(text, intent, 5);
        RiskAssessment risk = riskRuleEngine.assess(text, null);
        List<TriageRecommendationDto> recommendations = buildRecommendations(knowledge, risk);

        String triageNo = BusinessIds.next("TRIAGE");
        AiTriageResultEntity entity = new AiTriageResultEntity();
        entity.setTriageNo(triageNo);
        entity.setPatientId(BusinessIds.numericId(request.patientId()));
        entity.setSymptoms(JsonUtils.toJson(request.symptoms()));
        entity.setDuration(request.duration());
        entity.setSeverity(request.severity());
        entity.setEmergencyRecommended(risk.emergencyAdvice() ? 1 : 0);
        entity.setRecommendations(JsonUtils.toJson(recommendations));
        entity.setCitations(JsonUtils.toJson(knowledge));
        entity.setModelName(properties.llm().model());
        entity.setCreatedAt(LocalDateTime.now());
        triageResultMapper.insert(entity);
        callLogService.success("TRIAGE", request.patientId(), triageNo, properties.llm().model(),
                text, JsonUtils.toJson(recommendations), risk.riskLevel(), System.currentTimeMillis() - started);
        return new TriageResponse(risk.emergencyAdvice(), recommendations);
    }

    private List<TriageRecommendationDto> buildRecommendations(List<DiseaseKnowledge> knowledge, RiskAssessment risk) {
        Map<String, TriageRecommendationDto> result = new LinkedHashMap<>();
        if (risk.emergencyAdvice()) {
            result.put("急诊科", new TriageRecommendationDto("DEP_EMERGENCY", "急诊科", 0.98, 1,
                    "命中高危症状规则，建议优先急诊评估。", List.of()));
        }
        int priority = result.size() + 1;
        for (DiseaseKnowledge item : knowledge) {
            List<String> departments = metadataList(item.metadata().get("cure_department"));
            if (departments.isEmpty()) {
                departments = List.of("全科医学科");
            }
            for (String department : departments) {
                result.putIfAbsent(department, new TriageRecommendationDto(
                        departmentIdOf(department),
                        department,
                        Math.min(0.95, Math.max(0.55, item.score())),
                        priority++,
                        "与疾病 JSON 条目“" + item.diseaseName() + "”的症状或描述匹配。",
                        new ArrayList<AvailableScheduleDto>()
                ));
            }
        }
        if (result.isEmpty()) {
            result.put("全科医学科", new TriageRecommendationDto("DEP_GENERAL", "全科医学科", 0.5, 1,
                    "症状依据不足，建议先由全科进行初步评估。", List.of()));
        }
        return result.values().stream().limit(3).toList();
    }

    private static List<String> metadataList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return value == null || value.toString().isBlank() ? List.of() : List.of(value.toString());
    }

    private static String departmentIdOf(String department) {
        if (department.contains("心")) {
            return "DEP_CARDIOLOGY";
        }
        if (department.contains("呼吸")) {
            return "DEP_RESPIRATORY";
        }
        if (department.contains("儿")) {
            return "DEP_PEDIATRICS";
        }
        if (department.contains("急诊")) {
            return "DEP_EMERGENCY";
        }
        return "DEP_GENERAL";
    }
}
