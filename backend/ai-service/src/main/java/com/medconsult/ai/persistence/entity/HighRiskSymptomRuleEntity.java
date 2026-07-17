package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 高危症状组合判定表（《修改建议》§3.1）。
 *
 * <p>{@code symptomCombo} 存 JSON 数组字符串（如 {@code '["持续胸痛","冷汗"]'}），
 * 沿用 ai-service 既有范式（JSON 字段直接存 String）。
 *
 * <p><b>本轮只建 Entity/Mapper，assess 不查本表</b>——当前硬编码 RiskRuleEngine 无症状组合
 * 规则逻辑，强行接入会改变行为，违反"库空时行为等价"铁律。留作未来增量。
 */
@TableName("high_risk_symptom_rule")
public class HighRiskSymptomRuleEntity {
    @TableId
    private Long id;
    /** 症状组合（JSON 数组字符串，如 '["持续胸痛","冷汗"]'） */
    private String symptomCombo;
    /** 风险等级：CRITICAL / HIGH */
    private String riskLevel;
    /** 建议（急诊建议等） */
    private String advice;
    /** 是否启用：0 否 1 是 */
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSymptomCombo() { return symptomCombo; }
    public void setSymptomCombo(String symptomCombo) { this.symptomCombo = symptomCombo; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
