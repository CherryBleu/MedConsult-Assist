package com.medconsult.medicalrecord.prescription.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.medicalrecord.prescription.dto.PrescriptionDTO;
import com.medconsult.medicalrecord.prescription.entity.Prescription;

/**
 * 处方服务接口（对齐《修改建议》§2.1 处方接口补充表，本批第 1 批 5 接口）。
 *
 * <p>第 1 批：create / list / detail / submit / review。
 * <p>第 2 批（后续）：pay / dispense / complete / cancel。
 */
public interface PrescriptionService {

    /** 开方（初始 DRAFT，写主表 + 明细） */
    PrescriptionDTO.CreateResponse create(PrescriptionDTO.CreateRequest req);

    /** 处方列表（可按 status 过滤，药师审方工作台） */
    PageResult<PrescriptionDTO.ListItem> list(int page, int pageSize, String status);

    /** 处方详情（含明细） */
    PrescriptionDTO.DetailResponse detail(String prescriptionNo);

    /** 提交审方（DRAFT → PENDING_REVIEW） */
    PrescriptionDTO.SubmitResponse submit(String prescriptionNo);

    /** 审方（PENDING_REVIEW → APPROVED | REJECTED，Redis 锁内防并发） */
    PrescriptionDTO.ReviewResponse review(String prescriptionNo, PrescriptionDTO.ReviewRequest req);

    /** 按编号取实体，未找到抛 NOT_FOUND */
    Prescription requireByNo(String prescriptionNo);
}
