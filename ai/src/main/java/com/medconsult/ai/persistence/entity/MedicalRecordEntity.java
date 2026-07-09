package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("medical_record")
public class MedicalRecordEntity {
    @TableId
    private Long id;
    private String recordNo;
    private Long patientId;
    private Long doctorId;
    private Long appointmentId;
    private String chiefComplaint;
    private String presentIllness;
    private String pastHistory;
    private String physicalExam;
    private String initialDiagnosis;
    private String finalDiagnosis;
    private String prescriptions;
    private String doctorAdvice;
    private String status;

    public Long getId() {
        return id;
    }

    public String getRecordNo() {
        return recordNo;
    }

    public Long getPatientId() {
        return patientId;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public String getPresentIllness() {
        return presentIllness;
    }

    public String getPastHistory() {
        return pastHistory;
    }

    public String getPhysicalExam() {
        return physicalExam;
    }

    public String getInitialDiagnosis() {
        return initialDiagnosis;
    }

    public String getFinalDiagnosis() {
        return finalDiagnosis;
    }

    public String getPrescriptions() {
        return prescriptions;
    }

    public String getDoctorAdvice() {
        return doctorAdvice;
    }

    public String getStatus() {
        return status;
    }
}
