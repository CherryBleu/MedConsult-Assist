package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.CitationDto;
import com.medconsult.ai.dto.AiModels.SymptomChatRequest;
import com.medconsult.ai.dto.AiModels.SymptomChatResponse;
import com.medconsult.ai.dto.AiModels.VectorMatchDto;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.knowledge.KnowledgeFields;
import com.medconsult.ai.knowledge.RiskRuleEngine;
import com.medconsult.ai.persistence.entity.AiChatMessageEntity;
import com.medconsult.ai.persistence.entity.AiChatSessionEntity;
import com.medconsult.ai.persistence.mapper.AiChatMessageMapper;
import com.medconsult.ai.persistence.mapper.AiChatSessionMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.ai.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SymptomChatService {
    private static final Logger log = LoggerFactory.getLogger(SymptomChatService.class);

    private final DiseaseSearchService diseaseSearchService;
    private final AiProperties properties;
    private final AiChatSessionMapper chatSessionMapper;
    private final AiChatMessageMapper chatMessageMapper;
    private final AiCallLogService callLogService;
    private final DoctorFeignClient doctorFeignClient;
    private final StringRedisTemplate redisTemplate;
    private final RagReadinessService ragReadinessService;
    private final RiskRuleEngine riskRuleEngine;

    /** 有医生科室集合的 Redis 缓存 key（5 分钟 TTL，与 TriageService 共用） */
    private static final String DEPT_WITH_DOCTORS_CACHE_KEY = "medconsult:ai:dept-with-doctors";
    private static final Duration DEPT_WITH_DOCTORS_TTL = Duration.ofMinutes(5);

    public SymptomChatService(
            DiseaseSearchService diseaseSearchService,
            AiProperties properties,
            AiChatSessionMapper chatSessionMapper,
            AiChatMessageMapper chatMessageMapper,
            AiCallLogService callLogService,
            DoctorFeignClient doctorFeignClient,
            StringRedisTemplate redisTemplate,
            RagReadinessService ragReadinessService,
            RiskRuleEngine riskRuleEngine
    ) {
        this.diseaseSearchService = diseaseSearchService;
        this.properties = properties;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.callLogService = callLogService;
        this.doctorFeignClient = doctorFeignClient;
        this.redisTemplate = redisTemplate;
        this.ragReadinessService = ragReadinessService;
        this.riskRuleEngine = riskRuleEngine;
    }

    /**
     * 创建问诊会话（对齐前端 POST /ai/symptom-chat/session）。
     * 返回新会话编号，前端拿到后用于后续 sendChatMessage。
     *
     * <p>患者身份从 JWT 取（架构 §4.3/§5.1：以登录账号绑定的 patient_id 为准，
     * 不信任请求体或查询参数传入的 patientId，禁止代他人发起自诊）。
     * 账号未关联患者档案时返回业务错误并提示先建档。
     */
    @Transactional
    public SessionCreated createSession() {
        Long patientId = resolveCurrentPatientId();
        String sessionNo = BusinessIds.next("CHAT");
        AiChatSessionEntity session = new AiChatSessionEntity();
        session.setSessionNo(sessionNo);
        session.setPatientId(patientId);
        session.setTitle("智能问诊会话");
        session.setStatus("ACTIVE");
        session.setContextSymptoms(JsonUtils.toJson(List.of()));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return new SessionCreated(sessionNo, session.getTitle(), session.getStatus());
    }

    /**
     * 从 JWT 解析当前登录患者的主键 patient_id（架构 §4.3 SELF 数据范围）。
     *
     * <p>不信任请求体传入的 patientId——即便请求体带了，也仅作一致性校验：与 JWT 不符则
     * 抛 FORBIDDEN（禁止代他人发起自诊）。账号未关联患者档案（patientId 为 null）时抛
     * 业务错误并提示先完善个人档案（§5.1 L454）。
     */
    private Long resolveCurrentPatientId() {
        JwtPayload payload = SecurityContext.requireUser();
        Long jwtPatientId = payload.patientId();
        if (jwtPatientId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前账号未关联患者档案，请先完善个人档案后再使用智能问诊");
        }
        return jwtPatientId;
    }

    /**
     * 获取会话历史消息（对齐前端 GET /ai/symptom-chat/history/{sessionId}）。
     *
     * <p>归属校验：只能查本人的会话历史。session.patient_id 必须与当前登录患者的 JWT
     * patient_id 一致，否则抛 FORBIDDEN（防止 IDOR 越权读取他人问诊记录）。
     */
    public List<ChatHistoryItem> getHistory(String sessionId) {
        Long patientId = resolveCurrentPatientId();
        // sessionId 入参是业务编号 sessionNo，先查 sessions 表拿主键 id
        AiChatSessionEntity session = chatSessionMapper.selectOne(new LambdaQueryWrapper<AiChatSessionEntity>()
                .eq(AiChatSessionEntity::getSessionNo, sessionId)
                .last("limit 1"));
        if (session == null) {
            return List.of();
        }
        // 会话归属校验：只能查自己的会话历史
        if (session.getPatientId() != null && !session.getPatientId().equals(patientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看他人问诊历史");
        }
        return chatMessageMapper.selectList(new LambdaQueryWrapper<AiChatMessageEntity>()
                        .eq(AiChatMessageEntity::getSessionId, session.getId())
                        .orderByAsc(AiChatMessageEntity::getCreatedAt))
                .stream()
                .map(item -> new ChatHistoryItem(
                        "user", item.getUserMessage(),
                        "ai", item.getAiAnswer()
                ))
                .toList();
    }

    /** 会话创建结果 */
    public record SessionCreated(String sessionId, String title, String status) {}

    /** 单条历史消息（user/ai 配对） */
    public record ChatHistoryItem(String role, String content, String aiRole, String aiContent) {}

    @Transactional
    public SymptomChatResponse chat(SymptomChatRequest request) {
        // 患者身份从 JWT 取，不信任请求体（架构 §4.3 SELF）。
        // 请求体带了 patientId 时仅作一致性校验，与 JWT 不符则拒绝（禁止代他人自诊）。
        Long patientId = resolveCurrentPatientId();
        if (request.patientId() != null && !request.patientId().isBlank()) {
            Long requested = BusinessIds.numericId(request.patientId());
            if (requested != null && !requested.equals(patientId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "禁止代他人发起智能问诊");
            }
        }
        long started = System.currentTimeMillis();
        try {
            long stepStarted = System.currentTimeMillis();
            DiseaseIntent intent = diseaseSearchService.extractIntent(request.message());
            log.info("[AI-TIMER] symptomChat.extractIntent={}ms", elapsed(stepStarted));

            stepStarted = System.currentTimeMillis();
            int topK = request.ragOptions() == null ? 5 : request.ragOptions().safeTopK();
            List<DiseaseKnowledge> knowledge = diseaseSearchService.search(request.message(), intent, topK);
            RagReadinessService.RagReadiness ragReadiness = ragReadinessService.current();
            log.info("[AI-TIMER] symptomChat.search={}ms knowledge={} ragReady={}",
                    elapsed(stepStarted), knowledge.size(), ragReadiness.ready());

            stepStarted = System.currentTimeMillis();
            RiskAssessment risk = riskRuleEngine.assess(request.message(), request.patientContext());
            List<CitationDto> citations = toCitations(knowledge);
            List<String> departments = extractDepartments(knowledge, risk);
            List<String> causes = knowledge.stream().map(this::displayDiseaseName).filter(item -> !item.isBlank()).distinct().toList();
            log.info("[AI-TIMER] symptomChat.prepare={}ms citations={} departments={} causes={}",
                    elapsed(stepStarted), citations.size(), departments.size(), causes.size());

            stepStarted = System.currentTimeMillis();
            String answer = generateFinalAnswer(request, knowledge, citations, departments, risk, ragReadiness);
            log.info("[AI-TIMER] symptomChat.finalAnswer={}ms", elapsed(stepStarted));

            stepStarted = System.currentTimeMillis();
            AiChatSessionEntity session = ensureSession(request, patientId);
            AiChatMessageEntity message = new AiChatMessageEntity();
            message.setSessionId(session.getId());
            message.setPatientId(patientId);
            message.setUserMessage(request.message());
            message.setAiAnswer(answer);
            message.setCitations(JsonUtils.toJson(citations));
            message.setSuggestedDepartments(JsonUtils.toJson(departments));
            message.setRiskLevel(risk.riskLevel());
            message.setEmergencyAdvice(risk.emergencyAdvice() ? 1 : 0);
            message.setModelName(properties.llm().model());
            message.setQueryEmbeddingModel(properties.embedding().model());
            message.setRuleVersion("v1");
            message.setCreatedAt(LocalDateTime.now());
            chatMessageMapper.insert(message);
            log.info("[AI-TIMER] symptomChat.dbWrite={}ms", elapsed(stepStarted));

            stepStarted = System.currentTimeMillis();
            SymptomChatResponse response = new SymptomChatResponse(
                    request.sessionId(),
                    answer,
                    ragReadiness.ready() ? "VECTOR_SEARCH_AND_RULE" : "VECTOR_SEARCH_AND_RULE_DEGRADED",
                    causes,
                    departments,
                    risk.riskLevel(),
                    risk.emergencyAdvice(),
                    knowledge.stream().map(this::toVectorMatch).toList(),
                    citations
            );
            callLogService.success("SYMPTOM_CHAT", String.valueOf(patientId), request.sessionId(), properties.llm().model(),
                    request.message(), answer, risk.riskLevel(), System.currentTimeMillis() - started);
            log.info("[AI-TIMER] symptomChat.callLog={}ms", elapsed(stepStarted));
            log.info("[AI-TIMER] symptomChat.total={}ms", elapsed(started));
            return response;
        } catch (RuntimeException ex) {
            callLogService.failed("SYMPTOM_CHAT", String.valueOf(patientId), request.sessionId(), properties.llm().model(),
                    request.message(), System.currentTimeMillis() - started, ex);
            log.info("[AI-TIMER] symptomChat.total={}ms success=false error={}",
                    elapsed(started), ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String generateFinalAnswer(SymptomChatRequest request, List<DiseaseKnowledge> knowledge,
                                       List<CitationDto> citations, List<String> departments, RiskAssessment risk,
                                       RagReadinessService.RagReadiness ragReadiness) {
        // 症状自诊不调用生成式 LLM（docs/修改建议.md §3.1 P0 铁律）：
        // 回答仅由规则模板拼装（ruleBasedAnswer），answer 文本可追溯到疾病 JSON 命中片段，
        // 不含模型自由生成内容。Embedding 检索仍允许（仅向量化，非生成式）。
        long started = System.currentTimeMillis();
        if (knowledge.isEmpty()) {
            log.info("[AI-TIMER] symptomChat.generateFinalAnswer={}ms mode=rule_empty_knowledge ragReady={}",
                    elapsed(started), ragReadiness.ready());
            String degraded = ragReadiness.ready() ? "" : "当前知识库自检未完全通过（" + summarizeRagIssues(ragReadiness) + "），检索结果可能不完整。";
            return degraded + "暂未检索到与您描述匹配的疾病条目。建议您：1）尝试补充更具体的症状描述（如部位、持续时间、诱因、伴随症状）；2）若症状持续、加重或出现胸痛、呼吸困难、意识异常等危急情况，请立即就医。本结果仅供健康咨询参考，不能替代医生诊断。";
        }
        String answer = ruleBasedAnswer(knowledge, departments, risk);
        log.info("[AI-TIMER] symptomChat.generateFinalAnswer={}ms mode=rule_template", elapsed(started));
        return answer;
    }

    private String ruleBasedAnswer(List<DiseaseKnowledge> knowledge, List<String> departments, RiskAssessment risk) {
        String diseases = String.join("；", knowledge.stream()
                .map(this::formatKnowledgeSummary)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList());
        String deptText = departments.isEmpty() ? "相关专科" : String.join("、", departments);
        String action = actionAdvice(risk);
        String evidence = diseases.isBlank() ? "已检索到相关疾病 JSON 条目，但条目名称或描述缺失" : diseases;
        return action + "根据疾病 JSON 检索结果，相关条目包括：" + evidence
                + "。建议咨询：" + deptText
                + "，就诊时可说明症状出现时间、诱因、是否伴随发热/胸痛/呼吸困难等，并由医生判断需要的检查。非诊断，仅供参考，不能替代医生诊断。";
    }

    private static String summarizeRagIssues(RagReadinessService.RagReadiness readiness) {
        if (readiness == null || readiness.checks() == null || readiness.checks().isEmpty()) {
            return "UNKNOWN";
        }
        String issues = String.join(";", readiness.checks().stream()
                .filter(check -> !"UP".equals(check.status()))
                .map(check -> check.name() + ":" + check.reason())
                .toList());
        return issues.isBlank() ? "UNKNOWN" : issues;
    }

    private String actionAdvice(RiskAssessment risk) {
        if (risk.emergencyAdvice()) {
            return "下一步建议：你的描述包含高危症状，建议立即到急诊或呼叫急救，由医生尽快评估。";
        }
        if ("MEDIUM".equalsIgnoreCase(risk.riskLevel())) {
            return "下一步建议：建议尽快到门诊就医评估，症状加重或出现胸痛、呼吸困难、咯血、昏厥时立即急诊。";
        }
        return "下一步建议：若症状轻微且未加重，可短期观察；如持续不缓解、反复出现或影响日常活动，建议门诊评估。";
    }

    private AiChatSessionEntity ensureSession(SymptomChatRequest request, Long patientId) {
        AiChatSessionEntity existing = chatSessionMapper.selectOne(new LambdaQueryWrapper<AiChatSessionEntity>()
                .eq(AiChatSessionEntity::getSessionNo, request.sessionId())
                .last("limit 1"));
        if (existing != null) {
            // 会话归属校验：已有会话的 patient_id 必须与当前登录患者一致，防止串用他人会话。
            if (existing.getPatientId() != null && !existing.getPatientId().equals(patientId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人问诊会话");
            }
            // 追加而非覆盖：累积多轮对话的症状，保留完整会话上下文汇总。
            // 之前每次覆盖导致 contextSymptoms 只保留最后一条消息，丢失多轮症状演变。
            List<String> accumulated = mergeContextSymptoms(existing.getContextSymptoms(), request.message());
            existing.setContextSymptoms(JsonUtils.toJson(accumulated));
            existing.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(existing);
            return existing;
        }
        AiChatSessionEntity session = new AiChatSessionEntity();
        session.setSessionNo(request.sessionId());
        session.setPatientId(patientId);
        session.setTitle(truncate(request.message(), 60));
        session.setStatus("ACTIVE");
        session.setContextSymptoms(JsonUtils.toJson(List.of(request.message())));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 把新消息追加到已有 contextSymptoms JSON 串，去重 + 限长（最多保留 20 条，防 JSON 膨胀）。
     * 解析失败时退化为只保留当前消息（不阻断主流程）。
     */
    @SuppressWarnings("unchecked")
    private List<String> mergeContextSymptoms(String existingJson, String newMessage) {
        if (existingJson == null || existingJson.isBlank()) {
            return List.of(newMessage);
        }
        try {
            List<String> existing = JsonUtils.MAPPER.readValue(existingJson,
                    new TypeReference<List<String>>() {});
            List<String> merged = new ArrayList<>(existing);
            if (newMessage != null && !newMessage.isBlank() && !merged.contains(newMessage)) {
                merged.add(newMessage);
            }
            // 保留最近 20 条，超出则丢弃最早的
            if (merged.size() > 20) {
                merged = new ArrayList<>(merged.subList(merged.size() - 20, merged.size()));
            }
            return merged;
        } catch (Exception e) {
            log.warn("解析 contextSymptoms 失败，退化为只保留当前消息: {}", existingJson, e);
            return List.of(newMessage);
        }
    }

    private VectorMatchDto toVectorMatch(DiseaseKnowledge knowledge) {
        return new VectorMatchDto(
                knowledge.vectorId(),
                knowledge.score(),
                knowledge.sourceId(),
                displayDiseaseName(knowledge),
                knowledge.fieldName(),
                truncate(bestSnippet(knowledge), 200)
        );
    }

    private List<CitationDto> toCitations(List<DiseaseKnowledge> knowledge) {
        return knowledge.stream()
                .map(item -> new CitationDto(
                        item.sourceId(),
                        displayDiseaseName(item),
                        matchedFields(item),
                        truncate(bestSnippet(item), 240),
                        item.score()
                ))
                .toList();
    }

    private List<String> matchedFields(DiseaseKnowledge item) {
        List<String> fields = new ArrayList<>();
        fields.add(item.fieldName() == null || item.fieldName().isBlank() ? "text" : item.fieldName());
        for (String field : KnowledgeFields.STANDARD_METADATA_FIELDS) {
            if (item.metadata().containsKey(field)) {
                fields.add(field);
            }
        }
        return fields.stream().distinct().toList();
    }

    private List<String> extractDepartments(List<DiseaseKnowledge> knowledge, RiskAssessment risk) {
        // 过滤无医生的科室：避免推荐用户挂号时找不到医生的空科室（如全科医学科）。
        Set<String> deptNosWithDoctors = departmentNosWithDoctors();
        LinkedHashSet<String> departments = new LinkedHashSet<>();
        if (risk.emergencyAdvice() && deptNosWithDoctors.contains("DEP_EMERGENCY")) {
            departments.add("急诊科");
        }
        for (DiseaseKnowledge item : knowledge) {
            Object value = item.metadata().get("cure_department");
            List<String> rawDepts;
            if (value instanceof List<?> list) {
                rawDepts = list.stream().map(Objects::toString).toList();
            } else if (value != null) {
                rawDepts = List.of(value.toString());
            } else {
                rawDepts = List.of();
            }
            for (String dept : rawDepts) {
                if (dept.isBlank()) {
                    continue;
                }
                // 仅保留有医生的科室
                if (deptNosWithDoctors.contains(departmentIdOf(dept))) {
                    departments.add(dept);
                }
            }
        }
        return departments.stream().filter(item -> !item.isBlank()).toList();
    }

    /** 科室中文名 → 科室编号（与 TriageService.departmentIdOf 对齐） */
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

    /**
     * 查询有启用医生的科室编号集合（带 5 分钟 Redis 缓存，与 TriageService 共用同一 key）。
     * <p>调用失败时降级为空集合（不过滤），保持原行为。
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

    private static String truncate(String value, int length) {
        if (value == null) {
            return "";
        }
        return value.length() <= length ? value : value.substring(0, length);
    }

    private String displayDiseaseName(DiseaseKnowledge knowledge) {
        if (knowledge.diseaseName() != null && !knowledge.diseaseName().isBlank()) {
            return knowledge.diseaseName();
        }
        String name = extractBetween(knowledge.chunkText(), "疾病名称：", "\n");
        return name;
    }

    private String formatKnowledgeSummary(DiseaseKnowledge knowledge) {
        String name = displayDiseaseName(knowledge);
        String desc = firstNonBlank(knowledge.desc(), extractBetween(knowledge.chunkText(), "疾病描述：", ""));
        if (desc.isBlank()) {
            return name;
        }
        return name + "：" + truncate(desc.replaceAll("\\s+", ""), 120);
    }

    private String bestSnippet(DiseaseKnowledge knowledge) {
        return firstNonBlank(knowledge.chunkText(), knowledge.desc(), String.join("、", knowledge.symptoms()));
    }

    private static String extractBetween(String value, String startMarker, String endMarker) {
        String text = value == null ? "" : value;
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        int contentStart = start + startMarker.length();
        if (endMarker == null || endMarker.isEmpty()) {
            return text.substring(contentStart).trim();
        }
        int end = text.indexOf(endMarker, contentStart);
        return (end < 0 ? text.substring(contentStart) : text.substring(contentStart, end)).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}
