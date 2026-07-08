package com.medconsult.medicalrecord.prescription.service;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.medicalrecord.prescription.dto.PrescriptionDTO;
import com.medconsult.medicalrecord.prescription.entity.Prescription;
import com.medconsult.medicalrecord.prescription.mapper.PrescriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 处方审方事务体（架构文档 §7.1 锁的事务层，仿 outpatient AppointmentTxService）。
 *
 * <p>独立 Bean 避免 self-injection 循环依赖：{@link PrescriptionServiceImpl} 在 Redis 锁内
 * 调用本类的 {@link #reviewInTx}，保证"持锁期间事务已提交"。
 *
 * <p>事务边界与锁边界对齐：加锁 → 开事务 → 状态校验 + 状态转移 → 提交事务 → 释放锁。
 * 其他线程（并发审方的另一药师）在锁释放后才能读到已提交的新状态，避免重复审方。
 *
 * <p><b>审方状态机</b>：PENDING_REVIEW → APPROVED | REJECTED。
 * 锁内重查状态防并发：若已被另一药师审过（非 PENDING_REVIEW），抛 CONFLICT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionTxService {

    private final PrescriptionMapper prescriptionMapper;

    /**
     * 审方事务体（锁内执行）。
     *
     * @param p        锁外解析的处方实体（仅用于拿 id；状态以锁内重查为准）
     * @param action   APPROVE / REJECT
     * @param pharmacistId 药师编号（业务编号，落库存正哈希）
     * @param reviewComment 审方意见
     * @param rejectReason  驳回原因（REJECT 时校验非空）
     */
    @Transactional
    public PrescriptionDTO.ReviewResponse reviewInTx(Prescription p, String action,
                                                      String pharmacistId,
                                                      String reviewComment,
                                                      String rejectReason) {
        // 锁内重查最新状态（防并发：另一药师可能已审过）
        Prescription fresh = prescriptionMapper.selectById(p.getId());
        if (fresh == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "处方不存在: " + p.getPrescriptionNo());
        }
        if (!"PENDING_REVIEW".equals(fresh.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方不在待审状态，当前状态: " + fresh.getStatus() + "（可能已被其他药师审过）");
        }

        // REJECT 必须有驳回原因
        if ("REJECT".equals(action) && (rejectReason == null || rejectReason.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "驳回必须填写驳回原因");
        }

        String newStatus = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        fresh.setStatus(newStatus);
        fresh.setPharmacyPharmacistId(positiveHash(pharmacistId));
        fresh.setReviewedAt(LocalDateTime.now());
        if (reviewComment != null) {
            fresh.setReviewComment(reviewComment);
        }
        if ("REJECTED".equals(newStatus)) {
            fresh.setRejectReason(rejectReason);
        }
        prescriptionMapper.updateById(fresh);

        log.info("处方审方: prescriptionNo={} {} → {} pharmacist={}",
                fresh.getPrescriptionNo(), "PENDING_REVIEW", newStatus, pharmacistId);
        return new PrescriptionDTO.ReviewResponse(fresh.getPrescriptionNo(), newStatus, fresh.getReviewedAt());
    }

    /** 业务编号 → 正哈希 Long（与病历域同款策略） */
    private static long positiveHash(String businessNo) {
        if (businessNo == null) {
            return 0L;
        }
        long h = businessNo.hashCode();
        return h == Long.MIN_VALUE ? 0L : Math.abs(h);
    }
}
