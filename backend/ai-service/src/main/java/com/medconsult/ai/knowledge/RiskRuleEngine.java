package com.medconsult.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.persistence.entity.SymptomRuleEntity;
import com.medconsult.ai.persistence.mapper.SymptomRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 症状风险规则引擎（《修改建议》§3.1）。
 *
 * <p><b>2026-07-17 接库改造</b>：原硬编码 CRITICAL_TERMS/MEDIUM_TERMS 改为查 symptom_rule 表
 * （按 category='CRITICAL'/'MEDIUM' 分组），<b>常量保留作 fallback</b>——库为空或查询异常时
 * 退化为常量匹配，行为与改造前<b>完全等价</b>（铁律：库空时零行为漂移）。
 *
 * <p><b>保留不变的逻辑</b>（接库不影响）：
 * <ul>
 *   <li>CRITICAL 优先短路：CRITICAL 命中即返回 HIGH，不再查 MEDIUM</li>
 *   <li>reasons 文案："命中高危症状：" + term / "命中需尽快评估的症状：" + term</li>
 *   <li>既往病史规则（高血压/冠心病）：依赖 context，只在 MEDIUM 分支生效，<b>保留代码内</b>
 *       （病史语义不适合塞 symptom_rule，且 TriageService 永远传 null context 不触发）</li>
 * </ul>
 *
 * <p><b>未接入的两表</b>（本轮只建 Entity/Mapper）：negative_rule（否定词剔除）、
 * high_risk_symptom_rule（症状组合判定）——当前 assess 无此两类逻辑，强行接入会改变行为。
 *
 * <p>Spring 管理（@Component）+ 构造注入 Mapper；另保留无参构造兼容纯单元测试
 * （mapper=null 时走 fallback，与改造前行为一致）。
 */
@Slf4j
@Component
public class RiskRuleEngine {

    /** Fallback 常量（库为空/异常时用，与改造前完全一致） */
    private static final List<String> CRITICAL_TERMS = List.of(
            "持续胸痛", "呼吸困难", "意识障碍", "昏厥", "大出血", "抽搐", "咯血", "剧烈头痛"
    );
    private static final List<String> MEDIUM_TERMS = List.of(
            "胸闷", "心悸", "高血压", "发热", "喘息", "呕吐", "腹痛"
    );

    private final SymptomRuleMapper symptomRuleMapper;

    /** Spring 构造注入（@Component 默认构造注入） */
    public RiskRuleEngine(SymptomRuleMapper symptomRuleMapper) {
        this.symptomRuleMapper = symptomRuleMapper;
    }

    /**
     * 无参构造：兼容纯单元测试（KnowledgeComponentTest）与无 Spring 场景，
     * mapper=null 时永远走 fallback 常量，与改造前行为等价。
     */
    public RiskRuleEngine() {
        this.symptomRuleMapper = null;
    }

    public RiskAssessment assess(String message, PatientContext context) {
        String text = Objects.toString(message, "");
        List<String> reasons = new ArrayList<>();

        // 查库得 CRITICAL/MEDIUM 词条；库空/异常 → fallback 常量（行为等价）
        LoadedTerms terms = loadTerms();

        for (String term : terms.critical) {
            if (text.contains(term)) {
                reasons.add("命中高危症状：" + term);
            }
        }
        if (!reasons.isEmpty()) {
            // CRITICAL 优先短路：直接返回 HIGH
            return new RiskAssessment("HIGH", true, reasons);
        }
        for (String term : terms.medium) {
            if (text.contains(term)) {
                reasons.add("命中需尽快评估的症状：" + term);
            }
        }
        // 既往病史规则（保留代码内，依赖 context）
        if (context != null && context.pastMedicalHistory() != null
                && context.pastMedicalHistory().stream().anyMatch(item -> item.contains("高血压") || item.contains("冠心病"))) {
            reasons.add("合并心血管相关既往病史");
        }
        if (!reasons.isEmpty()) {
            return new RiskAssessment("MEDIUM", false, reasons);
        }
        return new RiskAssessment("LOW", false, List.of());
    }

    /**
     * 查 symptom_rule 表得 CRITICAL/MEDIUM 词条。
     * <p>库为空 / mapper 不可用 / 查询异常 → 返回 fallback 常量（铁律：零行为漂移）。
     * <p>按 id 升序保证词条顺序稳定（reasons 顺序可复现）。
     */
    private LoadedTerms loadTerms() {
        if (symptomRuleMapper == null) {
            return new LoadedTerms(CRITICAL_TERMS, MEDIUM_TERMS);
        }
        try {
            List<SymptomRuleEntity> rules = symptomRuleMapper.selectList(
                    new LambdaQueryWrapper<SymptomRuleEntity>()
                            .eq(SymptomRuleEntity::getEnabled, 1)
                            .orderByAsc(SymptomRuleEntity::getId));
            if (rules == null || rules.isEmpty()) {
                // 库空 → fallback
                return new LoadedTerms(CRITICAL_TERMS, MEDIUM_TERMS);
            }
            List<String> critical = rules.stream()
                    .filter(r -> "CRITICAL".equalsIgnoreCase(r.getCategory()))
                    .map(SymptomRuleEntity::getKeyword)
                    .filter(k -> k != null && !k.isBlank())
                    .collect(Collectors.toList());
            List<String> medium = rules.stream()
                    .filter(r -> "MEDIUM".equalsIgnoreCase(r.getCategory()))
                    .map(SymptomRuleEntity::getKeyword)
                    .filter(k -> k != null && !k.isBlank())
                    .collect(Collectors.toList());
            // 两类都空（如 category 未配）→ fallback，避免漏判
            if (critical.isEmpty() && medium.isEmpty()) {
                return new LoadedTerms(CRITICAL_TERMS, MEDIUM_TERMS);
            }
            return new LoadedTerms(critical, medium);
        } catch (RuntimeException e) {
            // 查询异常（库不可用等）→ fallback，不阻断症状自诊主流程
            log.warn("查 symptom_rule 失败，退化为硬编码 fallback 常量: {}", e.toString());
            return new LoadedTerms(CRITICAL_TERMS, MEDIUM_TERMS);
        }
    }

    /** 查库结果载体 */
    private record LoadedTerms(List<String> critical, List<String> medium) {}
}
