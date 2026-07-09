package com.medconsult.medicalrecord.prescription.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.redis.DistributedLock;
import com.medconsult.common.redis.RedisKey;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 处方服务实现（对齐《修改建议》§2.1 + 架构文档 §6.2 状态机）。
 *
 * <p>核心逻辑（第 1 批）：
 * <ul>
 *   <li>开方 create：prescriptionNo = RX + 雪花 base36；recordId/patientId/doctorId/departmentId
 *       用业务编号正哈希落库（同病历域策略）；status 初始 DRAFT；paymentStatus 初始 UNPAID；
 *       遍历明细累加 totalFee（无单价的按 0 计）；明细 drugId 本批存 null（快照名展示）</li>
 *   <li>列表 list：可按 status 过滤，分页</li>
 *   <li>详情 detail：主表 + 明细（批量查 by prescription_id）</li>
 *   <li>提交审方 submit：DRAFT → PENDING_REVIEW（状态校验）</li>
 *   <li>审方 review：PENDING_REVIEW → APPROVED | REJECTED；Redis 锁包裹 TxService 事务体防并发</li>
 * </ul>
 *
 * <p><b>锁 key 引用 RedisKey 常量</b>（改进点：outpatient 的 AppointmentServiceImpl 硬编码了字符串，
 * 本服务直接用 {@link RedisKey#PRESCRIPTION_LOCK}，避免常量漂移）。
 *
 * <p>事务体独立到 {@link PrescriptionTxService} 避免自注入循环依赖：本类负责锁 + 非事务查询/状态校验，
 * txService 负责锁内 @Transactional 的状态转移写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper itemMapper;
    private final DistributedLock distributedLock;
    /** 锁内事务体（独立 Bean，避免 self-injection 循环依赖） */
    private final PrescriptionTxService txService;

    /** 审方/缴费/完成/退方锁租约：5s（只改主表状态，单次 DB 操作） */
    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);

    /** 调剂发药锁租约：20s（锁内逐条同步 Feign 调 drug-service 出库，多明细时耗时更长） */
    private static final Duration DISPENSE_LOCK_LEASE = Duration.ofSeconds(20);

    // ===== 开方 =====

    @Override
    @Transactional
    public PrescriptionDTO.CreateResponse create(PrescriptionDTO.CreateRequest req) {
        Prescription p = new Prescription();
        p.setPrescriptionNo(generatePrescriptionNo());
        p.setRecordId(positiveHash(req.getRecordId()));
        p.setPatientId(positiveHash(req.getPatientId()));
        p.setDoctorId(positiveHash(req.getDoctorId()));
        if (req.getDepartmentId() != null && !req.getDepartmentId().isBlank()) {
            p.setDepartmentId(positiveHash(req.getDepartmentId()));
        }
        p.setStatus("DRAFT");
        p.setPaymentStatus("UNPAID");
        p.setSource(req.getSource() == null || req.getSource().isBlank() ? "OUTPATIENT" : req.getSource());

        // 插入主表先拿到 id
        prescriptionMapper.insert(p);

        // 遍历明细：累加 totalFee + 落库明细
        BigDecimal totalFee = BigDecimal.ZERO;
        for (PrescriptionDTO.ItemRequest item : req.getItems()) {
            PrescriptionItem pi = new PrescriptionItem();
            pi.setPrescriptionId(p.getId());
            // drug_id 保持 null（预留）；drugNo 非空时存 drug_no，dispense 时用它调 drug-service
            pi.setDrugNo(item.getDrugNo());
            pi.setDrugNameSnapshot(item.getDrugName());
            pi.setSpecificationSnapshot(item.getSpecification());
            pi.setDosage(item.getDosage());
            pi.setFrequency(item.getFrequency());
            pi.setRoute(item.getRoute());
            pi.setDays(item.getDays());
            pi.setQuantity(item.getQuantity());
            pi.setUnit(item.getUnit());
            BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            pi.setUnitPrice(unitPrice);
            BigDecimal qty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
            BigDecimal subtotal = unitPrice.multiply(qty);
            pi.setSubtotal(subtotal);
            itemMapper.insert(pi);
            totalFee = totalFee.add(subtotal);
        }
        // 回填 totalFee
        p.setTotalFee(totalFee);
        prescriptionMapper.updateById(p);

        log.info("处方创建: prescriptionNo={} items={} totalFee={}",
                p.getPrescriptionNo(), req.getItems().size(), totalFee);
        return new PrescriptionDTO.CreateResponse(p.getPrescriptionNo(), p.getStatus(), totalFee);
    }

    // ===== 列表 =====

    @Override
    public PageResult<PrescriptionDTO.ListItem> list(int page, int pageSize, String status) {
        Page<Prescription> p = new Page<>(page <= 0 ? 1 : page, pageSize <= 0 ? 10 : pageSize);
        QueryWrapper<Prescription> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        qw.orderByDesc("created_at");
        IPage<Prescription> result = prescriptionMapper.selectPage(p, qw);
        List<PrescriptionDTO.ListItem> items = new ArrayList<>();
        for (Prescription rx : result.getRecords()) {
            items.add(new PrescriptionDTO.ListItem(
                    rx.getPrescriptionNo(),
                    null,                 // recordId 本批无 Feign 反查，留 null
                    rx.getStatus(),
                    rx.getTotalFee(),
                    rx.getPaymentStatus(),
                    rx.getCreatedAt()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== 详情 =====

    @Override
    public PrescriptionDTO.DetailResponse detail(String prescriptionNo) {
        Prescription p = requireByNo(prescriptionNo);
        // 查明细
        List<PrescriptionItem> items = itemMapper.selectList(
                new QueryWrapper<PrescriptionItem>().eq("prescription_id", p.getId()));
        List<PrescriptionDTO.ItemResponse> itemDtos = new ArrayList<>();
        for (PrescriptionItem it : items) {
            itemDtos.add(new PrescriptionDTO.ItemResponse(
                    it.getDrugNameSnapshot(),
                    it.getSpecificationSnapshot(),
                    it.getDosage(),
                    it.getFrequency(),
                    it.getRoute(),
                    it.getDays(),
                    it.getQuantity(),
                    it.getUnit(),
                    it.getUnitPrice(),
                    it.getSubtotal()));
        }
        return new PrescriptionDTO.DetailResponse(
                p.getPrescriptionNo(),
                p.getStatus(),
                p.getSource(),
                p.getTotalFee(),
                p.getPaymentStatus(),
                p.getPharmacyPharmacistId(),
                p.getReviewedAt(),
                p.getReviewComment(),
                p.getRejectReason(),
                p.getCreatedAt(),
                itemDtos);
    }

    // ===== 提交审方 =====

    @Override
    @Transactional
    public PrescriptionDTO.SubmitResponse submit(String prescriptionNo) {
        Prescription p = requireByNo(prescriptionNo);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "仅草稿处方可提交审方，当前状态: " + p.getStatus());
        }
        p.setStatus("PENDING_REVIEW");
        prescriptionMapper.updateById(p);
        log.info("处方提交审方: prescriptionNo={} DRAFT → PENDING_REVIEW", prescriptionNo);
        return new PrescriptionDTO.SubmitResponse(p.getPrescriptionNo(), p.getStatus());
    }

    // ===== 审方（Redis 锁内事务体）=====

    @Override
    public PrescriptionDTO.ReviewResponse review(String prescriptionNo, PrescriptionDTO.ReviewRequest req) {
        Prescription p = requireByNo(prescriptionNo);
        // 锁前快速校验：非 PENDING_REVIEW 直接拒绝，避免无谓抢锁
        if (!"PENDING_REVIEW".equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方不在待审状态，当前状态: " + p.getStatus());
        }
        // action 非空校验（@Pattern 允许 null，此处兜底）
        String action = req.getAction();
        if (action == null || action.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审方动作不能为空（APPROVE/REJECT）");
        }
        String lockKey = RedisKey.PRESCRIPTION_LOCK + p.getId();
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.reviewInTx(p, action, req.getPharmacistId(),
                            req.getReviewComment(), req.getRejectReason()));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方正在被其他药师审方，请稍后重试: " + prescriptionNo);
        }
    }

    // ===== 缴费（APPROVED → PAID，锁内防与 dispense/cancel 竞态）=====

    @Override
    public PrescriptionDTO.PayResponse pay(String prescriptionNo, PrescriptionDTO.PayRequest req) {
        Prescription p = requireByNo(prescriptionNo);
        // 锁前快速校验，避免无谓抢锁
        if (!"APPROVED".equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "仅已审通过处方可缴费，当前状态: " + p.getStatus());
        }
        String lockKey = RedisKey.PRESCRIPTION_LOCK + p.getId();
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.payInTx(p, req));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方操作繁忙，请稍后重试: " + prescriptionNo);
        }
    }

    // ===== 调剂发药（APPROVED/PAID → DISPENSED，同步 Feign）=====

    @Override
    public PrescriptionDTO.DispenseResponse dispense(String prescriptionNo, PrescriptionDTO.DispenseRequest req) {
        Prescription p = requireByNo(prescriptionNo);
        // 状态校验：APPROVED 或 PAID 可调剂（架构文档 §6.2：APPROVED/PAID ──dispense──▶ DISPENSED）
        String st = p.getStatus();
        if (!"APPROVED".equals(st) && !"PAID".equals(st)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方不可调剂，当前状态: " + st + "（须 APPROVED 或 PAID）");
        }
        String lockKey = RedisKey.PRESCRIPTION_LOCK + p.getId();
        try {
            return distributedLock.withLock(lockKey, DISPENSE_LOCK_LEASE,
                    () -> txService.dispenseInTx(p, req));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方正在被调剂，请稍后重试: " + prescriptionNo);
        }
    }

    // ===== 完成（DISPENSED → COMPLETED，锁内防与 dispense 竞态）=====

    @Override
    public PrescriptionDTO.CompleteResponse complete(String prescriptionNo) {
        Prescription p = requireByNo(prescriptionNo);
        if (!"DISPENSED".equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "仅已调剂处方可完成，当前状态: " + p.getStatus());
        }
        String lockKey = RedisKey.PRESCRIPTION_LOCK + p.getId();
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.completeInTx(p));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方操作繁忙，请稍后重试: " + prescriptionNo);
        }
    }

    // ===== 退方（APPROVED/PAID → CANCELLED，锁内防与 dispense 竞态）=====

    @Override
    public PrescriptionDTO.CancelResponse cancel(String prescriptionNo, PrescriptionDTO.CancelRequest req) {
        Prescription p = requireByNo(prescriptionNo);
        String st = p.getStatus();
        if (!"APPROVED".equals(st) && !"PAID".equals(st)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方当前状态不可退方: " + st + "（仅 APPROVED/PAID 可退；已调剂须走退药流程）");
        }
        String lockKey = RedisKey.PRESCRIPTION_LOCK + p.getId();
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.cancelInTx(p, req));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "处方操作繁忙，请稍后重试: " + prescriptionNo);
        }
    }

    // ===== 内部校验 =====

    @Override
    public Prescription requireByNo(String prescriptionNo) {
        if (prescriptionNo == null || prescriptionNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "处方编号不能为空");
        }
        Prescription p = prescriptionMapper.selectOne(
                new QueryWrapper<Prescription>().eq("prescription_no", prescriptionNo));
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "处方不存在: " + prescriptionNo);
        }
        return p;
    }

    // ===== 私有助手 =====

    /** 业务编号 → 正哈希 Long（与病历域同款策略） */
    private static long positiveHash(String businessNo) {
        if (businessNo == null) {
            return 0L;
        }
        long h = businessNo.hashCode();
        return h == Long.MIN_VALUE ? 0L : Math.abs(h);
    }

    /** 生成处方编号：RX + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_prescription_no 兜底 */
    private static String generatePrescriptionNo() {
        long id = IdWorker.getId();
        return "RX" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
