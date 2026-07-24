package com.medconsult.medicalrecord.medicalrecord.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.mq.audit.AuditLog;
import com.medconsult.medicalrecord.medicalrecord.dto.MedicalRecordDTO;
import com.medconsult.medicalrecord.medicalrecord.entity.MedicalRecord;
import com.medconsult.medicalrecord.medicalrecord.mapper.MedicalRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transactional write body for medical records.
 *
 * <p>Feign resolution stays in {@link MedicalRecordServiceImpl}; this bean only performs local DB writes,
 * so @AuditLog can write local_message in the same proxied transactional path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordTxService {

    private final MedicalRecordMapper medicalRecordMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    @AuditLog(
            resourceType = "MEDICAL_RECORD",
            action = "CREATE",
            resourceId = "#result.recordId()",
            targetOwnerId = "#p1",
            detail = "'status=' + #result.status()")
    public MedicalRecordDTO.CreateResponse createInTx(MedicalRecordDTO.CreateRequest req,
                                                       Long patientId,
                                                       Long doctorId,
                                                       Long appointmentId) {
        MedicalRecord r = new MedicalRecord();
        r.setRecordNo(generateRecordNo());
        r.setPatientId(patientId);
        r.setDoctorId(doctorId);
        if (appointmentId != null) {
            r.setAppointmentId(appointmentId);
        }
        r.setChiefComplaint(req.getChiefComplaint());
        r.setPresentIllness(req.getPresentIllness());
        r.setPastHistory(req.getPastHistory());
        r.setPhysicalExam(req.getPhysicalExam());
        r.setInitialDiagnosis(toJsonArray(req.getInitialDiagnosis()));
        r.setDoctorAdvice(req.getDoctorAdvice());
        r.setPrescriptionsSnapshot(toJson(req.getPrescriptions()));
        r.setStatus("DRAFT");
        medicalRecordMapper.insert(r);
        log.info("病历创建: recordNo={} patientId={} doctorId={}",
                r.getRecordNo(), req.getPatientId(), req.getDoctorId());
        return new MedicalRecordDTO.CreateResponse(r.getRecordNo(), r.getStatus());
    }

    @Transactional
    @AuditLog(
            resourceType = "MEDICAL_RECORD",
            action = "UPDATE",
            resourceId = "#result.recordId()",
            targetOwnerId = "#p0.patientId",
            detail = "'draft reused'")
    public MedicalRecordDTO.CreateResponse updateExistingDraftInTx(MedicalRecord r,
                                                                    MedicalRecordDTO.CreateRequest req) {
        if (req.getChiefComplaint() != null) r.setChiefComplaint(req.getChiefComplaint());
        if (req.getPresentIllness() != null) r.setPresentIllness(req.getPresentIllness());
        if (req.getPastHistory() != null) r.setPastHistory(req.getPastHistory());
        if (req.getPhysicalExam() != null) r.setPhysicalExam(req.getPhysicalExam());
        if (req.getInitialDiagnosis() != null) r.setInitialDiagnosis(toJsonArray(req.getInitialDiagnosis()));
        if (req.getDoctorAdvice() != null) r.setDoctorAdvice(req.getDoctorAdvice());
        if (req.getPrescriptions() != null) r.setPrescriptionsSnapshot(toJson(req.getPrescriptions()));
        medicalRecordMapper.updateById(r);
        log.info("Existing draft reused: recordNo={}", r.getRecordNo());
        return new MedicalRecordDTO.CreateResponse(r.getRecordNo(), r.getStatus());
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("诊断序列化为 JSON 失败，原样存 null", e);
            return null;
        }
    }

    private String toJson(List<MedicalRecordDTO.DraftPrescriptionItem> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Draft prescription serialization failed, storing null", e);
            return null;
        }
    }

    private static String generateRecordNo() {
        long id = IdWorker.getId();
        return "MR" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
