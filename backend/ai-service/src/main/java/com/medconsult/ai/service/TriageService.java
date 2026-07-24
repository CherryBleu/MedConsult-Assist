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
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DoctorFeignClient;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
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
    private final RiskRuleEngine riskRuleEngine;
    private final DoctorFeignClient doctorFeignClient;
    private final StringRedisTemplate redisTemplate;

    public TriageService(DiseaseSearchService diseaseSearchService, AiTriageResultMapper triageResultMapper,
                         AiCallLogService callLogService, AiProperties properties,
                         DoctorFeignClient doctorFeignClient, StringRedisTemplate redisTemplate,
                         RiskRuleEngine riskRuleEngine) {
        this.diseaseSearchService = diseaseSearchService;
        this.triageResultMapper = triageResultMapper;
        this.callLogService = callLogService;
        this.properties = properties;
        this.doctorFeignClient = doctorFeignClient;
        this.redisTemplate = redisTemplate;
        this.riskRuleEngine = riskRuleEngine;
    }

    public TriageResponse triage(TriageRequest request) {
        long started = System.currentTimeMillis();
        // 患者身份从 JWT 取（与 symptom-chat 同源），不信任请求体（IDOR 防越权）
        JwtPayload payload = SecurityContext.requireUser();
        Long jwtPatientId = payload.patientId();
        if (jwtPatientId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前账号未关联患者档案，请先完善个人档案后再使用智能分诊");
        }
        String patientIdStr = String.valueOf(jwtPatientId);

        String text = String.join("、", request.symptoms()) + " " + (request.duration() == null ? "" : request.duration());
        DiseaseIntent intent = diseaseSearchService.extractIntent(text);
        List<DiseaseKnowledge> knowledge = diseaseSearchService.search(text, intent, 5);
        RiskAssessment risk = riskRuleEngine.assess(text, null);
        List<TriageRecommendationDto> recommendations = buildRecommendations(knowledge, risk, text);

        String triageNo = BusinessIds.next("TRIAGE");
        AiTriageResultEntity entity = new AiTriageResultEntity();
        entity.setTriageNo(triageNo);
        entity.setPatientId(jwtPatientId);
        entity.setSymptoms(JsonUtils.toJson(request.symptoms()));
        entity.setDuration(request.duration());
        entity.setSeverity(request.severity());
        entity.setEmergencyRecommended(risk.emergencyAdvice() ? 1 : 0);
        entity.setRecommendations(JsonUtils.toJson(recommendations));
        entity.setCitations(JsonUtils.toJson(knowledge));
        entity.setModelName(properties.llm().model());
        entity.setCreatedAt(LocalDateTime.now());
        triageResultMapper.insert(entity);
        callLogService.success("TRIAGE", patientIdStr, triageNo, properties.llm().model(),
                text, JsonUtils.toJson(recommendations), risk.riskLevel(), System.currentTimeMillis() - started);
        return new TriageResponse(risk.emergencyAdvice(), recommendations);
    }

    private List<TriageRecommendationDto> buildRecommendations(List<DiseaseKnowledge> knowledge,
                                                               RiskAssessment risk,
                                                               String triageText) {
        // 查询有启用医生的科室编号集合，过滤掉"无医生可预约"的空科室。
        // 避免推荐全科医学科等只有科室没有医生的空科室，导致用户挂号时无医生可选。
        Set<String> deptNosWithDoctors = departmentNosWithDoctors();

        Map<String, TriageRecommendationDto> result = new LinkedHashMap<>();
        if (risk.emergencyAdvice() && departmentIsAvailable(deptNosWithDoctors, "DEP_EMERGENCY")) {
            result.put("急诊科", new TriageRecommendationDto("DEP_EMERGENCY", "急诊科", 0.98, 1,
                    "命中高危症状规则，建议优先急诊评估。", List.of()));
        }
        int priority = result.size() + 1;
        for (DiseaseKnowledge item : knowledge) {
            List<String> departments = metadataList(item.metadata() == null ? null : item.metadata().get("cure_department"));
            if (departments.isEmpty()) {
                departments = inferDepartments(triageText, item);
            }
            for (String department : departments) {
                String deptNo = departmentIdOf(department);
                // 过滤无医生的科室：避免推荐用户挂号时找不到医生的空科室
                if (!departmentIsAvailable(deptNosWithDoctors, deptNo)) {
                    continue;
                }
                result.putIfAbsent(department, new TriageRecommendationDto(
                        deptNo,
                        department,
                        confidenceOf(item.score(), departments),
                        priority++,
                        "与疾病 JSON 条目“" + item.diseaseName() + "”的症状或描述匹配。",
                        new ArrayList<AvailableScheduleDto>()
                ));
            }
        }
        if (result.isEmpty()) {
            List<String> inferredDepartments = inferDepartments(triageText, null);
            String fallbackDeptNo = inferredDepartments.stream()
                    .map(TriageService::departmentIdOf)
                    .filter(deptNo -> departmentIsAvailable(deptNosWithDoctors, deptNo))
                    .findFirst()
                    .orElseGet(() -> fallbackDepartmentNo(deptNosWithDoctors));
            String fallbackDeptName = departmentNameOf(fallbackDeptNo);
            double confidence = "DEP_GENERAL".equals(fallbackDeptNo) ? 0.6 : 0.72;
            result.put(fallbackDeptName, new TriageRecommendationDto(fallbackDeptNo, fallbackDeptName, confidence, 1,
                    "已根据症状关键词优先匹配到" + fallbackDeptName + "，建议由对应专科先评估。", List.of()));
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

    private static List<String> inferDepartments(String triageText, DiseaseKnowledge knowledge) {
        String text = joinText(
                triageText,
                knowledge == null ? "" : knowledge.diseaseName(),
                knowledge == null ? "" : knowledge.desc(),
                knowledge == null ? "" : knowledge.chunkText(),
                knowledge == null || knowledge.symptoms() == null ? "" : String.join(" ", knowledge.symptoms())
        );
        List<String> departments = new ArrayList<>();
        if (containsAny(text, "急诊", "胸痛", "胸闷", "大汗", "冷汗", "呼吸困难", "喘不上气", "昏厥", "意识")) {
            departments.add("急诊科");
        }
        if (containsAny(text, "心", "胸痛", "胸闷", "心悸", "心慌", "高血压", "冠心病", "心律失常")) {
            departments.add("心内科");
        }
        if (containsAny(text, "咳", "痰", "发热", "发烧", "喘", "呼吸", "肺", "咽痛", "鼻塞", "流涕")) {
            departments.add("呼吸科");
        }
        if (containsAny(text, "小孩", "孩子", "儿童", "宝宝", "婴幼儿", "儿科")) {
            departments.add("儿科");
        }
        return departments.stream().distinct().toList();
    }

    private static boolean departmentIsAvailable(Set<String> deptNosWithDoctors, String departmentNo) {
        return deptNosWithDoctors.isEmpty() || deptNosWithDoctors.contains(departmentNo);
    }

    private static double confidenceOf(double score, List<String> departments) {
        double minimum = departments.size() == 1 ? 0.72 : 0.65;
        return Math.min(0.95, Math.max(minimum, score));
    }

    private static String fallbackDepartmentNo(Set<String> deptNosWithDoctors) {
        if (deptNosWithDoctors.isEmpty()) {
            return "DEP_GENERAL";
        }
        return deptNosWithDoctors.stream()
                .filter(deptNo -> !"DEP_GENERAL".equals(deptNo))
                .findFirst()
                .orElse("DEP_GENERAL");
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

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String joinText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(" ", parts);
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
