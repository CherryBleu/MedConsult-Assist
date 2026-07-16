package com.medconsult.ai.service;

import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DrugFeignClient;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import com.medconsult.common.feign.dto.PatientContextDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用药分析 Function Calling 服务（架构文档 §6.2）。
 *
 * <p>改用 common-feign 的 {@link PatientFeignClient} / {@link DrugFeignClient}（返回 {@code Result<T>}，
 * 服务名无 medconsult- 前缀，由 AuthRelayInterceptor 透传服务身份），替代 ai-stack 的本地 Feign 客户端。
 */
@Service
public class MedicationFunctionService {
    private static final Logger log = LoggerFactory.getLogger(MedicationFunctionService.class);

    private final PatientFeignClient patientClient;
    private final DrugFeignClient drugClient;

    public MedicationFunctionService(PatientFeignClient patientClient, DrugFeignClient drugClient) {
        this.patientClient = patientClient;
        this.drugClient = drugClient;
    }

    public FunctionResult execute(MedicationAnalysisRequest request) {
        List<Map<String, Object>> functionTrace = new ArrayList<>();
        PatientContext context = enrichPatientContext(request, functionTrace);
        List<DrugRiskInfoDTO> drugRiskInfos = queryDrugRiskInfos(request.prescriptions(), functionTrace);
        List<Map<String, Object>> contraindications = new ArrayList<>();
        List<Map<String, Object>> interactions = new ArrayList<>();
        List<Map<String, Object>> reminders = new ArrayList<>();

        for (PrescriptionDto prescription : request.prescriptions()) {
            // 前端在无真实处方数据时会硬编码 {drugName:'placeholder'} 占位以满足 @NotEmpty 校验，
            // 占位处方不应产生用药提醒，否则会向用户显示无意义的 placeholder 药名提醒。
            if (isPlaceholderDrugName(prescription.drugName())) {
                continue;
            }
            reminders.add(Map.of(
                    "drugName", prescription.drugName(),
                    "reminder", "请按医嘱规定的剂量和频次服用；如出现不适请及时就诊或咨询药师。"
            ));
            detectLocalContraindication(context, prescription, contraindications);
            applyDrugRiskInfo(prescription, drugRiskInfos, contraindications, interactions, functionTrace);
        }
        detectInlineInteractions(request, context, interactions);

        String overallRisk = contraindications.isEmpty() && interactions.isEmpty() ? "LOW" : "MEDIUM";
        return new FunctionResult(overallRisk, contraindications, interactions, reminders, functionTrace, context);
    }

    private PatientContext enrichPatientContext(MedicationAnalysisRequest request, List<Map<String, Object>> functionTrace) {
        if (request.patientContext() != null) {
            PatientContext context = request.patientContext();
            functionTrace.add(toolTrace("queryPatientAllergies", "patientId=" + safeText(request.patientId()),
                    "SUCCESS", summarizeList(context.allergies()), null));
            functionTrace.add(toolTrace("queryCurrentMedications", "patientId=" + safeText(request.patientId()),
                    "SUCCESS", summarizeList(context.currentMedications()), null));
            return context;
        }
        if (!StringUtils.hasText(request.patientId())) {
            PatientContext empty = new PatientContext(null, null, List.of(), List.of(), List.of());
            functionTrace.add(toolTrace("queryPatientAllergies", "patientId=missing", "SKIPPED", "empty", null));
            functionTrace.add(toolTrace("queryCurrentMedications", "patientId=missing", "SKIPPED", "empty", null));
            return empty;
        }
        try {
            Result<PatientContextDTO> result = patientClient.context(BusinessIds.numericId(request.patientId()));
            if (result == null || result.data() == null) {
                PatientContext empty = new PatientContext(null, null, List.of(), List.of(), List.of());
                functionTrace.add(toolTrace("queryPatientAllergies", "patientId=" + request.patientId(), "FAILED", "empty", "EMPTY_RESPONSE"));
                functionTrace.add(toolTrace("queryCurrentMedications", "patientId=" + request.patientId(), "FAILED", "empty", "EMPTY_RESPONSE"));
                return empty;
            }
            PatientContextDTO dto = result.data();
            PatientContext context = new PatientContext(dto.age(), dto.gender(), safe(dto.allergies()),
                    safe(dto.pastMedicalHistory()), safe(dto.currentMedications()));
            functionTrace.add(toolTrace("queryPatientAllergies", "patientId=" + request.patientId(),
                    "SUCCESS", summarizeList(context.allergies()), null));
            functionTrace.add(toolTrace("queryCurrentMedications", "patientId=" + request.patientId(),
                    "SUCCESS", summarizeList(context.currentMedications()), null));
            return context;
        } catch (RuntimeException ex) {
            functionTrace.add(toolTrace("queryPatientAllergies", "patientId=" + request.patientId(), "FAILED", "empty", errorCode(ex)));
            functionTrace.add(toolTrace("queryCurrentMedications", "patientId=" + request.patientId(), "FAILED", "empty", errorCode(ex)));
            return new PatientContext(null, null, List.of(), List.of(), List.of());
        }
    }

