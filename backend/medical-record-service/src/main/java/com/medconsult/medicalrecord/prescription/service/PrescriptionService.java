package com.medconsult.medicalrecord.prescription.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.medicalrecord.prescription.dto.PrescriptionDTO;
import com.medconsult.medicalrecord.prescription.entity.Prescription;

/**
 * 处方服务接口（对齐《修改建议》§2.1 处方接口补充表 + 架构文档 §6.2 8 态状态机）。
 *
 * <p>当前主流程：create 后直接 APPROVED，患者 pay，药房 dispense / complete。
 * submit / review 保留用于历史 DRAFT / PENDING_REVIEW 处方兼容处理。
 */
public interface PrescriptionService {

    /** 开方（初始 APPROVED，写主表 + 明细；drugNo 非空时供调剂发药扣库存使用） */
    PrescriptionDTO.CreateResponse create(PrescriptionDTO.CreateRequest req);

    /** 处方列表（可按 status 过滤，患者处方页 / 药房发药工作台） */
    PageResult<PrescriptionDTO.ListItem> list(int page, int pageSize, String status);

    /** 处方详情（含明细） */
    PrescriptionDTO.DetailResponse detail(String prescriptionNo);

    /** 提交审方（DRAFT → PENDING_REVIEW） */
    PrescriptionDTO.SubmitResponse submit(String prescriptionNo);

    /** 历史兼容：审方（PENDING_REVIEW → APPROVED | REJECTED，Redis 锁内防并发） */
    PrescriptionDTO.ReviewResponse review(String prescriptionNo, PrescriptionDTO.ReviewRequest req);

    /** 缴费（APPROVED → PAID） */
    PrescriptionDTO.PayResponse pay(String prescriptionNo, PrescriptionDTO.PayRequest req);

    /**
     * 调剂发药（APPROVED/PAID → DISPENSED，同步 Feign 调 drug-service FEFO 出库）。
     * <p>架构文档 §6.2 原写 MQ 异步，本批务实采用同步 Feign（MQ 化为后续待办）。
     */
    PrescriptionDTO.DispenseResponse dispense(String prescriptionNo, PrescriptionDTO.DispenseRequest req);

    /** 完成（DISPENSED → COMPLETED） */
    PrescriptionDTO.CompleteResponse complete(String prescriptionNo);

    /** 退方（APPROVED/PAID → CANCELLED） */
    PrescriptionDTO.CancelResponse cancel(String prescriptionNo, PrescriptionDTO.CancelRequest req);

    /** 按编号取实体，未找到抛 NOT_FOUND */
    Prescription requireByNo(String prescriptionNo);
}
