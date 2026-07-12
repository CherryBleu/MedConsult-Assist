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
        PatientContext context = enrichPatientContext(request);
        List<DrugRiskInfoDTO> drugRiskInfos = queryDrugRiskInfos(request.prescriptions());
        List<Map<String, Object>> contraindications = new ArrayList<>();
        List<Map<String, Object>> interactions = new ArrayList<>();
        List<Map<String, Object>> reminders = new ArrayList<>();
        List<Map<String, Object>> functionTrace = new ArrayList<>();

        functionTrace.add(trace("queryPatientAllergies", summarizeList(context.allergies())));
        functionTrace.add(trace("queryCurrentMedications", summarizeList(context.currentMedications())));

        for (PrescriptionDto prescription : request.prescriptions()) {
            reminders.add(Map.of(
                    "drugName", prescription.drugName(),
                    "reminder", "Use according to the prescribed dosage and frequency. Contact a doctor if discomfort occurs."
            ));
            detectLocalContraindication(context, prescription, contraindications);
            applyDrugRiskInfo(prescription, drugRiskInfos, contraindications, interactions, functionTrace);
        }
        detectInlineInteractions(request, context, interactions);

        String overallRisk = contraindications.isEmpty() && interactions.isEmpty() ? "LOW" : "MEDIUM";
        return new FunctionResult(overallRisk, contraindications, interactions, reminders, functionTrace, context);
    }

    private PatientContext enrichPatientContext(MedicationAnalysisRequest request) {
        if (request.patientContext() != null) {
            return request.patientContext();
        }
        if (!StringUtils.hasText(request.patientId())) {
            return new PatientContext(null, null, List.of(), List.of(), List.of());
        }
        try {
            Result<PatientContextDTO> result = patientClient.context(BusinessIds.numericId(request.patientId()));
            if (result == null || result.data() == null) {
                return new PatientContext(null, null, List.of(), List.of(), List.of());
            }
            PatientContextDTO dto = result.data();
            return new PatientContext(dto.age(), dto.gender(), safe(dto.allergies()),
                    safe(dto.pastMedicalHistory()), safe(dto.currentMedications()));
        } catch (RuntimeException ex) {
            return new PatientContext(null, null, List.of(), List.of(), List.of());
        }
    }

    private List<DrugRiskInfoDTO> queryDrugRiskInfos(List<PrescriptionDto> prescriptions) {
        List<DrugRiskInfoDTO> result = new ArrayList<>();
        for (PrescriptionDto prescription : prescriptions) {
            if (!StringUtils.hasText(prescription.drugId())) {
                continue;
            }
            try {
                Result<DrugRiskInfoDTO> res = drugClient.getRiskInfo(BusinessIds.numericId(prescription.drugId()));
                if (res != null && res.data() != null) {
                    result.add(res.data());
                }
            } catch (RuntimeException ex) {
                // 用药安全场景：drug-service 不可用时静默降级为本地规则，必须留日志便于发现，
                // 否则用户拿不到真实禁忌/相互作用却无任何告警。
                log.warn("drug risk info fetch failed, degrade to local rules, drugId={}",
                        prescription.drugId(), ex);
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
                    "description", "Patients with gastric disease history should use NSAIDs cautiously.",
                    "suggestion", "Ask the pharmacist or doctor to reassess gastrointestinal bleeding risk."
            ));
        }
    }

    private static void detectInlineInteractions(MedicationAnalysisRequest request, PatientContext context,
                                                 List<Map<String, Object>> interactions) {
        String prescribed = request.prescriptions().stream().map(PrescriptionDto::drugName).reduce("", (a, b) -> a + " " + b);
        String current = context.currentMedications() == null ? "" : String.join(" ", context.currentMedications());
        if (containsAny(prescribed, "布洛芬", "ibuprofen") && containsAny(prescribed + " " + current, "阿司匹林", "aspirin")) {
            interactions.add(Map.of(
                    "drugA", "ibuprofen",
                    "drugB", "aspirin",
                    "riskLevel", "MEDIUM",
                    "description", "Combined use may increase gastrointestinal adverse reaction or bleeding risk."
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
            functionTrace.add(trace("queryDrugContraindications", info.drugId() + " contraindications=" + contraList.size()));
            functionTrace.add(trace("queryDrugInteractions", info.drugId() + " interactions=" + interList.size()));
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

    private static Map<String, Object> trace(String functionName, String resultSummary) {
        return Map.of("functionName", functionName, "resultSummary", resultSummary);
    }

    private static String summarizeList(List<String> values) {
        return values == null || values.isEmpty() ? "empty" : String.join(",", values);
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
    private static boolean containsAny(String text, String... terms) {
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

    private static boolean containsLatin(String value) {
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
