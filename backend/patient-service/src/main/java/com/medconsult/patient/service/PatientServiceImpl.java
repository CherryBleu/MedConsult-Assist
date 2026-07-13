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
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.PatientContextDTO;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
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

    /** 允许的档案状态白名单（§2.2.5 / §2.4）—— 委托 DTO 常量，避免重复定义 */
    private static final List<String> ALLOWED_STATUS =
            List.copyOf(PatientDTO.ALLOWED_STATUS);

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

        // 规则 2 扩展：手机号唯一性校验（仅当填了手机号时）
        if (hasPhone) {
            Long phoneCount = patientMapper.selectCount(new QueryWrapper<Patient>()
                    .eq("phone", req.getPhone()));
            if (phoneCount != null && phoneCount > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "手机号已存在患者档案: " + req.getPhone());
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
        // 越权防护（IDOR，架构 §4.3 SELF）：PATIENT 只能查自己的档案（含脱敏证件号/手机号）。
        // patient.id 是主键，与 JWT patientId（sys_user.patient_id）同类型比较。
        // DOCTOR/管理员不限制（可查接诊范围内患者）。
        enforcePatientOwnership(p);
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
        // 越权防护（IDOR，架构 §4.3 SELF）：PATIENT 只能查自己的档案。
        // PATIENT 身份时强制限定为本人 patientId，忽略入参 keyword（防用搜索越权遍历他人档案）。
        // DOCTOR/管理员可按 keyword 全局搜索。
        Long scopePatientId = resolvePatientScope();
        Page<Patient> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<Patient> qw = new QueryWrapper<>();
        if (scopePatientId != null) {
            // PATIENT 身份：只查自己
            qw.eq("id", scopePatientId);
        } else if (keyword != null && !keyword.isBlank()) {
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
                    pat.getGender(),
                    computeAge(pat.getBirthDate()),
                    MaskType.PHONE.mask(pat.getPhone()),
                    MaskType.ID_NO.mask(pat.getIdNo()),
                    pat.getStatus(),
                    pat.getCreatedAt()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.2.4 更新 =====

    @Override
    @Transactional
    public PatientDTO.UpdateResponse update(String patientNo, PatientDTO.UpdateRequest req) {
        Patient p = requireByPatientNo(patientNo);
        // 越权防护（IDOR）：PATIENT 只能改自己的档案
        enforcePatientOwnership(p);

        // 部分字段更新（仅更新非 null 字段，避免覆盖未传字段）
        if (req.getPhone() != null) {
            // 唯一性校验：若 phone 变更且与现状不同，校验是否已被其他档案占用
            String newPhone = req.getPhone().isBlank() ? null : req.getPhone();
            String currentPhone = p.getPhone();
            boolean phoneChanged = (newPhone == null) != (currentPhone == null)
                    || (newPhone != null && !newPhone.equals(currentPhone));
            if (phoneChanged && newPhone != null && !newPhone.isBlank()) {
                Long cnt = patientMapper.selectCount(new QueryWrapper<Patient>()
                        .eq("phone", newPhone)
                        .ne("id", p.getId()));
                if (cnt != null && cnt > 0) {
                    throw new BusinessException(ErrorCode.CONFLICT, "手机号已被其他档案占用: " + newPhone);
                }
            }
            p.setPhone(newPhone);
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
        // 越权防护（IDOR）：PATIENT 不能改档案状态（DISABLED/MERGED 是管理员操作），
        // 即便是自己的档案也不允许自助禁用/合并。PATIENT 身份直接拒绝。
        enforceNotPatient("修改患者档案状态");

        String oldStatus = p.getStatus();
        String newStatus = req.getStatus();
        if (!ALLOWED_STATUS.contains(newStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法档案状态: " + newStatus);
        }
        p.setStatus(newStatus);
        patientMapper.updateById(p);

        log.info("患者档案状态流转: patientNo={} {} → {} reason={}",
                patientNo, oldStatus, newStatus, req.getReason());
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

    // ===== 内部接口：resolveId（架构文档 §2.3 补充）=====

    @Override
    public EntityIdDTO internalResolveId(String patientNo) {
        // 复用 requireByPatientNo：不存在直接抛 NOT_FOUND（由 FeignErrorDecoder 传给调用方）
        Patient p = requireByPatientNo(patientNo);
        return EntityIdDTO.of(p.getId());
    }

    @Override
    @Transactional
    public EntityIdDTO internalCreate(String name, String idNo, String phone, String idType) {
        // 复用 create 的校验逻辑：证件/手机至少一项 + 唯一性
        // 注册即建档场景：PATIENT 角色身份证必填（auth-service 侧已强制），phone 来自注册表单
        String resolvedIdType = (idType != null && !idType.isBlank()) ? idType : "ID_CARD";

        // 证件号唯一性校验
        if (idNo != null && !idNo.isBlank()) {
            Long count = patientMapper.selectCount(new QueryWrapper<Patient>()
                    .eq("id_type", resolvedIdType)
                    .eq("id_no", idNo));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "该证件号已存在患者档案: " + resolvedIdType + "/" + idNo);
            }
        }
        // 手机号唯一性校验
        if (phone != null && !phone.isBlank()) {
            Long phoneCount = patientMapper.selectCount(new QueryWrapper<Patient>()
                    .eq("phone", phone));
            if (phoneCount != null && phoneCount > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "手机号已存在患者档案: " + phone);
            }
        }

        Patient p = new Patient();
        p.setPatientNo(generatePatientNo());
        p.setName(name);
        p.setGender("UNKNOWN");
        p.setIdType(resolvedIdType);
        p.setIdNo(idNo);
        p.setPhone(phone);
        p.setStatus("ACTIVE");
        patientMapper.insert(p);

        return EntityIdDTO.of(p.getId());
    }

    // ===== 越权防护（IDOR，架构 §4.3 SELF 数据范围）=====
    //
    // PATIENT 身份只能访问自己的档案；DOCTOR / 管理员不限制（架构 §4.3 ALL/ASSIGNED）。
    // 身份取自 SecurityContext（网关已解析 X-User-* 头重建 JwtPayload，§4.4）。
    // 直连（无网关头、无 token）时 SecurityContext.getPayload() 返回 null，按"匿名拒绝"处理。

    /**
     * 单条档案归属校验（detail/update 等带具体 Patient 的场景）。
     * <p>PATIENT 身份：档案主键 id 必须等于本人 patientId，否则 FORBIDDEN。
     * DOCTOR/管理员：不校验。
     */
    private void enforcePatientOwnership(Patient p) {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null || !payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(payload)) {
            Long selfPatientId = payload.patientId();
            if (selfPatientId == null || !selfPatientId.equals(p.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该患者档案");
            }
        }
    }

    /**
     * 列表的 patient 作用域解析：
     * <ul>
     *   <li>PATIENT 身份：返回本人 patientId（列表强制只查自己）</li>
     *   <li>DOCTOR/管理员：返回 null（不按 patient 过滤，可全局搜索）</li>
     *   <li>匿名：拒绝（401）</li>
     * </ul>
     *
     * @return 用于查询的 patient 主键 id；null 表示不按 patient 过滤（DOCTOR/管理员）
     */
    private Long resolvePatientScope() {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null || !payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(payload)) {
            Long selfPatientId = payload.patientId();
            if (selfPatientId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联患者档案，无法查询");
            }
            return selfPatientId;
        }
        return null;
    }

    /**
     * 拒绝 PATIENT 身份执行管理类操作（如状态流转）。
     * <p>PATIENT 不得自助禁用/合并档案，这些是管理员操作。
     */
    private void enforceNotPatient(String operation) {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null || !payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(payload)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "患者账号无权" + operation + "，请联系管理员");
        }
    }

    /** 是否 PATIENT 主角色（primaryRole 或 roles 含 PATIENT） */
    private static boolean isPatient(JwtPayload p) {
        if (p == null) return false;
        if ("PATIENT".equals(p.primaryRole())) return true;
        return p.roles() != null && p.roles().contains("PATIENT");
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
        // 兼容 /auth/me 返回的 BIGINT 主键 id（auth-service 把 sys_user.patient_id 作为 patientId 返回）：
        // patient_no 查不到时，若入参是纯数字，回退按主键 id 查一次。
        if (p == null && patientNo.chars().allMatch(Character::isDigit)) {
            p = patientMapper.selectById(Long.parseLong(patientNo));
        }
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
