package com.medconsult.medicalrecord.medicalrecord.service;

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
 *   <li>创建：recordNo = MR + 雪花 base36；patientId/doctorId 用业务编号的正哈希落库（同款策略见
 *       outpatient AppointmentTxService——保证同编号始终映射到同 BIGINT，便于按 patientId 过滤；
 *       下一批接 Feign 后替换为真实主键反查，无需改表）；status 初始 DRAFT</li>
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

    // ===== §2.6.1 创建 =====

    @Override
    @Transactional
    public MedicalRecordDTO.CreateResponse create(MedicalRecordDTO.CreateRequest req) {
        MedicalRecord r = new MedicalRecord();
        r.setRecordNo(generateRecordNo());
        r.setPatientId(positiveHash(req.getPatientId()));
        r.setDoctorId(positiveHash(req.getDoctorId()));
        if (req.getAppointmentId() != null && !req.getAppointmentId().isBlank()) {
            r.setAppointmentId(positiveHash(req.getAppointmentId()));
        }
        r.setChiefComplaint(req.getChiefComplaint());
        r.setPresentIllness(req.getPresentIllness());
        r.setPastHistory(req.getPastHistory());
        r.setPhysicalExam(req.getPhysicalExam());
        r.setInitialDiagnosis(toJsonArray(req.getInitialDiagnosis()));
        r.setDoctorAdvice(req.getDoctorAdvice());
        // prescriptionsSnapshot 留 null（修订项 §2.1：处方独立成表）
        r.setStatus("DRAFT");
        medicalRecordMapper.insert(r);
        log.info("病历创建: recordNo={} patientId={} doctorId={}",
                r.getRecordNo(), req.getPatientId(), req.getDoctorId());
        return new MedicalRecordDTO.CreateResponse(r.getRecordNo(), r.getStatus());
    }

    // ===== §2.6.2 详情 =====

    @Override
    public MedicalRecordDTO.DetailResponse detail(String recordNo) {
        MedicalRecord r = requireByNo(recordNo);
        // patientId/doctorId 在落库时是哈希，详情接口回传业务编号需 Feign 反查；本批无 Feign，
        // 暂回传 null（避免传哈希误导调用方）。下一批接 patient/doctor Feign 后回填真实编号。
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
    public PageResult<MedicalRecordDTO.ListItem> list(int page, int pageSize, String patientId) {
        Page<MedicalRecord> p = new Page<>(page <= 0 ? 1 : page, pageSize <= 0 ? 10 : pageSize);
        QueryWrapper<MedicalRecord> qw = new QueryWrapper<>();
        if (patientId != null && !patientId.isBlank()) {
            // patientId 传的是业务编号（patient_no），落库时存的是其正哈希；用同款哈希过滤
            qw.eq("patient_id", positiveHash(patientId));
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
    public MedicalRecordDTO.UpdateResponse updateDraft(String recordNo, MedicalRecordDTO.UpdateDraftRequest req) {
        MedicalRecord r = requireByNo(recordNo);
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
    public MedicalRecordDTO.ArchiveResponse archive(String recordNo, MedicalRecordDTO.ArchiveRequest req) {
        MedicalRecord r = requireByNo(recordNo);
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

    /** 业务编号 → 正哈希 Long（同款策略见 outpatient AppointmentTxService）。下一批接 Feign 后替换为真实主键反查。 */
    private static long positiveHash(String businessNo) {
        if (businessNo == null) {
            return 0L;
        }
        long h = businessNo.hashCode();
        return h == Long.MIN_VALUE ? 0L : Math.abs(h);
    }

    /** 生成病历编号：MR + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_record_no 兜底 */
    private static String generateRecordNo() {
        long id = IdWorker.getId();
        return "MR" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
