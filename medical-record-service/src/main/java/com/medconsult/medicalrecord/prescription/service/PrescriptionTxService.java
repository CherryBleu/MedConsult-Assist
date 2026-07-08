package com.medconsult.medicalrecord.prescription.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DrugFeignClient;
import com.medconsult.common.feign.dto.DispenseDTO;
import com.medconsult.medicalrecord.prescription.dto.PrescriptionDTO;
import com.medconsult.medicalrecord.prescription.entity.Prescription;
import com.medconsult.medicalrecord.prescription.entity.PrescriptionItem;
import com.medconsult.medicalrecord.prescription.mapper.PrescriptionItemMapper;
import com.medconsult.medicalrecord.prescription.mapper.PrescriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final PrescriptionItemMapper itemMapper;
    private final DrugFeignClient drugFeignClient;

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

    // ===== 调剂发药事务体（锁内，同步 Feign 逐条扣减）=====

    /**
     * 调剂发药事务体（锁内执行，架构文档 §6.2）。
     *
     * <p>步骤：
     * <ol>
     *   <li>锁内重查处方状态（防并发：另一药师可能已调剂）</li>
     *   <li>查明细列表，逐条处理：
     *     <ul>
     *       <li>解析 drugNo：优先 item.drugNo，其次 DispenseRequest.itemDrugNoMap（兼容历史 drug_no=null）</li>
     *       <li>quantity BigDecimal → int（小数拒绝 PARAM_ERROR，药品库存按整数件）</li>
     *       <li>Feign 调 drug-service POST /internal/drugs/{drugNo}/outbound（FEFO 扣减）</li>
     *       <li>成功 → 回写 item.dispensedQuantity + 返回 flowNo</li>
     *       <li>失败（库存不足 CONFLICT / 药品不存在 NOT_FOUND）→ 抛异常，整事务回滚</li>
     *     </ul>
     *   </li>
     *   <li>全部扣减成功 → 处方状态 → DISPENSED</li>
     * </ol>
     *
     * <p><b>⚠️ 同步 Feign 跨服务事务限制</b>：drug-service 的 outbound 是独立事务且已提交，
     * 本方法事务回滚<b>无法回滚</b> drug 那边的扣减。缓解：扣减前不做批量预校验（库存实时变化，
     * 预校验也无意义），而是接受"部分扣减后失败需人工对账"的现实。彻底方案是改 MQ 异步（架构文档
     * §6.2 待办），drug-service 消费失败时不提交。本批在代码注释和架构文档明确此限制。
     *
     * @param p   锁外解析的处方（状态以锁内重查为准）
     * @param req 调剂请求（含 pharmacistId + 可选 itemDrugNoMap）
     */
    @Transactional
    public PrescriptionDTO.DispenseResponse dispenseInTx(Prescription p, PrescriptionDTO.DispenseRequest req) {
        // 锁内重查最新状态
        Prescription fresh = prescriptionMapper.selectById(p.getId());
        if (fresh == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "处方不存在: " + p.getPrescriptionNo());
        }
        String curStatus = fresh.getStatus();
        if (!"APPROVED".equals(curStatus) && !"PAID".equals(curStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方不可调剂，当前状态: " + curStatus + "（可能已被其他药师调剂）");
        }

        // 查明细
        List<PrescriptionItem> items = itemMapper.selectList(
                new QueryWrapper<PrescriptionItem>().eq("prescription_id", fresh.getId()));
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "处方无明细，不可调剂: " + fresh.getPrescriptionNo());
        }

        Map<String, String> drugNoMap = req.getItemDrugNoMap();
        List<PrescriptionDTO.DispenseItem> dispenseItems = new ArrayList<>();

        // 逐条扣减
        for (PrescriptionItem item : items) {
            // 解析 drugNo：优先 item.drugNo，其次前端补传的 map（兼容历史数据）
            String drugNo = item.getDrugNo();
            if ((drugNo == null || drugNo.isBlank()) && drugNoMap != null) {
                drugNo = drugNoMap.get(String.valueOf(item.getId()));
            }
            if (drugNo == null || drugNo.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "处方明细缺少药品编号，无法调剂: itemId=" + item.getId()
                                + " drugName=" + item.getDrugNameSnapshot()
                                + "（历史处方须通过 itemDrugNoMap 补传）");
            }

            // quantity BigDecimal → int（拒绝小数）
            BigDecimal qty = item.getQuantity();
            if (qty == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "处方明细数量为空: itemId=" + item.getId());
            }
            int intQty = toIntQuantity(qty, item.getDrugNameSnapshot());

            // Feign 调 drug-service FEFO 出库（库存不足抛 CONFLICT，药品不存在抛 NOT_FOUND，由 FeignErrorDecoder 转）
            DispenseDTO.OutboundRequest outReq = DispenseDTO.OutboundRequest.forDispense(
                    intQty, fresh.getId(), item.getId());
            Result<DispenseDTO.OutboundResponse> outResp;
            try {
                outResp = drugFeignClient.outbound(drugNo, outReq);
            } catch (BusinessException be) {
                // 下游业务错误（库存不足等），附药品名便于药师定位
                throw new BusinessException(be.getErrorCode(),
                        "调剂扣减失败 [" + item.getDrugNameSnapshot() + "(" + drugNo + ")]: " + be.getMessage());
            }
            DispenseDTO.OutboundResponse outData = outResp == null ? null : outResp.data();

            // 回写明细已发数量
            item.setDispensedQuantity(BigDecimal.valueOf(intQty));
            itemMapper.updateById(item);

            dispenseItems.add(new PrescriptionDTO.DispenseItem(
                    item.getId(),
                    item.getDrugNameSnapshot(),
                    drugNo,
                    BigDecimal.valueOf(intQty),
                    outData == null ? null : outData.stockFlowId()));
            log.info("调剂扣减: prescriptionNo={} itemId={} drugNo={} qty={} flowNo={}",
                    fresh.getPrescriptionNo(), item.getId(), drugNo, intQty,
                    outData == null ? null : outData.stockFlowId());
        }

        // 全部扣减成功 → 处方状态 DISPENSED
        fresh.setStatus("DISPENSED");
        fresh.setPharmacyPharmacistId(positiveHash(req.getPharmacistId()));
        prescriptionMapper.updateById(fresh);

        log.info("处方调剂完成: prescriptionNo={} → DISPENSED items={}",
                fresh.getPrescriptionNo(), dispenseItems.size());
        return new PrescriptionDTO.DispenseResponse(
                fresh.getPrescriptionNo(), fresh.getStatus(), LocalDateTime.now(), dispenseItems);
    }

    /**
     * BigDecimal 数量 → int（药品库存按整数件）。
     * 小数拒绝（如 1.5 片）：PARAM_ERROR。
     */
    private static int toIntQuantity(BigDecimal qty, String drugName) {
        if (qty.scale() > 0 && qty.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "药品数量须为整数（库存按件管理）: " + drugName + " 数量=" + qty);
        }
        return qty.intValueExact();
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
