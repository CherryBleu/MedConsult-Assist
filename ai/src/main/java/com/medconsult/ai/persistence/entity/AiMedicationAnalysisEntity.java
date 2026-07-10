package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_medication_analysis")
public class AiMedicationAnalysisEntity {
    @TableId
    private Long id;
    private String analysisNo;
    private Long patientId;
    private Long recordId;
    private Long prescriptionId;
    private String prescriptions;
    private String overallRiskLevel;
    private String allergyRisks;
    private String contraindicationRisks;
    private String interactionRisks;
    private String reminders;
    private String functionTrace;
    private String modelName;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAnalysisNo() {
        return analysisNo;
    }

    public void setAnalysisNo(String analysisNo) {
        this.analysisNo = analysisNo;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getPrescriptionId() {
        return prescriptionId;
    }

    public void setPrescriptionId(Long prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    public String getPrescriptions() {
        return prescriptions;
    }

    public void setPrescriptions(String prescriptions) {
        this.prescriptions = prescriptions;
    }

    public String getOverallRiskLevel() {
        return overallRiskLevel;
    }

    public void setOverallRiskLevel(String overallRiskLevel) {
        this.overallRiskLevel = overallRiskLevel;
    }

    public String getAllergyRisks() {
        return allergyRisks;
    }

    public void setAllergyRisks(String allergyRisks) {
        this.allergyRisks = allergyRisks;
    }

    public String getContraindicationRisks() {
        return contraindicationRisks;
    }

    public void setContraindicationRisks(String contraindicationRisks) {
        this.contraindicationRisks = contraindicationRisks;
    }

    public String getInteractionRisks() {
        return interactionRisks;
    }

    public void setInteractionRisks(String interactionRisks) {
        this.interactionRisks = interactionRisks;
    }

    public String getReminders() {
        return reminders;
    }

    public void setReminders(String reminders) {
        this.reminders = reminders;
    }

    public String getFunctionTrace() {
        return functionTrace;
    }

    public void setFunctionTrace(String functionTrace) {
        this.functionTrace = functionTrace;
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
