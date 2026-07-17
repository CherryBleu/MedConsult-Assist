package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 症状规则表（口语词→标准词映射，《修改建议》§3.1）。
 *
 * <p>不继承 BaseEntity：三表是配置/规则表，无 deleted 逻辑删除、无 BaseEntity 元数据
 * （对齐 ai-service 既有 Entity 范式如 AiCallLogEntity）。
 *
 * <p>{@code category} 承载风险等级区分：CRITICAL / MEDIUM（schema 无独立 risk_level 列）。
 * RiskRuleEngine 接库后按 category 过滤复刻原 CRITICAL 优先短路逻辑。
 */
@TableName("symptom_rule")
public class SymptomRuleEntity {
    @TableId
    private Long id;
    /** 匹配关键词（患者口语/医学术语，对应原硬编码 CRITICAL_TERMS/MEDIUM_TERMS 词条） */
    private String keyword;
    /** 标准症状名 */
    private String standardSymptom;
    /** 分类：CRITICAL / MEDIUM（承载风险等级区分） */
    private String category;
    /** 是否启用：0 否 1 是 */
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStandardSymptom() { return standardSymptom; }
    public void setStandardSymptom(String standardSymptom) { this.standardSymptom = standardSymptom; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
