package com.medconsult.medicalrecord.medicalrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.AppointmentFeignClient;
import com.medconsult.common.feign.client.DoctorFeignClient;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.AppointmentOwnershipDTO;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.mq.audit.AuditLog;
import com.medconsult.medicalrecord.medicalrecord.dto.MedicalRecordDTO;
import com.medconsult.medicalrecord.medicalrecord.entity.MedicalRecord;
import com.medconsult.medicalrecord.medicalrecord.mapper.MedicalRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 电子病历服务实现（对齐《接口文档》§2.6 / 架构文档 §2.3）。
 *
 * <p>核心逻辑：
 * <ul>
 *   <li>创建：recordNo = MR + 雪花 base36；patientId/doctorId 用 Feign 反查 patient/doctor 业务编号
 *       的真实 BIGINT 主键落库（替代早期正哈希占位，根治跨患者/医生串号）；appointmentId 暂仍用
 *       正哈希占位（outpatient 尚未暴露反查接口，且非查询键）；status 初始 DRAFT</li>
 *   <li>initialDiagnosis/finalDiagnosis：List&lt;String&gt; 序列化为 JSON 串入库，读出时反序列化</li>
 *   <li>更新草稿：仅 DRAFT 可改（ARCHIVED 抛 CONFLICT，医疗文书不可变，§6.1）；非空字段才覆盖</li>
 *   <li>归档：DRAFT → ARCHIVED，回填 archivedAt；终态不可逆</li>
 *   <li>列表：按 patient_id（= patient_no 哈希）过滤，分页</li>
 *   <li>内部 getByIdFull：返回完整病历（供 ai-service 摘要）</li>
 * </ul>
 *
 * <p><b>无并发写锁</b>：病历创建/更新是单医生操作，无抢号/库存类并发场景；归档由医生本人触发，
 * 也不存在多人同时归档。故本域不加 Redis 锁（区别于处方审方/调剂）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordMapper medicalRecordMapper;
    private final ObjectMapper objectMapper;
    /** Feign 反查 patient_no / doctor_no → 真实 BIGINT 主键（替代正哈希占位，根治串号） */
    private final PatientFeignClient patientFeignClient;
    private final DoctorFeignClient doctorFeignClient;
    private final AppointmentFeignClient appointmentFeignClient;
    private final MedicalRecordTxService txService;

    // ===== §2.6.1 创建 =====

    /**
     * 创建病历。
     * <p><b>Feign 反查在事务外</b>：patient_no/doctor_no 经 Feign 反查真实主键，HTTP 调用不占用
     * DB 事务/连接（避免长事务跨网络往返）。反查通过后，DB 写入交给 {@link MedicalRecordTxService} 事务方法。
     */
    @Override
    public MedicalRecordDTO.CreateResponse create(MedicalRecordDTO.CreateRequest req) {
        JwtPayload payload = SecurityContext.requireUser();
        if (isPatient(payload)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "患者无权创建病历");
        }

        // ① 事务外：Feign 反查真实主键（patient_no/doctor_no 不存在 → NOT_FOUND，此时无 DB 写入可回滚）
        Long patientId = resolvePatientId(req.getPatientId());
        Long requestedDoctorId = resolveDoctorId(req.getDoctorId());
        // ② 身份与归属校验（§2.3 权限矩阵「病历 创建=DOCTOR 自己接诊的」）：
        //    DOCTOR 身份下请求医生必须解析为 JWT doctorId，防伪造其他医生接诊上下文建病历。
        Long doctorId = enforceCreateScope(payload, requestedDoctorId);
        Long appointmentId = resolveAppointmentId(req.getAppointmentId(), patientId, doctorId);

        // ③ 事务内：纯 DB 写入（无远程调用，事务持有时间短）
        return txService.createInTx(req, patientId, doctorId, appointmentId);
    }

    /**
     * 创建病历的身份归属校验（参考 AppointmentServiceImpl.enforceAppointmentOwnership）。
     * <p>DOCTOR 身份：请求医生必须解析为 JWT 内的本人 doctorId，防伪造他人接诊上下文给任意患者建病历。
     * <p>HOSPITAL_ADMIN：放行（管理员可代建，行级权限留待 RBAC 五表阶段）。
     * <p>PATIENT：已在 Feign 反查前拒绝（§2.3 矩阵「病历 创建」无 PATIENT）。
     */
    private Long enforceCreateScope(JwtPayload payload, Long requestedDoctorId) {
        if (isDoctor(payload)) {
            Long selfDoctorId = payload.doctorId();
            if (selfDoctorId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联医生档案，无法创建病历");
            }
            if (!selfDoctorId.equals(requestedDoctorId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权以非本人接诊医生创建病历");
            }
            return selfDoctorId;
        }
        // HOSPITAL_ADMIN/PHARMACY_ADMIN 保留请求体值（代建场景）
        return requestedDoctorId;
    }

    // ===== §2.6.2 详情 =====

    @Override
    public MedicalRecordDTO.DetailResponse detail(String recordNo) {
        MedicalRecord r = requireByNo(recordNo);
        // 越权防护（IDOR）：PATIENT 只能查自己的病历；DOCTOR 只能查自己接诊的病历。
        // medical_record.patient_id 落库已是真实主键（create 时 Feign 反查 no→id），
        // medical_record.doctor_id 与 JWT 里的 doctorId（BIGINT PK）同类型可直接比较。
        enforceRecordAccess(r);
        // patientId/doctorId 落库已是真实主键（create 时 Feign 反查 no→id）；但详情接口回传
        // 业务编号需 id→no 反向解析，目前未实现（display 反查是独立需求，留待下一批）。
        // 暂回传 null（避免传主键误导调用方当作 patient_no 使用）。
        return new MedicalRecordDTO.DetailResponse(
                r.getRecordNo(),
                null,
                null,
                r.getChiefComplaint(),
                fromJsonArray(r.getInitialDiagnosis()),
                r.getStatus());
    }

    // ===== §2.6.3 列表 =====

    @Override
    public PageResult<MedicalRecordDTO.ListItem> list(int page, int pageSize, String patientId, Long appointmentId) {
        Page<MedicalRecord> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<MedicalRecord> qw = new QueryWrapper<>();
        // 越权防护（IDOR）：PATIENT 只能列自己的病历；DOCTOR 只能列自己接诊的病历。
        // PATIENT 身份时忽略入参 patientId，强制限定为本人 patientId，防用他人 patient_no 越权拉取。
        // 非 PATIENT（DOCTOR/管理员）尊重入参 patientId 过滤。
        Long scopePatientId = resolvePatientScope(patientId);
        if (scopePatientId != null) {
            qw.eq("patient_id", scopePatientId);
        }
        Long scopeDoctorId = resolveDoctorScope();
        if (scopeDoctorId != null) {
            qw.eq("doctor_id", scopeDoctorId);
        }
        // 可选：按预约 ID 过滤（供"完成就诊前校验是否有病历"场景）
        if (appointmentId != null) {
            qw.eq("appointment_id", appointmentId);
        }
        qw.orderByDesc("created_at");
        IPage<MedicalRecord> result = medicalRecordMapper.selectPage(p, qw);
        List<MedicalRecordDTO.ListItem> items = new ArrayList<>();
        for (MedicalRecord r : result.getRecords()) {
            // doctorName 本批无医生信息（跨服务），暂留 null
            items.add(new MedicalRecordDTO.ListItem(
                    r.getRecordNo(),
                    null,
                    r.getChiefComplaint(),
                    r.getStatus()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.6.4 更新草稿 =====

    @Override
    @Transactional
    @AuditLog(
            resourceType = "MEDICAL_RECORD",
            action = "UPDATE",
            resourceId = "#result.recordId()",
            detail = "'draft updated'")
    public MedicalRecordDTO.UpdateResponse updateDraft(String recordNo, MedicalRecordDTO.UpdateDraftRequest req) {
        MedicalRecord r = requireByNo(recordNo);
        // 越权防护（IDOR）：PATIENT 只能改自己的病历草稿；DOCTOR 只能改自己接诊的病历草稿
        enforceRecordAccess(r);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "仅草稿病历可修改，当前状态: " + r.getStatus() + "（归档后不可改，§6.1 医疗文书不可变）");
        }
        // 非空字段才覆盖（PATCH 语义）
        if (req.getChiefComplaint() != null) {
            r.setChiefComplaint(req.getChiefComplaint());
        }
        if (req.getPresentIllness() != null) {
            r.setPresentIllness(req.getPresentIllness());
        }
        if (req.getPastHistory() != null) {
            r.setPastHistory(req.getPastHistory());
        }
        if (req.getPhysicalExam() != null) {
            r.setPhysicalExam(req.getPhysicalExam());
        }
        if (req.getFinalDiagnosis() != null) {
            r.setFinalDiagnosis(toJsonArray(req.getFinalDiagnosis()));
        }
        if (req.getDoctorAdvice() != null) {
            r.setDoctorAdvice(req.getDoctorAdvice());
        }
        medicalRecordMapper.updateById(r);
        log.info("病历草稿更新: recordNo={}", recordNo);
        return new MedicalRecordDTO.UpdateResponse(r.getRecordNo(), r.getUpdatedAt());
    }

    // ===== §2.6.5 归档 =====

    @Override
    @Transactional
    @AuditLog(
            resourceType = "MEDICAL_RECORD",
            action = "ARCHIVE",
            resourceId = "#result.recordId()",
            detail = "'status=' + #result.status()")
    public MedicalRecordDTO.ArchiveResponse archive(String recordNo, MedicalRecordDTO.ArchiveRequest req) {
        MedicalRecord r = requireByNo(recordNo);
        // 越权防护（IDOR）：PATIENT 不能归档他人病历；DOCTOR 只能归档自己接诊的病历
        enforceRecordAccess(r);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "仅草稿病历可归档，当前状态: " + r.getStatus());
        }
        r.setStatus("ARCHIVED");
        r.setArchivedAt(LocalDateTime.now());
        // prescriptionsSnapshot 本批不填（处方数据经 prescription 表反查即可）；
        // batch 2 接入处方完整流转后，归档时按需序列化处方列表写入快照列
        medicalRecordMapper.updateById(r);
        log.info("病历归档: recordNo={} confirmBy={}", recordNo, req.getConfirmBy());
        return new MedicalRecordDTO.ArchiveResponse(r.getRecordNo(), r.getStatus());
    }

    // ===== 内部接口 =====

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
        MedicalRecord r = medicalRecordMapper.selectOne(
                new QueryWrapper<MedicalRecord>().eq("record_no", recordNo));
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "病历不存在: " + recordNo);
        }
        return r;
    }

    // ===== 私有助手 =====

    /** List<String> → JSON 数组串；空列表/null 返回 null */
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

    /** JSON 数组串 → List<String>；空/null 返回空列表 */
    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("诊断 JSON 解析失败: {}", json, e);
            return List.of();
        }
    }

    private Long resolveAppointmentId(String appointmentNo, Long patientId, Long doctorId) {
        if (appointmentNo == null || appointmentNo.isBlank()) {
            return null;
        }
        Result<AppointmentOwnershipDTO> resp;
        try {
            resp = appointmentFeignClient.resolveOwnership(appointmentNo);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                log.warn("预约归属内部校验未通过服务鉴权，跳过 appointment_id 绑定: appointmentNo={}", appointmentNo);
                return null;
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("预约归属内部校验失败，跳过 appointment_id 绑定: appointmentNo={}, error={}",
                    appointmentNo, ex.getMessage());
            return null;
        }
        if (resp == null || resp.data() == null || resp.data().appointmentId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在: " + appointmentNo);
        }
        AppointmentOwnershipDTO ownership = resp.data();
        if (!patientId.equals(ownership.patientId()) || !doctorId.equals(ownership.doctorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用非当前患者或医生的预约创建病历");
        }
        return ownership.appointmentId();
    }

    /**
     * patient_no → 真实 BIGINT 主键（Feign 反查 patient-service）。
     * <p>不存在时下游返回 NOT_FOUND → FeignErrorDecoder 转 BusinessException，由调用方/事务处理。
     */
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

    // ===== 越权防护（IDOR，架构 §4.3 SELF 数据范围）=====
    //
    // 当前调用者为 PATIENT 时，强制只能访问自己的数据；DOCTOR 强制限定自己接诊的数据（架构 §4.3 SELF/ASSIGNED）。
    // 身份取自 SecurityContext（网关已解析 X-User-* 头重建 JwtPayload，§4.4）。
    // 直连（无网关头、无 token）时 SecurityContext.getPayload() 返回 null，按"匿名拒绝"处理。

    /**
     * 列表/查询的 patient 作用域解析：
     * <ul>
     *   <li>PATIENT 身份：忽略入参 patientId，强制返回本人 patientId（只能查自己的）</li>
     *   <li>DOCTOR/管理员身份：尊重入参 patientId（传了则反查真实主键过滤，没传则不过滤）</li>
     *   <li>匿名（无身份）：拒绝（401），避免未登录遍历</li>
     * </ul>
     *
     * @param patientId 入参 patient_no（DOCTOR/管理员用）
     * @return 用于查询的 patient_id 主键；null 表示不按 patient 过滤（仅 DOCTOR/管理员未传 patientId 时）
     */
    private Long resolvePatientScope(String patientId) {
        JwtPayload p = SecurityContext.getPayload();
        if (p == null || !p.isUser()) {
            // 匿名/服务身份访问对外患者数据接口 → 拒绝
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (isPatient(p)) {
            Long selfPatientId = p.patientId();
            if (selfPatientId == null) {
                // PATIENT 身份但 JWT 无 patientId 关联（档案未绑定）→ 拒绝，防越权
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联患者档案，无法查询病历");
            }
            return selfPatientId;
        }
        // 非 PATIENT（DOCTOR/管理员）：尊重入参
        return (patientId != null && !patientId.isBlank()) ? resolvePatientId(patientId) : null;
    }

    /**
     * 列表/查询的 doctor 作用域解析：
     * <ul>
     *   <li>DOCTOR 身份：强制返回本人 doctorId（只能查自己接诊的病历）</li>
     *   <li>PATIENT/管理员身份：不按 doctor 过滤，分别交由 patient scope 或管理员权限处理</li>
     * </ul>
     */
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
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联医生档案，无法查询病历");
            }
            return selfDoctorId;
        }
        return null;
    }

    /**
     * 单条资源的归属校验（detail/update/delete 等带具体 record 的场景）。
     * <p>PATIENT 身份：资源的 patient_id 必须等于本人 patientId，否则 FORBIDDEN。
     * <p>DOCTOR 身份：资源的 doctor_id 必须等于本人 doctorId，否则 FORBIDDEN。
     * <p>管理员：不校验，保留既有跨病历管理行为。
     */
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

    /** 是否 PATIENT 主角色；旧 token 缺 primaryRole 时回退 roles。 */
    private static boolean isPatient(JwtPayload p) {
        if (p == null) return false;
        if (p.primaryRole() != null && !p.primaryRole().isBlank()) {
            return "PATIENT".equals(p.primaryRole());
        }
        return p.roles() != null && p.roles().contains("PATIENT");
    }

    /** 是否 DOCTOR 主角色；旧 token 缺 primaryRole 时回退 roles。 */
    private static boolean isDoctor(JwtPayload p) {
        if (p == null) return false;
        if (p.primaryRole() != null && !p.primaryRole().isBlank()) {
            return "DOCTOR".equals(p.primaryRole());
        }
        return p.roles() != null && p.roles().contains("DOCTOR");
    }

    /** doctor_no → 真实 BIGINT 主键（Feign 反查 outpatient-service）。 */
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
