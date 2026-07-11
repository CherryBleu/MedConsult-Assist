package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.client.OpenAiCompatibleClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SymptomChatService {
    private static final Logger log = LoggerFactory.getLogger(SymptomChatService.class);

    private static final String FINAL_ANSWER_PROMPT = """
            你是严谨的医疗健康问答助手。你只能使用用户输入、患者上下文、系统风险规则和最多三个疾病 JSON RAG 命中片段生成单一、连贯的回复。
            必须遵守：
            1. 不作确定诊断；
            2. 不新增命中知识之外的疾病知识；
            3. 明确包含“非诊断，仅供参考，不能替代医生诊断”；
            4. 如 emergencyAdvice 为 true，优先建议急诊；
            5. 行动建议优先：第一段先回答“接下来该怎么办”，明确是在家观察、尽快门诊、还是立即急诊；
            6. 第二段再用简洁语言综合相关条目，不要罗列一堆病名吓唬用户；
            7. 第三段说明建议就诊科室、可与医生沟通的检查方向和需要立刻就医的警示信号；
            8. 语气自然、简洁，不输出 Markdown 表格。
            
            """;

    private final DiseaseSearchService diseaseSearchService;
    private final OpenAiCompatibleClient llmClient;
    private final AiProperties properties;
    private final AiChatSessionMapper chatSessionMapper;
    private final AiChatMessageMapper chatMessageMapper;
    private final AiCallLogService callLogService;
    private final RiskRuleEngine riskRuleEngine = new RiskRuleEngine();

    public SymptomChatService(
            DiseaseSearchService diseaseSearchService,
            OpenAiCompatibleClient llmClient,
            AiProperties properties,
            AiChatSessionMapper chatSessionMapper,
            AiChatMessageMapper chatMessageMapper,
            AiCallLogService callLogService
    ) {
        this.diseaseSearchService = diseaseSearchService;
        this.llmClient = llmClient;
        this.properties = properties;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.callLogService = callLogService;
    }

    /**
     * 创建问诊会话（对齐前端 POST /ai/symptom-chat/session）。
     * 返回新会话编号，前端拿到后用于后续 sendChatMessage。
     */
    @Transactional
    public SessionCreated createSession(String patientId) {
        String sessionNo = BusinessIds.next("CHAT");
        AiChatSessionEntity session = new AiChatSessionEntity();
        session.setSessionNo(sessionNo);
        session.setPatientId(BusinessIds.numericId(patientId));
        session.setTitle("智能问诊会话");
        session.setStatus("ACTIVE");
        session.setContextSymptoms(JsonUtils.toJson(List.of()));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return new SessionCreated(sessionNo, session.getTitle(), session.getStatus());
    }

    /**
     * 获取会话历史消息（对齐前端 GET /ai/symptom-chat/history/{sessionId}）。
     */
    public List<ChatHistoryItem> getHistory(String sessionId) {
        // sessionId 入参是业务编号 sessionNo，先查 sessions 表拿主键 id
        AiChatSessionEntity session = chatSessionMapper.selectOne(new LambdaQueryWrapper<AiChatSessionEntity>()
                .eq(AiChatSessionEntity::getSessionNo, sessionId)
                .last("limit 1"));
        if (session == null) {
            return List.of();
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
        long started = System.currentTimeMillis();
        try {
            long stepStarted = System.currentTimeMillis();
            DiseaseIntent intent = diseaseSearchService.extractIntent(request.message());
            log.info("[AI-TIMER] symptomChat.extractIntent={}ms", elapsed(stepStarted));

            stepStarted = System.currentTimeMillis();
            int topK = request.ragOptions() == null ? 5 : request.ragOptions().safeTopK();
            List<DiseaseKnowledge> knowledge = diseaseSearchService.search(request.message(), intent, topK);
            log.info("[AI-TIMER] symptomChat.search={}ms knowledge={}", elapsed(stepStarted), knowledge.size());

            stepStarted = System.currentTimeMillis();
            RiskAssessment risk = riskRuleEngine.assess(request.message(), request.patientContext());
            List<CitationDto> citations = toCitations(knowledge);
            List<String> departments = extractDepartments(knowledge, risk);
            List<String> causes = knowledge.stream().map(this::displayDiseaseName).filter(item -> !item.isBlank()).distinct().toList();
            log.info("[AI-TIMER] symptomChat.prepare={}ms citations={} departments={} causes={}",
                    elapsed(stepStarted), citations.size(), departments.size(), causes.size());

            stepStarted = System.currentTimeMillis();
            String answer = generateFinalAnswer(request, knowledge, citations, departments, risk);
            log.info("[AI-TIMER] symptomChat.finalAnswer={}ms", elapsed(stepStarted));

            stepStarted = System.currentTimeMillis();
            AiChatSessionEntity session = ensureSession(request);
            AiChatMessageEntity message = new AiChatMessageEntity();
            message.setSessionId(session.getId());
            message.setPatientId(BusinessIds.numericId(request.patientId()));
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
                    "RAG_WITH_FINAL_LLM",
                    causes,
                    departments,
                    risk.riskLevel(),
                    risk.emergencyAdvice(),
                    knowledge.stream().map(this::toVectorMatch).toList(),
                    citations
            );
            callLogService.success("SYMPTOM_CHAT", request.patientId(), request.sessionId(), properties.llm().model(),
                    request.message(), answer, risk.riskLevel(), System.currentTimeMillis() - started);
            log.info("[AI-TIMER] symptomChat.callLog={}ms", elapsed(stepStarted));
            log.info("[AI-TIMER] symptomChat.total={}ms", elapsed(started));
            return response;
        } catch (RuntimeException ex) {
            callLogService.failed("SYMPTOM_CHAT", request.patientId(), request.sessionId(), properties.llm().model(),
                    request.message(), System.currentTimeMillis() - started, ex);
            log.info("[AI-TIMER] symptomChat.total={}ms success=false error={}",
                    elapsed(started), ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String generateFinalAnswer(SymptomChatRequest request, List<DiseaseKnowledge> knowledge,
                                       List<CitationDto> citations, List<String> departments, RiskAssessment risk) {
        long started = System.currentTimeMillis();
        if (knowledge.isEmpty()) {
            log.info("[AI-TIMER] symptomChat.generateFinalAnswer={}ms mode=rule_empty_knowledge", elapsed(started));
            return "目前疾病 JSON 向量检索未获得足够依据，无法给出可靠的可能原因。若症状持续、加重或出现胸痛、呼吸困难、意识异常等情况，请及时就医。非诊断，仅供参考，不能替代医生诊断。";
        }
        Map<String, Object> payload = Map.of(
                "userMessage", request.message(),
                "patientContext", request.patientContext() == null ? Map.of() : request.patientContext(),
                "risk", risk,
                "suggestedDepartments", departments,
                "ragKnowledge", knowledge.stream().limit(3).toList(),
                "citations", citations
        );
        String llmAnswer = llmClient.chatText(FINAL_ANSWER_PROMPT, JsonUtils.toJson(payload))
                .filter(answer -> answer.contains("仅供参考") || answer.contains("不能替代医生诊断"))
                .orElse(null);
        if (llmAnswer != null) {
            log.info("[AI-TIMER] symptomChat.generateFinalAnswer={}ms mode=llm", elapsed(started));
            return llmAnswer;
        }
        String fallbackAnswer = ruleBasedAnswer(knowledge, departments, risk);
        log.info("[AI-TIMER] symptomChat.generateFinalAnswer={}ms mode=rule_fallback", elapsed(started));
        return fallbackAnswer;
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

    private String actionAdvice(RiskAssessment risk) {
        if (risk.emergencyAdvice()) {
            return "下一步建议：你的描述包含高危症状，建议立即到急诊或呼叫急救，由医生尽快评估。";
        }
        if ("MEDIUM".equalsIgnoreCase(risk.riskLevel())) {
            return "下一步建议：建议尽快到门诊就医评估，症状加重或出现胸痛、呼吸困难、咯血、昏厥时立即急诊。";
        }
        return "下一步建议：若症状轻微且未加重，可短期观察；如持续不缓解、反复出现或影响日常活动，建议门诊评估。";
    }

    private AiChatSessionEntity ensureSession(SymptomChatRequest request) {
        AiChatSessionEntity existing = chatSessionMapper.selectOne(new LambdaQueryWrapper<AiChatSessionEntity>()
                .eq(AiChatSessionEntity::getSessionNo, request.sessionId())
                .last("limit 1"));
        if (existing != null) {
            existing.setContextSymptoms(JsonUtils.toJson(List.of(request.message())));
            existing.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(existing);
            return existing;
        }
        AiChatSessionEntity session = new AiChatSessionEntity();
        session.setSessionNo(request.sessionId());
        session.setPatientId(BusinessIds.numericId(request.patientId()));
        session.setTitle(truncate(request.message(), 60));
        session.setStatus("ACTIVE");
        session.setContextSymptoms(JsonUtils.toJson(List.of(request.message())));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
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
        LinkedHashSet<String> departments = new LinkedHashSet<>();
        if (risk.emergencyAdvice()) {
            departments.add("急诊科");
        }
        for (DiseaseKnowledge item : knowledge) {
            Object value = item.metadata().get("cure_department");
            if (value instanceof List<?> list) {
                list.stream().map(Objects::toString).forEach(departments::add);
            } else if (value != null) {
                departments.add(value.toString());
            }
        }
        return departments.stream().filter(item -> !item.isBlank()).toList();
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