    private List<DrugRiskInfoDTO> queryDrugRiskInfos(List<PrescriptionDto> prescriptions, List<Map<String, Object>> functionTrace) {
        List<DrugRiskInfoDTO> result = new ArrayList<>();
        Map<String, PrescriptionDto> unique = new LinkedHashMap<>();
        for (PrescriptionDto prescription : prescriptions) {
            if (!StringUtils.hasText(prescription.drugId()) || isPlaceholderDrugName(prescription.drugName())) {
                continue;
            }
            unique.putIfAbsent(prescription.drugId(), prescription);
        }
        for (PrescriptionDto prescription : unique.values()) {
            try {
                Result<DrugRiskInfoDTO> res = drugClient.getRiskInfo(BusinessIds.numericId(prescription.drugId()));
                if (res != null && res.data() != null) {
                    result.add(res.data());
                    functionTrace.add(toolTrace("queryDrugRiskInfo",
                            "drugId=" + prescription.drugId() + ",drugName=" + safeText(prescription.drugName()),
                            "SUCCESS", riskInfoSummary(res.data()), null));
                } else {
                    functionTrace.add(toolTrace("queryDrugRiskInfo",
                            "drugId=" + prescription.drugId() + ",drugName=" + safeText(prescription.drugName()),
                            "FAILED", "empty", "EMPTY_RESPONSE"));
                }
            } catch (RuntimeException ex) {
                // 用药安全场景：drug-service 不可用时静默降级为本地规则，必须留日志便于发现，
                // 否则用户拿不到真实禁忌/相互作用却无任何告警。
                log.warn("drug risk info fetch failed, degrade to local rules, drugId={}",
                        prescription.drugId(), ex);
                functionTrace.add(toolTrace("queryDrugRiskInfo",
                        "drugId=" + prescription.drugId() + ",drugName=" + safeText(prescription.drugName()),
                        "FAILED", "degraded to local rules", errorCode(ex)));
            }
        }
        return result;
    }

    private static void detectLocalContraindication(PatientContext context, PrescriptionDto prescription,
                                                    List<Map<String, Object>> contraindications) {
        String drugName = lower(prescription.drugName());
        boolean nsaid = containsAny(drugName, "ibuprofen", "aspirin", "布洛芬", "阿司匹林");
        boolean stomachHistory = context.pastMedicalHistory() != null
                && context.pastMedicalHistory().stream()
                .anyMatch(item -> containsAny(lower(item), "gastritis", "ulcer", "胃", "胃炎", "溃疡"));
        if (nsaid && stomachHistory) {
            contraindications.add(Map.of(
                    "drugName", prescription.drugName(),
                    "riskLevel", "MEDIUM",
                    "description", "有胃病既往史的患者应慎用 NSAIDs 类药物。",
                    "suggestion", "请药师或医生重新评估胃肠道出血风险。"
            ));
        }
    }

    private static void detectInlineInteractions(MedicationAnalysisRequest request, PatientContext context,
                                                 List<Map<String, Object>> interactions) {
        String prescribed = request.prescriptions().stream().map(PrescriptionDto::drugName).reduce("", (a, b) -> a + " " + b);
        String current = context.currentMedications() == null ? "" : String.join(" ", context.currentMedications());
        if (containsAny(prescribed, "布洛芬", "ibuprofen") && containsAny(prescribed + " " + current, "阿司匹林", "aspirin")) {
            interactions.add(Map.of(
                    "drugA", "布洛芬",
                    "drugB", "阿司匹林",
                    "riskLevel", "MEDIUM",
                    "description", "合用可能增加胃肠道不良反应或出血风险。"
            ));
        }
    }

    private static void applyDrugRiskInfo(PrescriptionDto prescription,
                                          List<DrugRiskInfoDTO> drugRiskInfos,
                                          List<Map<String, Object>> contraindications,
                                          List<Map<String, Object>> interactions,
                                          List<Map<String, Object>> functionTrace) {
        for (DrugRiskInfoDTO info : drugRiskInfos) {
            if (!matches(prescription, info)) {
                continue;
            }
            List<DrugRiskInfoDTO.Contraindication> contraList = info.contraindications() == null ? List.of() : info.contraindications();
            List<DrugRiskInfoDTO.Interaction> interList = info.interactions() == null ? List.of() : info.interactions();
            functionTrace.add(toolTrace("queryDrugContraindications", "drugId=" + info.drugId(),
                    "SUCCESS", "contraindications=" + contraList.size(), null));
            functionTrace.add(toolTrace("queryDrugInteractions", "drugId=" + info.drugId(),
                    "SUCCESS", "interactions=" + interList.size(), null));
            for (DrugRiskInfoDTO.Contraindication item : contraList) {
                Map<String, Object> risk = new LinkedHashMap<>();
                risk.put("condition", item.condition());
                risk.put("level", item.level());
                risk.put("note", item.note());
                risk.putIfAbsent("drugName", prescription.drugName());
                risk.putIfAbsent("riskLevel", "MEDIUM");
                contraindications.add(risk);
            }
            for (DrugRiskInfoDTO.Interaction item : interList) {
                Map<String, Object> risk = new LinkedHashMap<>();
                risk.put("drugCode", item.drugCode());
                risk.put("effect", item.effect());
                risk.put("level", item.level());
                risk.putIfAbsent("drugA", prescription.drugName());
                risk.putIfAbsent("riskLevel", "MEDIUM");
                interactions.add(risk);
            }
        }
    }

