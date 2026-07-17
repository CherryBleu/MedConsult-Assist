package com.medconsult.ai.knowledge;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.RiskAssessment;
import com.medconsult.ai.persistence.entity.SymptomRuleEntity;
import com.medconsult.ai.persistence.mapper.SymptomRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RiskRuleEngine 接库测试（《修改建议》§3.1）。
 *
 * <p>验证两条路径：
 * <ul>
 *   <li>无参构造（mapper=null）→ fallback 常量，行为与改造前等价</li>
 *   <li>注入 mapper → 查库命中，按 category 分组生效</li>
 * </ul>
 *
 * <p>不启 Spring（纯 Mockito），聚焦接库逻辑而非上下文装配。
 */
@ExtendWith(MockitoExtension.class)
class RiskRuleEngineTest {

    @Mock
    private SymptomRuleMapper symptomRuleMapper;

    @Test
    void 无参构造_走fallback常量_持续胸痛判HIGH() {
        // 兼容旧测试范式：new RiskRuleEngine() 无 Spring，mapper=null，走 fallback 常量
        RiskRuleEngine engine = new RiskRuleEngine();
        RiskAssessment a = engine.assess("持续胸痛并且呼吸困难", null);
        assertEquals("HIGH", a.riskLevel());
        assertTrue(a.emergencyAdvice());
        // reasons 文案保持一致
        assertTrue(a.reasons().contains("命中高危症状：持续胸痛"));
        assertTrue(a.reasons().contains("命中高危症状：呼吸困难"));
    }

    @Test
    void 无参构造_Medium症状判MEDIUM且既往病史叠加() {
        RiskRuleEngine engine = new RiskRuleEngine();
        com.medconsult.ai.dto.AiModels.PatientContext ctx = new com.medconsult.ai.dto.AiModels.PatientContext(
                60, "M", List.of(), List.of("高血压病史"), List.of());
        RiskAssessment a = engine.assess("最近有点胸闷", ctx);
        assertEquals("MEDIUM", a.riskLevel());
        assertFalse(a.emergencyAdvice());
        assertTrue(a.reasons().contains("命中需尽快评估的症状：胸闷"));
        assertTrue(a.reasons().contains("合并心血管相关既往病史"),
                "既往病史规则应保留（MEDIUM 分支）");
    }

    @Test
    void 无参构造_不命中判LOW() {
        RiskRuleEngine engine = new RiskRuleEngine();
        RiskAssessment a = engine.assess("我就是想咨询一下", null);
        assertEquals("LOW", a.riskLevel());
        assertTrue(a.reasons().isEmpty());
    }

    @Test
    void 查库为空_仍走fallback() {
        // 库空（新部署未种数据）→ 必须走 fallback，行为与改造前等价（关键：零漂移）
        when(symptomRuleMapper.selectList(any())).thenReturn(List.of());
        RiskRuleEngine engine = new RiskRuleEngine(symptomRuleMapper);
        RiskAssessment a = engine.assess("剧烈头痛", null);
        assertEquals("HIGH", a.riskLevel());
        assertTrue(a.emergencyAdvice());
    }

    @Test
    void 查库命中CRITICAL_短路返回HIGH() {
        // 库有 CRITICAL 词条（如自定义"突发偏瘫"），命中即 HIGH
        SymptomRuleEntity critical = rule(10L, "突发偏瘫", "偏瘫", "CRITICAL");
        SymptomRuleEntity medium = rule(20L, "头晕", "头晕", "MEDIUM");
        when(symptomRuleMapper.selectList(any())).thenReturn(List.of(critical, medium));

        RiskRuleEngine engine = new RiskRuleEngine(symptomRuleMapper);
        RiskAssessment a = engine.assess("老人突发偏瘫", null);
        assertEquals("HIGH", a.riskLevel());
        assertTrue(a.emergencyAdvice());
        // CRITICAL 短路：不查 MEDIUM，reasons 只含 CRITICAL 命中
        assertTrue(a.reasons().contains("命中高危症状：突发偏瘫"));
        assertFalse(a.reasons().stream().anyMatch(r -> r.contains("头晕")));
    }

    @Test
    void 查库命中MEDIUM_判MEDIUM() {
        SymptomRuleEntity medium = rule(20L, "头晕", "头晕", "MEDIUM");
        when(symptomRuleMapper.selectList(any())).thenReturn(List.of(medium));

        RiskRuleEngine engine = new RiskRuleEngine(symptomRuleMapper);
        RiskAssessment a = engine.assess("最近总头晕", null);
        assertEquals("MEDIUM", a.riskLevel());
        assertFalse(a.emergencyAdvice());
        assertTrue(a.reasons().contains("命中需尽快评估的症状：头晕"));
    }

    @Test
    void 查库异常_降级fallback不阻断() {
        // 库不可用（如连接异常）→ catch 后走 fallback，绝不抛异常阻断症状自诊
        when(symptomRuleMapper.selectList(any())).thenThrow(new RuntimeException("DB down"));
        RiskRuleEngine engine = new RiskRuleEngine(symptomRuleMapper);
        RiskAssessment a = engine.assess("呼吸困难", null);
        assertEquals("HIGH", a.riskLevel());
        assertTrue(a.emergencyAdvice());
    }

    private SymptomRuleEntity rule(long id, String keyword, String standard, String category) {
        SymptomRuleEntity e = new SymptomRuleEntity();
        e.setId(id);
        e.setKeyword(keyword);
        e.setStandardSymptom(standard);
        e.setCategory(category);
        e.setEnabled(1);
        return e;
    }
}
