package com.medconsult.drug.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.drug.dto.DrugDTO;
import com.medconsult.drug.entity.Drug;
import com.medconsult.drug.entity.DrugStockBatch;
import com.medconsult.drug.entity.DrugStockFlow;
import com.medconsult.drug.mapper.DrugMapper;
import com.medconsult.drug.mapper.DrugStockBatchMapper;
import com.medconsult.drug.mapper.DrugStockFlowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 药品库存事务体（架构文档 §7.1 库存扣减锁的事务层，仿 outpatient AppointmentTxService）。
 *
 * <p>独立 Bean 避免 self-injection 循环依赖：{@link DrugServiceImpl} 在 Redis 锁内
 * 调用本类的事务方法（{@link #inboundInTx} / {@link #outboundInTx}），保证"持锁期间事务已提交"。
 *
 * <p>事务边界与锁边界对齐：加锁 → 开事务 → DB 操作 → 提交事务 → 释放锁。
 * 其他线程在锁释放后才能读到已提交的 current_stock，避免超卖。
 *
 * <p><b>FEFO 核心</b>（《需求文档》§4.1.5 规则 2、规则 4）：
 * <ol>
 *   <li>查 status=AVAILABLE AND expire_date &gt;= CURDATE() AND quantity &gt; 0 的批次</li>
 *   <li>按 expire_date ASC 排序（FOR UPDATE 行锁防并发读旧值）</li>
 *   <li>按顺序扣减各 batch.quantity，累计到请求 quantity</li>
 *   <li>不足（跨所有批次）抛 CONFLICT「库存不足」</li>
 *   <li>每个被扣减的批次各写一条 OUTBOUND flow（before/after quantity 记录）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugStockTxService {

    private final DrugMapper drugMapper;
    private final DrugStockBatchMapper batchMapper;
    private final DrugStockFlowMapper flowMapper;

    // ===== 入库事务体（锁内）=====

    /**
     * 入库事务体（锁内执行）。
     *
     * <p>步骤：
     * <ol>
     *   <li>锁内重查 drug 最新值，校验 status=ACTIVE（DISABLED 不可入库）</li>
     *   <li>同 batchNo 则累加 quantity；否则新建批次</li>
     *   <li>batch.status 根据 expire_date 判断（&lt; 今日则 EXPIRED，入库仍记库存但不参与出库）</li>
     *   <li>drug.current_stock += quantity</li>
     *   <li>写一条 INBOUND flow（before/after quantity）</li>
     * </ol>
     *
     * @param drugId    药品主键（锁外解析）
     * @param drugNo    药品编号（flow 记录用 drugId）
     * @param req       入库请求
     * @return 入库响应（含 flow_no / currentStock）
     */
    @Transactional
    public DrugDTO.InboundResponse inboundInTx(Long drugId, String drugNo, DrugDTO.InboundRequest req) {
        Drug drug = drugMapper.selectById(drugId);
        if (drug == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + drugNo);
        }
        if (!"ACTIVE".equals(drug.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "药品已停用，不可入库: " + drugNo);
        }

        int qty = req.getQuantity();
        LocalDate today = LocalDate.now();

        // 同 batchNo 则累加 quantity，否则新建批次
        DrugStockBatch batch = batchMapper.selectOne(
                new QueryWrapper<DrugStockBatch>().eq("batch_no", req.getBatchNo()));
        if (batch != null) {
            // 批次存在：累加数量，更新有效期/单价/供应商（以最新入库为准）
            int newQty = (batch.getQuantity() == null ? 0 : batch.getQuantity()) + qty;
            batch.setQuantity(newQty);
            batch.setExpireDate(req.getExpireDate());
            if (req.getUnitPrice() != null) {
                batch.setUnitPrice(req.getUnitPrice());
            }
            if (req.getSupplier() != null) {
                batch.setSupplier(req.getSupplier());
            }
            if (req.getProductionDate() != null) {
                batch.setProductionDate(req.getProductionDate());
            }
            batch.setStatus(decideBatchStatus(req.getExpireDate(), today));
            batchMapper.updateById(batch);
        } else {
            batch = new DrugStockBatch();
            batch.setDrugId(drugId);
            batch.setBatchNo(req.getBatchNo());
            batch.setQuantity(qty);
            batch.setUnitPrice(req.getUnitPrice());
            batch.setProductionDate(req.getProductionDate());
            batch.setExpireDate(req.getExpireDate());
            batch.setSupplier(req.getSupplier());
            batch.setStatus(decideBatchStatus(req.getExpireDate(), today));
            batchMapper.insert(batch);
        }

        // 更新 drug.current_stock
        int before = drug.getCurrentStock() == null ? 0 : drug.getCurrentStock();
        int after = before + qty;
        drug.setCurrentStock(after);
        drugMapper.updateById(drug);

        // 写 INBOUND flow（流水表只追加）
        DrugStockFlow flow = new DrugStockFlow();
        flow.setFlowNo(generateFlowNo());
        flow.setDrugId(drugId);
        flow.setBatchId(batch.getId());
        flow.setType("INBOUND");
        flow.setQuantity(qty);
        flow.setBeforeQuantity(before);
        flow.setAfterQuantity(after);
        flow.setRemark("入库 批次=" + req.getBatchNo());
        flow.setCreatedAt(java.time.LocalDateTime.now());
        flowMapper.insert(flow);

        log.info("药品入库: drugNo={} batchNo={} qty={} stock {}->{} flowNo={}",
                drugNo, req.getBatchNo(), qty, before, after, flow.getFlowNo());
        return new DrugDTO.InboundResponse(flow.getFlowNo(), drugNo, after);
    }

    // ===== 出库事务体（锁内，FEFO 核心）=====

    /**
     * 出库事务体（锁内执行，FEFO 选批扣减）。
     *
     * <p>步骤见类注释。多批次出库时，每个被扣减的批次各写一条 OUTBOUND flow，
     * before/after 记录该药品的 current_stock 连续变化。
     *
     * @param drugId    药品主键
     * @param drugNo    药品编号
     * @param req       出库请求（含 quantity / batchStrategy / prescription 溯源）
     * @return 出库响应（首条 flow_no / currentStock）
     */
    @Transactional
    public DrugDTO.OutboundResponse outboundInTx(Long drugId, String drugNo, DrugDTO.OutboundRequest req) {
        Drug drug = drugMapper.selectById(drugId);
        if (drug == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + drugNo);
        }
        int qty = req.getQuantity();
        int current = drug.getCurrentStock() == null ? 0 : drug.getCurrentStock();
        // 先按药品总量快速校验（不足直接拒绝，避免无谓查询）
        if (current < qty) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "库存不足: 当前=" + current + " 请求=" + qty);
        }

        // FEFO 选批：AVAILABLE 且未过期且 quantity>0，按 expire_date ASC
        // FOR UPDATE 行锁（MySQL）：防并发读到旧 quantity 导致超扣。H2 不支持 FOR UPDATE 但 MOCK 下无并发。
        DrugDTO.BatchStrategy strategy = req.getBatchStrategy() == null
                ? DrugDTO.BatchStrategy.FEFO : req.getBatchStrategy();
        QueryWrapper<DrugStockBatch> bw = new QueryWrapper<DrugStockBatch>()
                .eq("drug_id", drugId)
                .eq("status", "AVAILABLE")
                .ge("expire_date", LocalDate.now())
                .gt("quantity", 0);
        if (strategy == DrugDTO.BatchStrategy.FEFO) {
            bw.orderByAsc("expire_date");
        } else if (strategy == DrugDTO.BatchStrategy.FIFO) {
            bw.orderByAsc("created_at");
        } else {
            // MANUAL 暂不支持（业务上医疗强制 FEFO，手工选批需后续扩展指定 batchId）
            throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持的选批策略: " + strategy);
        }
        bw.last("FOR UPDATE");
        List<DrugStockBatch> batches = batchMapper.selectList(bw);

        // 按 FEFO 顺序扣减各批次，累计到 qty
        int remaining = qty;
        int runningStock = current;
        String firstFlowNo = null;
        for (DrugStockBatch b : batches) {
            if (remaining <= 0) {
                break;
            }
            int avail = b.getQuantity();
            if (avail <= 0) {
                continue;
            }
            int take = Math.min(avail, remaining);
            b.setQuantity(avail - take);
            batchMapper.updateById(b);

            int before = runningStock;
            int after = runningStock - take;
            runningStock = after;

            DrugStockFlow flow = new DrugStockFlow();
            flow.setFlowNo(generateFlowNo());
            flow.setDrugId(drugId);
            flow.setBatchId(b.getId());
            flow.setType("OUTBOUND");
            flow.setQuantity(take);
            flow.setBeforeQuantity(before);
            flow.setAfterQuantity(after);
            // 处方出库溯源（修订项 §5.1）
            flow.setRelatedRecordId(parseLong(req.getRelatedRecordId()));
            flow.setPrescriptionId(req.getPrescriptionId());
            flow.setPrescriptionItemId(req.getPrescriptionItemId());
            flow.setRemark("出库 批次=" + b.getBatchNo() + " 用途=" + req.getPurpose());
            flow.setCreatedAt(java.time.LocalDateTime.now());
            flowMapper.insert(flow);
            if (firstFlowNo == null) {
                firstFlowNo = flow.getFlowNo();
            }

            remaining -= take;
            log.info("FEFO 扣减批次: drugNo={} batchNo={} expire={} take={} batchRemain={}",
                    drugNo, b.getBatchNo(), b.getExpireDate(), take, b.getQuantity());
        }

        // 跨所有批次仍不足（理论上前面的 current 校验应已拦截，此处为兜底，防并发扣减后可用批次变少）
        if (remaining > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "可用批次库存不足: 缺口=" + remaining + "（可能存在并发扣减）");
        }

        // 更新 drug.current_stock
        drug.setCurrentStock(runningStock);
        drugMapper.updateById(drug);

        log.info("药品出库: drugNo={} qty={} stock {}->{} firstFlowNo={}",
                drugNo, qty, current, runningStock, firstFlowNo);
        return new DrugDTO.OutboundResponse(firstFlowNo, drugNo, runningStock);
    }

    // ===== 私有助手 =====

    /**
     * 根据有效期判断批次状态：过期则 EXPIRED，否则 AVAILABLE。
     * （入库时即使已过期也记录，但不参与出库——出库 SQL 过滤了 status=AVAILABLE 且 expire_date>=today）
     */
    private String decideBatchStatus(LocalDate expireDate, LocalDate today) {
        if (expireDate == null) {
            return "AVAILABLE";
        }
        return expireDate.isBefore(today) ? "EXPIRED" : "AVAILABLE";
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 生成流水编号：SF + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_flow_no 兜底 */
    private static String generateFlowNo() {
        long id = IdWorker.getId();
        return "SF" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
