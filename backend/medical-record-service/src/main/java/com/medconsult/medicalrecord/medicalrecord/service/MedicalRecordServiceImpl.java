package com.medconsult.medicalrecord.medicalrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.AppointmentFeignClient;
import com.medconsult.common.feign.client.DoctorFeignClient;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.AppointmentOwnershipDTO;
import com.medconsult.common.feign.dto.DoctorProfileDTO;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.mq.audit.AuditLog;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.medicalrecord.medicalrecord.dto.MedicalRecordDTO;
import com.medconsult.medicalrecord.medicalrecord.entity.MedicalRecord;
import com.medconsult.medicalrecord.medicalrecord.mapper.MedicalRecordMapper;
import com.medconsult.medicalrecord.prescription.entity.Prescription;
import com.medconsult.medicalrecord.prescription.entity.PrescriptionItem;
import com.medconsult.medicalrecord.prescription.mapper.PrescriptionItemMapper;
import com.medconsult.medicalrecord.prescription.mapper.PrescriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordMapper medicalRecordMapper;
    private final ObjectMapper objectMapper;
    private final PatientFeignClient patientFeignClient;
    private final DoctorFeignClient doctorFeignClient;
    private final AppointmentFeignClient appointmentFeignClient;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final MedicalRecordTxService txService;

    @Override
    public MedicalRecordDTO.CreateResponse create(MedicalRecordDTO.CreateRequest req) {
        JwtPayload payload = SecurityContext.requireUser();
        if (isPatient(payload)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "患者无权创建病历");
        }
        Long patientId = resolvePatientId(req.getPatientId());
        Long requestedDoctorId = resolveDoctorId(req.getDoctorId());
        Long doctorId = enforceCreateScope(payload, requestedDoctorId);
        Long appointmentId = resolveAppointmentId(req.getAppointmentId(), patientId, doctorId);

        MedicalRecord existing = findExistingAppointmentRecord(appointmentId, patientId, doctorId);
        if (existing != null) {
            if ("DRAFT".equals(existing.getStatus())) {
                return txService.updateExistingDraftInTx(existing, req);
            }
            return new MedicalRecordDTO.CreateResponse(existing.getRecordNo(), existing.getStatus());
        }
        return txService.createInTx(req, patientId, doctorId, appointmentId);
    }

    private Long enforceCreateScope(JwtPayload payload, Long requestedDoctorId) {
        if (isDoctor(payload)) {
            Long selfDoctorId = payload.doctorId();
            if (selfDoctorId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未绑定医生档案，无法创建病历");
            }
            if (!selfDoctorId.equals(requestedDoctorId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权以非本人接诊医生创建病历");
            }
            return selfDoctorId;
        }
        return requestedDoctorId;
    }

    @Override
    public MedicalRecordDTO.DetailResponse detail(String recordNo) {
        MedicalRecord r = requireByNo(recordNo);
        enforceRecordAccess(r);
        if (isPatient(SecurityContext.getPayload()) && !"ARCHIVED".equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "病历不存在: " + recordNo);
        }
        String patientName = patientNamesByIds(List.of(r.getPatientId())).get(r.getPatientId());
        DoctorProfileDTO doctorProfile = doctorProfilesByIds(List.of(r.getDoctorId())).get(r.getDoctorId());
        return new MedicalRecordDTO.DetailResponse(
                r.getRecordNo(),
                String.valueOf(r.getPatientId()),
                patientName,
                String.valueOf(r.getDoctorId()),
                doctorProfile == null ? null : doctorProfile.doctorName(),
                doctorProfile == null ? null : doctorProfile.departmentName(),
                r.getChiefComplaint(),
                r.getPresentIllness(),
                r.getPastHistory(),
                r.getPhysicalExam(),
                fromJsonArray(r.getInitialDiagnosis()),
                fromJsonArray(r.getFinalDiagnosis()),
                r.getDoctorAdvice(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getArchivedAt(),
                loadPrescriptionItemsOrDraftSnapshot(r));
    }

    @Override
    public PageResult<MedicalRecordDTO.ListItem> list(int page, int pageSize, String patientId, String appointmentId) {
        Page<MedicalRecord> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<MedicalRecord> qw = new QueryWrapper<>();
        Long scopePatientId = resolvePatientScope(patientId);
        if (scopePatientId != null) {
            qw.eq("patient_id", scopePatientId);
        }
        Long scopeDoctorId = resolveDoctorScope();
        if (scopeDoctorId != null) {
            qw.eq("doctor_id", scopeDoctorId);
        }
        if (isPatient(SecurityContext.getPayload())) {
            qw.eq("status", "ARCHIVED");
        }
        Long scopeAppointmentId = resolveAppointmentFilter(appointmentId, scopePatientId, scopeDoctorId);
        if (scopeAppointmentId != null) {
            qw.eq("appointment_id", scopeAppointmentId);
        }
        qw.orderByDesc("created_at");
        IPage<MedicalRecord> result = medicalRecordMapper.selectPage(p, qw);
        Map<Long, String> patientNames = patientNamesByIds(result.getRecords().stream()
                .map(MedicalRecord::getPatientId)
                .toList());
        Map<Long, DoctorProfileDTO> doctorProfiles = doctorProfilesByIds(result.getRecords().stream()
                .map(MedicalRecord::getDoctorId)
                .toList());
        List<MedicalRecordDTO.ListItem> items = new ArrayList<>();
        for (MedicalRecord r : result.getRecords()) {
            DoctorProfileDTO doctorProfile = doctorProfiles.get(r.getDoctorId());
            items.add(new MedicalRecordDTO.ListItem(
                    r.getRecordNo(),
                    String.valueOf(r.getPatientId()),
                    patientNames.get(r.getPatientId()),
                    String.valueOf(r.getDoctorId()),
                    doctorProfile == null ? null : doctorProfile.doctorName(),
                    doctorProfile == null ? null : doctorProfile.departmentName(),
                    r.getChiefComplaint(),
                    fromJsonArray(r.getInitialDiagnosis()),
                    fromJsonArray(r.getFinalDiagnosis()),
                    r.getCreatedAt(),
                    r.getArchivedAt(),
                    r.getStatus()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    @Override
    @Transactional
    @AuditLog(resourceType = "MEDICAL_RECORD", action = "UPDATE", resourceId = "#result.recordId()", detail = "'draft updated'")
    public MedicalRecordDTO.UpdateResponse updateDraft(String recordNo, MedicalRecordDTO.UpdateDraftRequest req) {
        MedicalRecord r = requireByNo(recordNo);
        enforceRecordAccess(r);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿病历可修改，当前状态: " + r.getStatus());
        }
        if (req.getChiefComplaint() != null) r.setChiefComplaint(req.getChiefComplaint());
        if (req.getPresentIllness() != null) r.setPresentIllness(req.getPresentIllness());
        if (req.getPastHistory() != null) r.setPastHistory(req.getPastHistory());
        if (req.getPhysicalExam() != null) r.setPhysicalExam(req.getPhysicalExam());
        if (req.getInitialDiagnosis() != null) r.setInitialDiagnosis(toJsonArray(req.getInitialDiagnosis()));
        if (req.getFinalDiagnosis() != null) r.setFinalDiagnosis(toJsonArray(req.getFinalDiagnosis()));
        if (req.getDoctorAdvice() != null) r.setDoctorAdvice(req.getDoctorAdvice());
        if (req.getPrescriptions() != null) r.setPrescriptionsSnapshot(toJson(req.getPrescriptions()));
        medicalRecordMapper.updateById(r);
        return new MedicalRecordDTO.UpdateResponse(r.getRecordNo(), r.getUpdatedAt());
    }

    @Override
    @Transactional
    @AuditLog(resourceType = "MEDICAL_RECORD", action = "ARCHIVE", resourceId = "#result.recordId()", detail = "'status=' + #result.status()")
    public MedicalRecordDTO.ArchiveResponse archive(String recordNo, MedicalRecordDTO.ArchiveRequest req) {
        MedicalRecord r = requireByNo(recordNo);
        enforceRecordAccess(r);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿病历可归档，当前状态: " + r.getStatus());
        }
        r.setStatus("ARCHIVED");
        r.setArchivedAt(LocalDateTime.now());
        medicalRecordMapper.updateById(r);
        return new MedicalRecordDTO.ArchiveResponse(r.getRecordNo(), r.getStatus());
    }

    @Override
    public MedicalRecordDTO.FullRecordResponse getByIdFull(Long id) {
        MedicalRecord r = medicalRecordMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "病历不存在: " + id);
        }
        return new MedicalRecordDTO.FullRecordResponse(
                r.getRecordNo(),
                r.getPatientId(),
                r.getDoctorId(),
                r.getChiefComplaint(),
                r.getPresentIllness(),
                r.getPastHistory(),
                r.getPhysicalExam(),
                fromJsonArray(r.getInitialDiagnosis()),
                fromJsonArray(r.getFinalDiagnosis()),
                r.getDoctorAdvice(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getArchivedAt());
    }

    @Override
    public MedicalRecord requireByNo(String recordNo) {
        if (recordNo == null || recordNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "病历编号不能为空");
        }
        MedicalRecord r = medicalRecordMapper.selectOne(new QueryWrapper<MedicalRecord>().eq("record_no", recordNo));
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "病历不存在: " + recordNo);
        }
        return r;
    }

    private List<MedicalRecordDTO.PrescriptionItemResponse> loadPrescriptionItems(Long recordId) {
        List<Prescription> prescriptions = prescriptionMapper.selectList(
                new QueryWrapper<Prescription>().eq("record_id", recordId).orderByAsc("created_at"));
        if (prescriptions.isEmpty()) {
            return List.of();
        }
        List<MedicalRecordDTO.PrescriptionItemResponse> items = new ArrayList<>();
        for (Prescription prescription : prescriptions) {
            List<PrescriptionItem> prescriptionItems = prescriptionItemMapper.selectList(
                    new QueryWrapper<PrescriptionItem>().eq("prescription_id", prescription.getId()).orderByAsc("id"));
            for (PrescriptionItem item : prescriptionItems) {
                items.add(new MedicalRecordDTO.PrescriptionItemResponse(
                        prescription.getPrescriptionNo(),
                        prescription.getStatus(),
                        item.getDrugNo(),
                        item.getDrugNameSnapshot(),
                        item.getDrugNameSnapshot(),
                        item.getSpecificationSnapshot(),
                        item.getDosage(),
                        item.getFrequency(),
                        item.getRoute(),
                        item.getDays(),
                        item.getQuantity(),
                        item.getUnit(),
                        item.getUnitPrice(),
                        item.getSubtotal()));
            }
        }
        return items;
    }

    private List<MedicalRecordDTO.PrescriptionItemResponse> loadPrescriptionItemsOrDraftSnapshot(MedicalRecord record) {
        List<MedicalRecordDTO.PrescriptionItemResponse> savedItems = loadPrescriptionItems(record.getId());
        if (!savedItems.isEmpty()) {
            return savedItems;
        }
        return fromPrescriptionSnapshot(record.getPrescriptionsSnapshot());
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

    private List<MedicalRecordDTO.PrescriptionItemResponse> fromPrescriptionSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<MedicalRecordDTO.DraftPrescriptionItem> draftItems =
                    objectMapper.readValue(json, new TypeReference<List<MedicalRecordDTO.DraftPrescriptionItem>>() {});
            List<MedicalRecordDTO.PrescriptionItemResponse> items = new ArrayList<>();
            for (MedicalRecordDTO.DraftPrescriptionItem item : draftItems) {
                if (item == null) {
                    continue;
                }
                String drugName = item.getDrugName() != null ? item.getDrugName() : item.getName();
                items.add(new MedicalRecordDTO.PrescriptionItemResponse(
                        null,
                        "DRAFT",
                        item.getDrugNo(),
                        drugName,
                        drugName,
                        item.getSpecification(),
                        item.getDosage(),
                        item.getFrequency(),
                        item.getRoute(),
                        item.getDays(),
                        item.getQuantity(),
                        item.getUnit(),
                        item.getUnitPrice(),
                        item.getSubtotal()));
            }
            return items;
        } catch (Exception e) {
            log.warn("Draft prescription snapshot parsing failed: {}", json, e);
            return List.of();
        }
    }

    private MedicalRecord findExistingAppointmentRecord(Long appointmentId, Long patientId, Long doctorId) {
        if (appointmentId == null) {
            return null;
        }
        List<MedicalRecord> records = medicalRecordMapper.selectList(new QueryWrapper<MedicalRecord>()
                .eq("appointment_id", appointmentId)
                .eq("patient_id", patientId)
                .eq("doctor_id", doctorId)
                .orderByDesc("created_at"));
        return records.isEmpty() ? null : records.get(0);
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("序列化诊断失败", e);
            return null;
        }
    }

    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析诊断失败: {}", json, e);
            return List.of();
        }
    }

    private Map<Long, String> patientNamesByIds(List<Long> ids) {
        List<Long> distinctIds = ids == null ? List.of() : ids.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Result<Map<Long, String>> resp = patientFeignClient.namesByIds(distinctIds);
            return resp == null || resp.data() == null ? Collections.emptyMap() : resp.data();
        } catch (RuntimeException e) {
            log.warn("Patient name lookup failed, fallback to id display: {}", distinctIds, e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, DoctorProfileDTO> doctorProfilesByIds(List<Long> ids) {
        List<Long> distinctIds = ids == null ? List.of() : ids.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Result<Map<Long, DoctorProfileDTO>> resp = doctorFeignClient.profilesByIds(distinctIds);
            return resp == null || resp.data() == null ? Collections.emptyMap() : resp.data();
        } catch (RuntimeException e) {
            log.warn("Doctor profile lookup failed, fallback to id display: {}", distinctIds, e);
            return Collections.emptyMap();
        }
    }

    private Long resolveAppointmentId(String appointmentNo, Long patientId, Long doctorId) {
        if (appointmentNo == null || appointmentNo.isBlank()) {
            return null;
        }
        Result<AppointmentOwnershipDTO> resp = appointmentFeignClient.resolveOwnership(appointmentNo);
        if (resp == null || resp.data() == null || resp.data().appointmentId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在: " + appointmentNo);
        }
        AppointmentOwnershipDTO ownership = resp.data();
        if (!patientId.equals(ownership.patientId()) || !doctorId.equals(ownership.doctorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用非当前患者或医生的预约创建病历");
        }
        return ownership.appointmentId();
    }

    private Long resolvePatientId(String patientNo) {
        if (patientNo == null || patientNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "患者编号不能为空");
        }
        Long numericId = parseUnsignedLong(patientNo);
        if (numericId != null) {
            return numericId;
        }
        Result<EntityIdDTO> resp = patientFeignClient.resolveId(patientNo);
        if (resp == null || resp.data() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "患者不存在: " + patientNo);
        }
        return resp.data().id();
    }

    private Long resolveDoctorId(String doctorNo) {
        if (doctorNo == null || doctorNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "医生编号不能为空");
        }
        Long numericId = parseUnsignedLong(doctorNo);
        if (numericId != null) {
            return numericId;
        }
        Result<EntityIdDTO> resp = doctorFeignClient.resolveDoctorId(doctorNo);
        if (resp == null || resp.data() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "医生不存在: " + doctorNo);
        }
        return resp.data().id();
    }

    private Long resolveAppointmentFilter(String appointmentId, Long patientId, Long doctorId) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return null;
        }
        Long numericId = parseUnsignedLong(appointmentId);
        if (numericId != null) {
            return numericId;
        }
        Result<AppointmentOwnershipDTO> resp = appointmentFeignClient.resolveOwnership(appointmentId);
        if (resp == null || resp.data() == null || resp.data().appointmentId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "appointment not found: " + appointmentId);
        }
        AppointmentOwnershipDTO ownership = resp.data();
        if (patientId != null && !patientId.equals(ownership.patientId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "appointment does not belong to current patient scope");
        }
        if (doctorId != null && !doctorId.equals(ownership.doctorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "appointment does not belong to current doctor scope");
        }
        return ownership.appointmentId();
    }

    private Long resolvePatientScope(String patientId) {
        JwtPayload p = SecurityContext.getPayload();
        if (p == null || !p.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(p)) {
            Long selfPatientId = p.patientId();
            if (selfPatientId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联患者档案");
            }
            return selfPatientId;
        }
        return patientId != null && !patientId.isBlank() ? resolvePatientId(patientId) : null;
    }

    private Long resolveDoctorScope() {
        JwtPayload p = SecurityContext.getPayload();
        if (p == null || !p.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(p)) {
            return null;
        }
        if (isDoctor(p)) {
            Long selfDoctorId = p.doctorId();
            if (selfDoctorId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联医生档案");
            }
            return selfDoctorId;
        }
        return null;
    }

    private void enforceRecordAccess(MedicalRecord resource) {
        JwtPayload p = SecurityContext.getPayload();
        if (p == null || !p.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(p)) {
            Long selfPatientId = p.patientId();
            if (selfPatientId == null || !selfPatientId.equals(resource.getPatientId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该患者的病历");
            }
            return;
        }
        if (isDoctor(p)) {
            Long selfDoctorId = p.doctorId();
            if (selfDoctorId == null || !selfDoctorId.equals(resource.getDoctorId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问非本人接诊的病历");
            }
        }
    }

    private static boolean isPatient(JwtPayload p) {
        if (p == null) return false;
        if (p.primaryRole() != null && !p.primaryRole().isBlank()) {
            return "PATIENT".equals(p.primaryRole());
        }
        return p.roles() != null && p.roles().contains("PATIENT");
    }

    private static boolean isDoctor(JwtPayload p) {
        if (p == null) return false;
        if (p.primaryRole() != null && !p.primaryRole().isBlank()) {
            return "DOCTOR".equals(p.primaryRole());
        }
        return p.roles() != null && p.roles().contains("DOCTOR");
    }

    private static Long parseUnsignedLong(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "编号超出范围: " + value);
        }
    }
}
