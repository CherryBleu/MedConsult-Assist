package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("negative_rule")
public class NegativeRuleEntity {
    @TableId
    private Long id;
    private String negativeWords;
    private String matchStrategy;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNegativeWords() {
        return negativeWords;
    }

    public void setNegativeWords(String negativeWords) {
        this.negativeWords = negativeWords;
    }

    public String getMatchStrategy() {
        return matchStrategy;
    }

    public void setMatchStrategy(String matchStrategy) {
        this.matchStrategy = matchStrategy;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
