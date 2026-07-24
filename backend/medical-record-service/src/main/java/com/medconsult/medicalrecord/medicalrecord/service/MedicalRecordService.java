package com.medconsult.medicalrecord.medicalrecord.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.medicalrecord.medicalrecord.dto.MedicalRecordDTO;
import com.medconsult.medicalrecord.medicalrecord.entity.MedicalRecord;

/**
 * 电子病历服务接口（对齐《接口文档》§2.6 + 架构文档 §2.3 内部接口）。
 *
 * <p>对外 5 接口：create / detail / list / updateDraft / archive。
 * <p>内部 1 接口：getByIdFull（供 ai-service 病历摘要，架构文档 §2.3）。
 */
public interface MedicalRecordService {

    /** §2.6.1 创建电子病历（初始 DRAFT） */
    MedicalRecordDTO.CreateResponse create(MedicalRecordDTO.CreateRequest req);

    /** §2.6.2 病历详情 */
    MedicalRecordDTO.DetailResponse detail(String recordNo);

    /** §2.6.3 分页查询病历（可按 patientId / appointmentId 过滤） */
    PageResult<MedicalRecordDTO.ListItem> list(int page, int pageSize, String patientId, String appointmentId);

    /** §2.6.4 更新草稿病历（仅 DRAFT 可改） */
    MedicalRecordDTO.UpdateResponse updateDraft(String recordNo, MedicalRecordDTO.UpdateDraftRequest req);

    /** §2.6.5 归档病历（DRAFT → ARCHIVED，不可逆） */
    MedicalRecordDTO.ArchiveResponse archive(String recordNo, MedicalRecordDTO.ArchiveRequest req);

    // ===== 内部接口 =====

    /**
     * 内部：按主键取病历全文（架构文档 §2.3，供 ai-service 病历摘要）。
     * <p>用 BIGINT 主键而非 record_no——跨服务内部调用主键更稳定。
     */
    MedicalRecordDTO.FullRecordResponse getByIdFull(Long id);

    /** 按编号取实体，未找到抛 NOT_FOUND（处方域复用） */
    MedicalRecord requireByNo(String recordNo);
}
