package com.medconsult.patient.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.feign.dto.PatientContextDTO;
import com.medconsult.common.web.MaskType;
import com.medconsult.patient.dto.PatientDTO;
import com.medconsult.patient.entity.Patient;
import com.medconsult.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 患者档案服务实现。
 *
 * <p>核心逻辑（对齐《需求文档》§4.1.1 业务规则）：
 * <ul>
 *   <li>创建：证件号/手机号至少填一项（规则 1）；同证件类型+证件号不允许重复（规则 2）→ CONFLICT</li>
 *   <li>查询详情：返回脱敏后的证件号/手机号（MaskType.ID_NO / PHONE）</li>
 *   <li>分页：MyBatis-Plus Page + QueryWrapper.or().like(name/phone/id_no/patient_no)</li>
 *   <li>更新：部分字段更新（phone/address/allergies/pastMedicalHistory/familyHistory/emergencyContact）</li>
 *   <li>状态流转：ACTIVE/DISABLED/MERGED（逻辑禁用，不做物理删除，规则 3）</li>
 *   <li>内部 context：Patient → PatientContextDTO（allergies JSON 串解析为 List，姓名脱敏）</li>
 * </ul>
 *
 * <p>JSON 字段（allergies/pastMedicalHistory/familyHistory）存为 JSON 数组串，
 * emergencyContact 存为 JSON 对象串，用 {@link ObjectMapper} 序列化/反序列化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientMapper patientMapper;
    private final ObjectMapper objectMapper;

    /** 允许的档案状态白名单（§2.2.5 / §2.4） */
    private static final List<String> ALLOWED_STATUS = List.of("ACTIVE", "DISABLED", "MERGED");

    // ===== §2.2.1 创建 =====

    @Override
    @Transactional
    public PatientDTO.SummaryResponse create(PatientDTO.CreateRequest req) {
        // 规则 1：证件号和手机号至少填一项（《需求文档》§4.1.1）
        boolean hasIdNo = req.getIdNo() != null && !req.getIdNo().isBlank();
        boolean hasPhone = req.getPhone() != null && !req.getPhone().isBlank();
        if (!hasIdNo && !hasPhone) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "证件号和手机号至少填写一项");
        }

        // 规则 2：同一证件类型 + 证件号不允许重复建档（仅当填了证件号时校验）
        if (hasIdNo) {
            String idType = req.getIdType() != null ? req.getIdType() : "ID_CARD";
            Long count = patientMapper.selectCount(new QueryWrapper<Patient>()
                    .eq("id_type", idType)
                    .eq("id_no", req.getIdNo()));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "该证件号已存在患者档案: " + idType + "/" + req.getIdNo());
            }
        }

        Patient p = new Patient();
        p.setPatientNo(generatePatientNo());
        p.setName(req.getName());
        p.setGender(req.getGender() != null ? req.getGender() : "UNKNOWN");
        p.setBirthDate(req.getBirthDate());
        p.setIdType(hasIdNo ? (req.getIdType() != null ? req.getIdType() : "ID_CARD") : req.getIdType());
        p.setIdNo(req.getIdNo());
        p.setPhone(req.getPhone());
        p.setAddress(req.getAddress());
        p.setAllergies(toJsonArray(req.getAllergies()));
        p.setPastMedicalHistory(toJsonArray(req.getPastMedicalHistory()));
        p.setFamilyHistory(toJsonArray(req.getFamilyHistory()));
        p.setEmergencyContact(toJsonObject(req.getEmergencyContact()));
        p.setStatus("ACTIVE");
        patientMapper.insert(p);

        return new PatientDTO.SummaryResponse(p.getPatientNo(), p.getName(), p.getStatus());
    }

    // ===== §2.2.2 详情 =====

    @Override
    public PatientDTO.DetailResponse detail(String patientNo) {
        Patient p = requireByPatientNo(patientNo);
        return new PatientDTO.DetailResponse(
                p.getPatientNo(),
                p.getName(),
                p.getGender(),
                p.getBirthDate(),
                MaskType.ID_NO.mask(p.getIdNo()),
                MaskType.PHONE.mask(p.getPhone()),
                fromJsonArray(p.getAllergies()),
                fromJsonArray(p.getPastMedicalHistory()),
                p.getStatus());
    }

    // ===== §2.2.3 分页 =====

    @Override
    public PageResult<PatientDTO.ListItem> list(int page, int pageSize, String keyword) {
        Page<Patient> p = new Page<>(page <= 0 ? 1 : page, pageSize <= 0 ? 10 : pageSize);
        QueryWrapper<Patient> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 按 姓名/手机号/证件号/患者编号 模糊检索（需求 §4.1.1 核心功能 3）
            qw.and(w -> w.like("name", keyword)
                    .or().like("phone", keyword)
                    .or().like("id_no", keyword)
                    .or().like("patient_no", keyword));
        }
        qw.orderByDesc("created_at");
        IPage<Patient> result = patientMapper.selectPage(p, qw);

        List<PatientDTO.ListItem> items = new ArrayList<>();
        for (Patient pat : result.getRecords()) {
            items.add(new PatientDTO.ListItem(
                    pat.getPatientNo(),
                    pat.getName(),
                    MaskType.PHONE.mask(pat.getPhone()),
                    pat.getStatus()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.2.4 更新 =====

    @Override
    @Transactional
    public PatientDTO.UpdateResponse update(String patientNo, PatientDTO.UpdateRequest req) {
        Patient p = requireByPatientNo(patientNo);

        // 部分字段更新（仅更新非 null 字段，避免覆盖未传字段）
        if (req.getPhone() != null) {
            p.setPhone(req.getPhone());
        }
        if (req.getAddress() != null) {
            p.setAddress(req.getAddress());
        }
        if (req.getAllergies() != null) {
            p.setAllergies(toJsonArray(req.getAllergies()));
        }
        if (req.getPastMedicalHistory() != null) {
            p.setPastMedicalHistory(toJsonArray(req.getPastMedicalHistory()));
        }
        if (req.getFamilyHistory() != null) {
            p.setFamilyHistory(toJsonArray(req.getFamilyHistory()));
        }
        if (req.getEmergencyContact() != null) {
            p.setEmergencyContact(toJsonObject(req.getEmergencyContact()));
        }
        patientMapper.updateById(p);

        return new PatientDTO.UpdateResponse(p.getPatientNo(),
                p.getUpdatedAt() != null ? p.getUpdatedAt().atOffset(OffsetDateTime.now().getOffset())
                        : OffsetDateTime.now());
    }

    // ===== §2.2.5 状态流转 =====

    @Override
    @Transactional
    public PatientDTO.StatusResponse updateStatus(String patientNo, PatientDTO.StatusUpdateRequest req) {
        Patient p = requireByPatientNo(patientNo);

        String newStatus = req.getStatus();
        if (!ALLOWED_STATUS.contains(newStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法档案状态: " + newStatus);
        }
        p.setStatus(newStatus);
        patientMapper.updateById(p);

        log.info("患者档案状态流转: patientNo={} {} → {} reason={}",
                patientNo, p.getStatus(), newStatus, req.getReason());
        return new PatientDTO.StatusResponse(p.getPatientNo(), newStatus);
    }

    // ===== 内部接口：context（架构文档 §2.3）=====

    @Override
    public PatientContextDTO internalContext(Long patientId) {
        Patient p = patientMapper.selectById(patientId);
        if (p == null) {
            return PatientContextDTO.empty(patientId);
        }
        // 姓名脱敏（PatientContextDTO 故意不含敏感原始字段，架构文档 §2.3）
        String maskedName = MaskType.NAME.mask(p.getName());
        Integer age = computeAge(p.getBirthDate());
        return new PatientContextDTO(
                p.getId(),
                maskedName,
                age,
                p.getGender(),
                fromJsonArray(p.getAllergies()),
                fromJsonArray(p.getPastMedicalHistory()),
                // currentMedications 来自 prescription_item，patient-service 暂不维护，
                // 返回空列表占位（架构文档 §2.3 出参要点）
                List.of());
    }

    // ===== 内部接口：allergies（架构文档 §2.3）=====

    @Override
    public List<String> internalAllergies(Long patientId) {
        Patient p = patientMapper.selectById(patientId);
        if (p == null) {
            return List.of();
        }
        return fromJsonArray(p.getAllergies());
    }

    // ===== 私有助手 =====

    /**
     * 按 patient_no 查询，未找到抛 NOT_FOUND。
     * <p>MyBatis-Plus @TableLogic 自动追加 deleted=0 过滤。
     */
    private Patient requireByPatientNo(String patientNo) {
        if (patientNo == null || patientNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "患者编号不能为空");
        }
        Patient p = patientMapper.selectOne(new QueryWrapper<Patient>().eq("patient_no", patientNo));
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "患者档案不存在: " + patientNo);
        }
        return p;
    }

    /**
     * 生成患者编号：P + 雪花序列（IdWorker 生成的 Long 的无符号 base36）。
     * <p>仿 auth-service 的 generateUserNo（U 前缀）；DB 有 uk_patient_no 兜底。
     */
    private static String generatePatientNo() {
        long id = IdWorker.getId();
        return "P" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }

    /**
     * List<String> → JSON 数组串。null/空返回 null（不落库空串，避免歧义）。
     */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("序列化 JSON 数组失败，原样存首个元素: {}", list, e);
            return list.toString();
        }
    }

    /**
     * 对象 → JSON 串。null 返回 null。
     */
    private String toJsonObject(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("序列化 JSON 对象失败: {}", obj, e);
            return null;
        }
    }

    /**
     * JSON 数组串 → List<String>。null/空/解析失败返回空列表。
     */
    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("反序列化 JSON 数组失败，原样返回单元素: {}", json, e);
            return List.of(json);
        }
    }

    /**
     * 由出生日期计算年龄（周岁）。null 返回 null。
     */
    private static Integer computeAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
