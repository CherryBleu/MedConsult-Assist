package com.medconsult.ai.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.ai.persistence.entity.SymptomRuleEntity;
import com.medconsult.ai.persistence.mapper.SymptomRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 症状规则表种子初始化器（《修改建议》§3.1，P0 症状规则表接库）。
 *
 * <p><b>存在原因</b>：schema-ai.sql 在 db/ 子目录、不被 Spring 自动加载，纯 SQL 种子需手动跑。
 * 用 Java DataSeeder 启动时检查 symptom_rule 为空则补种，幂等可重入，与 auth-service DataSeeder 一致。
 *
 * <p><b>种子来源</b>：原 RiskRuleEngine 硬编码的 CRITICAL_TERMS(8) + MEDIUM_TERMS(7) = 15 条，
 * 一对一转为 symptom_rule 行（category 承载风险等级区分）。种入后 RiskRuleEngine 查库命中，
 * fallback 常量仅在库空/异常时启用（零行为漂移）。
 *
 * <p><b>幂等</b>：symptom_rule 为空才种（COUNT=0 门控），已有数据跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(20)  // 晚于业务配置初始化
public class SymptomRuleDataSeeder implements CommandLineRunner {

    private final SymptomRuleMapper symptomRuleMapper;

    @Override
    public void run(String... args) {
        long existing = symptomRuleMapper.selectCount(new QueryWrapper<>());
        if (existing > 0) {
            log.info("[SymptomRuleDataSeeder] symptom_rule 已有 {} 条，跳过种子", existing);
            return;
        }
        log.info("[SymptomRuleDataSeeder] symptom_rule 为空，补种 15 条规则（8 CRITICAL + 7 MEDIUM）");
        LocalDateTime now = LocalDateTime.now();
        long id = 1;
        // 8 条 CRITICAL（原 CRITICAL_TERMS，顺序保持一致以便 reasons 可复现）
        for (String[] row : new String[][]{
                {"持续胸痛", "胸痛"}, {"呼吸困难", "呼吸困难"}, {"意识障碍", "意识障碍"}, {"昏厥", "晕厥"},
                {"大出血", "出血"}, {"抽搐", "抽搐"}, {"咯血", "咯血"}, {"剧烈头痛", "头痛"}
        }) {
            insert(id++, row[0], row[1], "CRITICAL", now);
        }
        // 7 条 MEDIUM（原 MEDIUM_TERMS）
        for (String[] row : new String[][]{
                {"胸闷", "胸闷"}, {"心悸", "心悸"}, {"高血压", "高血压"}, {"发热", "发热"},
                {"喘息", "喘息"}, {"呕吐", "呕吐"}, {"腹痛", "腹痛"}
        }) {
            insert(id++, row[0], row[1], "MEDIUM", now);
        }
        log.info("[SymptomRuleDataSeeder] 症状规则补种完成");
    }

    private void insert(long id, String keyword, String standardSymptom, String category, LocalDateTime now) {
        SymptomRuleEntity e = new SymptomRuleEntity();
        e.setId(id);
        e.setKeyword(keyword);
        e.setStandardSymptom(standardSymptom);
        e.setCategory(category);
        e.setEnabled(1);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        symptomRuleMapper.insert(e);
    }
}
