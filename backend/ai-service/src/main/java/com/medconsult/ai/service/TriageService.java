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
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DoctorFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TriageService {
    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    /** 有医生科室集合的 Redis 缓存 key（5 分钟 TTL，避免分诊每次都 Feign 调 outpatient） */
    private static final String DEPT_WITH_DOCTORS_CACHE_KEY = "medconsult:ai:dept-with-doctors";
    private static final Duration DEPT_WITH_DOCTORS_TTL = Duration.ofMinutes(5);

    private final DiseaseSearchService diseaseSearchService;
    private final AiTriageResultMapper triageResultMapper;
    private final AiCallLogService callLogService;
    private final AiProperties properties;
    private final RiskRuleEngine riskRuleEngine = new RiskRuleEngine();
    private final DoctorFeignClient doctorFeignClient;
    private final StringRedisTemplate redisTemplate;

    public TriageService(DiseaseSearchService diseaseSearchService, AiTriageResultMapper triageResultMapper,
                         AiCallLogService callLogService, AiProperties properties,
                         DoctorFeignClient doctorFeignClient, StringRedisTemplate redisTemplate) {
        this.diseaseSearchService = diseaseSearchService;
        this.triageResultMapper = triageResultMapper;
        this.callLogService = callLogService;
        this.properties = properties;
        this.doctorFeignClient = doctorFeignClient;
        this.redisTemplate = redisTemplate;
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
        // 查询有启用医生的科室编号集合，过滤掉"无医生可预约"的空科室。
        // 避免推荐全科医学科等只有科室没有医生的空科室，导致用户挂号时无医生可选。
        Set<String> deptNosWithDoctors = departmentNosWithDoctors();

        Map<String, TriageRecommendationDto> result = new LinkedHashMap<>();
        if (risk.emergencyAdvice() && deptNosWithDoctors.contains("DEP_EMERGENCY")) {
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
                String deptNo = departmentIdOf(department);
                // 过滤无医生的科室：避免推荐用户挂号时找不到医生的空科室
                if (!deptNosWithDoctors.contains(deptNo)) {
                    continue;
                }
                result.putIfAbsent(department, new TriageRecommendationDto(
                        deptNo,
                        department,
                        Math.min(0.95, Math.max(0.55, item.score())),
                        priority++,
                        "与疾病 JSON 条目“" + item.diseaseName() + "”的症状或描述匹配。",
                        new ArrayList<AvailableScheduleDto>()
                ));
            }
        }
        if (result.isEmpty()) {
            // 所有疾病命中的科室都无医生时，兜底推荐第一个有医生的科室（而非全科医学科）。
            // 若全部科室都无医生（极端情况），退化为全科医学科保持原行为。
            String fallbackDeptNo = deptNosWithDoctors.isEmpty() ? "DEP_GENERAL" : deptNosWithDoctors.iterator().next();
            String fallbackDeptName = departmentNameOf(fallbackDeptNo);
            result.put(fallbackDeptName, new TriageRecommendationDto(fallbackDeptNo, fallbackDeptName, 0.5, 1,
                    "症状依据不足，建议先由" + fallbackDeptName + "进行初步评估。", List.of()));
        }
        return result.values().stream().limit(3).toList();
    }

    /**
     * 查询有启用医生的科室编号集合（带 5 分钟 Redis 缓存）。
     * <p>Feign 调 outpatient-service 的 /internal/departments/with-doctors。
     * 调用失败（outpatient 不可用）时降级为空集合——此时不过滤，保持原行为
     * （宁可推荐可能有空的科室，也不要因为 outpatient 不可用导致分诊无结果）。
     */
    private Set<String> departmentNosWithDoctors() {
        try {
            String cached = redisTemplate.opsForValue().get(DEPT_WITH_DOCTORS_CACHE_KEY);
            if (cached != null && !cached.isBlank()) {
                Set<String> nos = JsonUtils.MAPPER.readValue(cached,
                        JsonUtils.MAPPER.getTypeFactory().constructCollectionType(Set.class, String.class));
                if (!nos.isEmpty()) {
                    return nos;
                }
            }
            Result<List<String>> resp = doctorFeignClient.departmentNosWithDoctors();
            List<String> nos = resp == null ? null : resp.data();
            if (nos == null || nos.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> set = Set.copyOf(nos);
            redisTemplate.opsForValue().set(DEPT_WITH_DOCTORS_CACHE_KEY, JsonUtils.toJson(set), DEPT_WITH_DOCTORS_TTL);
            return set;
        } catch (Exception e) {
            log.warn("查询有医生科室失败，降级为不过滤: {}", e.getMessage());
            return Collections.emptySet();
        }
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

    /** department_no → 中文名（兜底场景用）。与 schema.sql 种子科室名对齐。 */
    private static String departmentNameOf(String departmentNo) {
        return switch (departmentNo) {
            case "DEP_CARDIOLOGY" -> "心内科";
            case "DEP_RESPIRATORY" -> "呼吸科";
            case "DEP_PEDIATRICS" -> "儿科";
            case "DEP_EMERGENCY" -> "急诊科";
            case "DEP_GENERAL" -> "全科医学科";
            default -> "全科医学科";
        };
    }
}
