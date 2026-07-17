package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 否定词剔除规则表（《修改建议》§3.1）。
 *
 * <p>{@code negativeWords} 存 JSON 数组字符串（如 {@code '["没有","无","不"]}）。
 *
 * <p><b>本轮只建 Entity/Mapper，assess 不查本表</b>——当前硬编码 RiskRuleEngine 无否定词
 * 逻辑，强行接入会改变行为，违反"库空时行为等价"铁律。留作未来增量。
 */
@TableName("negative_rule")
public class NegativeRuleEntity {
    @TableId
    private Long id;
    /** 否定词列表（JSON 数组字符串） */
    private String negativeWords;
    /** 匹配策略 */
    private String matchStrategy;
    /** 是否启用：0 否 1 是 */
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNegativeWords() { return negativeWords; }
    public void setNegativeWords(String negativeWords) { this.negativeWords = negativeWords; }
    public String getMatchStrategy() { return matchStrategy; }
    public void setMatchStrategy(String matchStrategy) { this.matchStrategy = matchStrategy; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