    private static boolean matches(PrescriptionDto prescription, DrugRiskInfoDTO info) {
        return (StringUtils.hasText(prescription.drugId()) && prescription.drugId().equals(String.valueOf(info.drugId())))
                || (StringUtils.hasText(info.genericName()) && prescription.drugName().contains(info.genericName()));
    }

    private static Map<String, Object> toolTrace(String toolName, String inputSummary, String status,
                                                String resultSummary, String errorCode) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("type", "tool_call");
        trace.put("toolName", toolName);
        trace.put("functionName", toolName); // 兼容旧前端/旧文档字段
        trace.put("inputSummary", inputSummary);
        trace.put("status", status);
        trace.put("resultSummary", resultSummary);
        if (StringUtils.hasText(errorCode)) {
            trace.put("errorCode", errorCode);
        }
        return trace;
    }

    private static String summarizeList(List<String> values) {
        return values == null || values.isEmpty() ? "empty" : String.join(",", values);
    }

    private static String riskInfoSummary(DrugRiskInfoDTO info) {
        int contraindications = info.contraindications() == null ? 0 : info.contraindications().size();
        int interactions = info.interactions() == null ? 0 : info.interactions().size();
        return "contraindications=" + contraindications + ",interactions=" + interactions;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static String errorCode(RuntimeException ex) {
        return ex.getClass().getSimpleName();
    }

    /**
     * 词边界感知的多关键词匹配。
     *
     * <p>避免子串误报：英文药名用 {@code \b} 词边界正则，"ibuprofen" 不再匹配
     * "ibuprofenspecial" 这类把药名直接拼进另一个单词的字符串（前后均为
     * 单词字符 [a-zA-Z0-9_] 时无边界，不匹配）。仍会匹配 "ibuprofen-er"、
     * "non-ibuprofen-fake" 这类以空格/连字符分隔的文本（这属于合理行为：
     * 缓释片与原药同效，分隔符本就是词边界）。中文无空格分词、药名多为
     * 完整词组（如"阿司匹林"），子串匹配误报概率低，仍走 {@code contains}。
     *
     * <p>判断逻辑：term 含拉丁字母（a-z/A-Z）走词边界正则；纯非拉丁走 contains。
     * 英文大小写不敏感由 {@link Pattern#CASE_INSENSITIVE} 保证；term 中的正则
     * 元字符通过 {@link Pattern#quote(String)} 转义，避免 "h2o" 之类被当成正则。
     */
    // package-private 以便单元测试直接覆盖词边界行为（无需 Spring 容器）
    static boolean containsAny(String text, String... terms) {
        if (text == null) {
            return false;
        }
        for (String term : terms) {
            if (term == null || term.isEmpty()) {
                continue;
            }
            if (containsLatin(term)) {
                // \b\Q<term>\E\b：\b 词边界 + 引用字面量，CASE_INSENSITIVE 兼容已转小写的 text
                Pattern p = Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
                if (p.matcher(text).find()) {
                    return true;
                }
            } else if (text.contains(lower(term))) {
                return true;
            }
        }
        return false;
    }

    static boolean containsLatin(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * 判断药名是否为前端无真实处方时硬编码的占位值。
     * <p>前端 MedicationAnalysis.vue / api/ai.js 在无处方数据时会传 {drugName:'placeholder'}
     * 满足后端 @NotEmpty 校验，这类占位处方不应产生用药提醒和风险分析。
     */
    private static boolean isPlaceholderDrugName(String drugName) {
        if (!StringUtils.hasText(drugName)) {
            return true;
        }
        String trimmed = drugName.trim().toLowerCase(Locale.ROOT);
        return trimmed.equals("placeholder") || trimmed.equals("占位") || trimmed.equals("test");
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }

    public record FunctionResult(
            String overallRiskLevel,
            List<Map<String, Object>> contraindicationRisks,
            List<Map<String, Object>> interactionRisks,
            List<Map<String, Object>> reminders,
            List<Map<String, Object>> functionTrace,
            PatientContext patientContext
    ) {
    }
}
