package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_chat_message")
public class AiChatMessageEntity {
    @TableId
    private Long id;
    private Long sessionId;
    private Long patientId;
    private String userMessage;
    private String aiAnswer;
    private String citations;
    private String suggestedDepartments;
    private String riskLevel;
    private Integer emergencyAdvice;
    private String modelName;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAiAnswer() {
        return aiAnswer;
    }

    public void setAiAnswer(String aiAnswer) {
        this.aiAnswer = aiAnswer;
    }

    public String getCitations() {
        return citations;
    }

    public void setCitations(String citations) {
        this.citations = citations;
    }

    public String getSuggestedDepartments() {
        return suggestedDepartments;
    }

    public void setSuggestedDepartments(String suggestedDepartments) {
        this.suggestedDepartments = suggestedDepartments;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getEmergencyAdvice() {
        return emergencyAdvice;
    }

    public void setEmergencyAdvice(Integer emergencyAdvice) {
        this.emergencyAdvice = emergencyAdvice;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
