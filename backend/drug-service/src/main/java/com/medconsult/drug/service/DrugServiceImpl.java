package com.medconsult.drug.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.feign.dto.DrugRiskBatchResponse;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import com.medconsult.common.mq.audit.AuditLog;
import com.medconsult.common.redis.DistributedLock;
import com.medconsult.common.redis.RedisKey;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药品库存服务实现（对齐《需求文档》§4.1.5 / 《接口文档》§2.7 / 架构文档 §7.1）。
 *
 * <p>核心逻辑：
 * <ul>
 *   <li>创建药品：D + 雪花 ID base36（仿 outpatient scheduleNo / patient patientNo），
 *       current_stock 初始 0，contraindications/interactions 序列化为 JSON 串入库</li>
 *   <li>入库 / 出库：Redis 分布式锁 lock:drug:{drugId}:stock（租约 10s）内，
 *       委托 {@link DrugStockTxService} 执行事务体（FEFO 选批扣减）</li>
 *   <li>预警：LOW_STOCK（current_stock &lt; min_stock_threshold）/ NEAR_EXPIRY
 *       （批次 expire_date 在 30 天内且 quantity&gt;0，阈值硬编码，TODO 配置化）</li>
 *   <li>内部 getRiskInfo：解析 JSON 串组装 DrugRiskInfoDTO；内部 ffeoBatches：返回 FEFO 排序批次（不扣减）</li>
 * </ul>
 *
 * <p><b>库存锁关键</b>（架构文档 §7.1，红线）：必须用 Redis 分布式锁，不用 JVM synchronized
 * （多实例失效）。锁包裹事务方法，保证"持锁期间事务已提交"。
 *
 * <p>事务体独立到 {@link DrugStockTxService} 避免自注入循环依赖：本类负责锁 + 非事务查询，
 * txService 负责锁内 @Transactional 的 DB 写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugServiceImpl implements DrugService {

    private final DrugMapper drugMapper;
    private final DrugStockBatchMapper batchMapper;
    private final DrugStockFlowMapper flowMapper;
    private final DistributedLock distributedLock;
    /** 锁内事务体（独立 Bean，避免 self-injection 循环依赖） */
    private final DrugStockTxService txService;
    private final ObjectMapper objectMapper;

    /** 分布式锁租约：10s（库存事务含多批次扣减，比抢号 5s 略长） */
    private static final Duration LOCK_LEASE = Duration.ofSeconds(10);

    /** 近效期预警阈值：30 天（硬编码，TODO 后续配置化到 drug-service.yaml） */
    private static final int NEAR_EXPIRY_DAYS = 30;

    // ===== §2.7.1 创建药品 =====

    @Override
    @Transactional
    @AuditLog(
            resourceType = "DRUG",
            action = "CREATE",
            resourceId = "#result.drugId()",
            detail = "'status=' + #result.status()")
    public DrugDTO.DrugSummary createDrug(DrugDTO.CreateDrugRequest req) {
        Drug d = new Drug();
        d.setDrugNo(generateDrugNo());
        d.setGenericName(req.getGenericName());
        d.setTradeName(req.getTradeName());
        d.setSpecification(req.getSpecification());
        d.setDosageForm(req.getDosageForm());
        d.setManufacturer(req.getManufacturer());
        d.setApprovalNo(req.getApprovalNo());
        d.setUnit(req.getUnit());
        d.setMinStockThreshold(req.getMinStockThreshold());
        // 禁忌 / 相互作用序列化为 JSON 串入库（结构化为 [{condition,level,note}] / [{drugCode,effect,level}]）
        d.setContraindications(toContraindicationsJson(req.getContraindications()));
        d.setInteractions(toInteractionsJson(req.getInteractions()));
        d.setCurrentStock(0); // 初始库存 0，入库后才增加
        d.setStatus("ACTIVE");
        drugMapper.insert(d);
        log.info("药品创建: drugNo={} genericName={}", d.getDrugNo(), d.getGenericName());
        return new DrugDTO.DrugSummary(d.getDrugNo(), d.getGenericName(), d.getStatus());
    }

    // ===== §2.7.2 查询药品列表 =====

    @Override
    public PageResult<DrugDTO.DrugListItem> listDrugs(int page, int pageSize, String keyword) {
        Page<Drug> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<Drug> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 模糊匹配通用名 / 商品名
            qw.and(w -> w.like("generic_name", keyword).or().like("trade_name", keyword));
        }
        qw.orderByDesc("created_at");
        IPage<Drug> result = drugMapper.selectPage(p, qw);
        List<DrugDTO.DrugListItem> items = new ArrayList<>();
        for (Drug d : result.getRecords()) {
            items.add(new DrugDTO.DrugListItem(
                    d.getDrugNo(),
                    d.getGenericName(),
                    d.getSpecification(),
                    d.getManufacturer(),
                    d.getCurrentStock() == null ? 0 : d.getCurrentStock(),
                    d.getUnit(),
                    d.getStatus()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.7.3 入库 =====

    @Override
    public DrugDTO.InboundResponse inbound(String drugNo, DrugDTO.InboundRequest req) {
        Drug d = requireByDrugNo(drugNo);
        String lockKey = RedisKey.DRUG_STOCK_LOCK + d.getId();
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.inboundInTx(d.getId(), drugNo, req));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "库存操作繁忙，请稍后重试: " + drugNo);
        }
    }

    // ===== §2.7.4 出库（FEFO）=====

    @Override
    public DrugDTO.OutboundResponse outbound(String drugNo, DrugDTO.OutboundRequest req) {
        Drug d = requireByDrugNo(drugNo);
        String lockKey = RedisKey.DRUG_STOCK_LOCK + d.getId();
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.outboundInTx(d.getId(), drugNo, req));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "库存操作繁忙，请稍后重试: " + drugNo);
        }
    }

    // ===== §2.7.5 查询库存流水 =====

    @Override
    public PageResult<DrugDTO.StockFlowListItem> listFlows(String drugNo, int page, int pageSize) {
        Drug d = requireByDrugNo(drugNo);
        Page<DrugStockFlow> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<DrugStockFlow> qw = new QueryWrapper<DrugStockFlow>()
                .eq("drug_id", d.getId())
                .orderByDesc("created_at");
        IPage<DrugStockFlow> result = flowMapper.selectPage(p, qw);

        // 组装 batchNo（流水只存 batch_id，跨表查询批次号）。LinkedHashSet 去重 O(n)。
        java.util.Set<Long> batchIdSet = new java.util.LinkedHashSet<>();
        for (DrugStockFlow f : result.getRecords()) {
            if (f.getBatchId() != null) batchIdSet.add(f.getBatchId());
        }
        Map<Long, String> batchNoMap = toBatchNoMap(
                batchMapper.selectBatchIds(batchIdSet.isEmpty() ? java.util.List.of() : new ArrayList<>(batchIdSet)));

        List<DrugDTO.StockFlowListItem> items = new ArrayList<>();
        for (DrugStockFlow f : result.getRecords()) {
            items.add(new DrugDTO.StockFlowListItem(
                    f.getFlowNo(),
                    f.getType(),
                    f.getQuantity(),
                    f.getBatchId() != null ? batchNoMap.get(f.getBatchId()) : null,
                    f.getCreatedAt()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.7.5b 全局库存流水（管理员视角） =====

    @Override
    public PageResult<DrugDTO.GlobalStockFlowListItem> listAllFlows(int page, int pageSize, String type, String drugNo) {
        Page<DrugStockFlow> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<DrugStockFlow> qw = new QueryWrapper<DrugStockFlow>().orderByDesc("created_at");
        if (type != null && !type.isBlank()) {
            qw.eq("type", type);
        }
        // drugNo 过滤需先转 drugId（流水表存 drug_id）
        Long filterDrugId = null;
        if (drugNo != null && !drugNo.isBlank()) {
            Drug d = drugMapper.selectOne(new QueryWrapper<Drug>().eq("drug_no", drugNo));
            if (d == null) {
                return PageResult.of(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize), 0L, new ArrayList<>());
            }
            filterDrugId = d.getId();
            qw.eq("drug_id", filterDrugId);
        }
        IPage<DrugStockFlow> result = flowMapper.selectPage(p, qw);

        // 跨表组装 drugNo/drugName + batchNo（参照 listAlerts 的批量查询模式）
        java.util.Set<Long> drugIdSet = new java.util.LinkedHashSet<>();
        java.util.Set<Long> batchIdSet = new java.util.LinkedHashSet<>();
        for (DrugStockFlow f : result.getRecords()) {
            if (f.getDrugId() != null) drugIdSet.add(f.getDrugId());
            if (f.getBatchId() != null) batchIdSet.add(f.getBatchId());
        }
        Map<Long, Drug> drugMap = toDrugMap(
                drugMapper.selectBatchIds(drugIdSet.isEmpty() ? java.util.List.of() : new ArrayList<>(drugIdSet)));
        Map<Long, String> batchNoMap = toBatchNoMap(
                batchMapper.selectBatchIds(batchIdSet.isEmpty() ? java.util.List.of() : new ArrayList<>(batchIdSet)));

        List<DrugDTO.GlobalStockFlowListItem> items = new ArrayList<>();
        for (DrugStockFlow f : result.getRecords()) {
            Drug d = drugMap.get(f.getDrugId());
            items.add(new DrugDTO.GlobalStockFlowListItem(
                    f.getFlowNo(),
                    f.getType(),
                    f.getQuantity(),
                    f.getBatchId() != null ? batchNoMap.get(f.getBatchId()) : null,
                    d != null ? d.getDrugNo() : null,
                    d != null ? d.getGenericName() : null,
                    f.getCreatedAt()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.7.6 查询库存预警 =====

    @Override
    public PageResult<DrugDTO.AlertItem> listAlerts(String type, int page, int pageSize) {
        String alertType = (type == null || type.isBlank()) ? "LOW_STOCK" : type.toUpperCase();
        int pNo = PageQuery.normalizePage(page);
        int pSize = PageQuery.normalizePageSize(pageSize);

        if ("LOW_STOCK".equals(alertType)) {
            // LOW_STOCK：current_stock < min_stock_threshold AND status=ACTIVE
            Page<Drug> p = new Page<>(pNo, pSize);
            // current_stock < min_stock_threshold（列间比较，用 apply 原生片段更稳妥，避免被当字符串字面量）
            QueryWrapper<Drug> qw = new QueryWrapper<Drug>()
                    .eq("status", "ACTIVE")
                    .apply("current_stock < min_stock_threshold")
                    .orderByAsc("current_stock");
            IPage<Drug> result = drugMapper.selectPage(p, qw);
            List<DrugDTO.AlertItem> items = new ArrayList<>();
            for (Drug d : result.getRecords()) {
                items.add(new DrugDTO.AlertItem(
                        d.getDrugNo(),
                        d.getGenericName(),
                        d.getSpecification(),
                        d.getUnit(),
                        "LOW_STOCK",
                        d.getCurrentStock() == null ? 0 : d.getCurrentStock(),
                        d.getMinStockThreshold() == null ? 0 : d.getMinStockThreshold(),
                        null,
                        null,
                        null));
            }
            return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
        } else if ("NEAR_EXPIRY".equals(alertType)) {
            // NEAR_EXPIRY：批次 expire_date BETWEEN today AND today+30 AND quantity>0
            LocalDate today = LocalDate.now();
            LocalDate horizon = today.plusDays(NEAR_EXPIRY_DAYS);
            Page<DrugStockBatch> p = new Page<>(pNo, pSize);
            QueryWrapper<DrugStockBatch> bw = new QueryWrapper<DrugStockBatch>()
                    .eq("status", "AVAILABLE")
                    .gt("quantity", 0)
                    .between("expire_date", today, horizon)
                    .orderByAsc("expire_date");
            IPage<DrugStockBatch> result = batchMapper.selectPage(p, bw);
            // 组装 drugName（跨表查 drug）。LinkedHashSet 去重 O(n)。
            java.util.Set<Long> drugIdSet = new java.util.LinkedHashSet<>();
            for (DrugStockBatch b : result.getRecords()) {
                if (b.getDrugId() != null) drugIdSet.add(b.getDrugId());
            }
            Map<Long, Drug> drugMap = toDrugMap(
                    drugMapper.selectBatchIds(drugIdSet.isEmpty() ? java.util.List.of() : new ArrayList<>(drugIdSet)));
            List<DrugDTO.AlertItem> items = new ArrayList<>();
            for (DrugStockBatch b : result.getRecords()) {
                Drug d = drugMap.get(b.getDrugId());
                items.add(new DrugDTO.AlertItem(
                        d != null ? d.getDrugNo() : null,
                        d != null ? d.getGenericName() : null,
                        d != null ? d.getSpecification() : null,
                        d != null ? d.getUnit() : null,
                        "NEAR_EXPIRY",
                        b.getQuantity(),
                        NEAR_EXPIRY_DAYS, // 近效期阈值天数
                        b.getBatchNo(),
                        b.getExpireDate(),
                        b.getExpireDate() == null ? null : ChronoUnit.DAYS.between(today, b.getExpireDate())));
            }
            return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法预警类型: " + alertType
                    + "（支持 LOW_STOCK / NEAR_EXPIRY）");
        }
    }

    // ===== 内部接口 =====

    /** 内部：用药风险信息（解析 JSON 串组装 DrugRiskInfoDTO） */
    @Override
    public DrugRiskInfoDTO getRiskInfo(Long drugId) {
        Drug d = drugMapper.selectById(drugId);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + drugId);
        }
        return toRiskInfo(d);
    }

    /** 内部：批量用药风险信息（一次 selectBatchIds，按输入首次出现顺序返回） */
    @Override
    public DrugRiskBatchResponse getRiskInfoBatch(List<Long> drugIds) {
        List<Long> normalized = normalizeDrugIds(drugIds);
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "drugIds 不能为空");
        }
        if (normalized.size() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "drugIds 一次最多查询 100 个");
        }

        List<Drug> drugs = drugMapper.selectBatchIds(normalized);
        Map<Long, Drug> drugMap = toDrugMap(drugs);
        List<DrugRiskInfoDTO> items = new ArrayList<>();
        List<Long> missingDrugIds = new ArrayList<>();
        for (Long drugId : normalized) {
            Drug drug = drugMap.get(drugId);
            if (drug == null) {
                missingDrugIds.add(drugId);
            } else {
                items.add(toRiskInfo(drug));
            }
        }
        return new DrugRiskBatchResponse(items, missingDrugIds);
    }

    private DrugRiskInfoDTO toRiskInfo(Drug d) {
        List<DrugRiskInfoDTO.Contraindication> contras = parseContraindications(d.getContraindications());
        List<DrugRiskInfoDTO.Interaction> inters = parseInteractions(d.getInteractions());
        return new DrugRiskInfoDTO(d.getId(), d.getGenericName(), contras, inters);
    }

    /** 内部：FEFO 选批查询（返回按 expire_date ASC 排序的可用批次，不扣减） */
    @Override
    public List<DrugDTO.BatchInfo> ffeoBatches(Long drugId, int quantity) {
        Drug d = drugMapper.selectById(drugId);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + drugId);
        }
        QueryWrapper<DrugStockBatch> bw = new QueryWrapper<DrugStockBatch>()
                .eq("drug_id", drugId)
                .eq("status", "AVAILABLE")
                .ge("expire_date", LocalDate.now())
                .gt("quantity", 0)
                .orderByAsc("expire_date");
        List<DrugStockBatch> batches = batchMapper.selectList(bw);
        List<DrugDTO.BatchInfo> items = new ArrayList<>();
        for (DrugStockBatch b : batches) {
            items.add(new DrugDTO.BatchInfo(
                    b.getId(),
                    b.getBatchNo(),
                    b.getQuantity(),
                    b.getExpireDate(),
                    b.getSupplier()));
        }
        return items;
    }

    /** 内部：某药品当前总库存（drug.current_stock） */
    @Override
    public int getCurrentStock(Long drugId) {
        Drug d = drugMapper.selectById(drugId);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + drugId);
        }
        return d.getCurrentStock() == null ? 0 : d.getCurrentStock();
    }

    /**
     * 内部：按处方明细回滚出库（补偿调剂失败，锁内调用 txService）。
     * <p>回滚同样走 Redis 锁（与出库同一把 lock:drug:{drugId}:stock），保证回滚期间库存读一致。
     */
    @Override
    public int rollbackOutboundByItem(String drugNo, Long prescriptionItemId) {
        Drug d = requireByDrugNo(drugNo);
        String lockKey = RedisKey.DRUG_STOCK_LOCK + d.getId();
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.rollbackOutboundByItem(d.getId(), drugNo, prescriptionItemId));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "库存回滚繁忙，请稍后重试: " + drugNo);
        }
    }

    // ===== 私有助手 =====

    /** 按药品编号查询，未找到抛 NOT_FOUND */
    private Drug requireByDrugNo(String drugNo) {
        if (drugNo == null || drugNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "药品编号不能为空");
        }
        Drug d = drugMapper.selectOne(new QueryWrapper<Drug>().eq("drug_no", drugNo));
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + drugNo);
        }
        return d;
    }

    private Map<Long, String> toBatchNoMap(List<DrugStockBatch> batches) {
        Map<Long, String> map = new HashMap<>();
        for (DrugStockBatch b : batches) {
            map.put(b.getId(), b.getBatchNo());
        }
        return map;
    }

    private Map<Long, Drug> toDrugMap(List<Drug> drugs) {
        Map<Long, Drug> map = new HashMap<>();
        for (Drug d : drugs) {
            map.put(d.getId(), d);
        }
        return map;
    }

    private static List<Long> normalizeDrugIds(List<Long> drugIds) {
        Set<Long> unique = new LinkedHashSet<>();
        if (drugIds != null) {
            for (Long id : drugIds) {
                if (id != null && id > 0) {
                    unique.add(id);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * 禁忌 JSON 串组装：输入简化字符串列表 → [{condition,level="RELATIVE",note=null}]。
     * 创建接口输入简化（无 level），出参 DrugRiskInfoDTO 仍结构化；level 默认 RELATIVE（相对禁忌）。
     */
    private String toContraindicationsJson(List<String> contras) {
        if (contras == null || contras.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, String>> list = new ArrayList<>();
            for (String c : contras) {
                Map<String, String> m = new HashMap<>();
                m.put("condition", c);
                m.put("level", "RELATIVE");
                list.add(m);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("禁忌序列化失败，原样存 null", e);
            return null;
        }
    }

    /** 相互作用 JSON 串组装：输入简化字符串列表 → [{drugCode=input,effect=input,level="MODERATE"}] */
    private String toInteractionsJson(List<String> inters) {
        if (inters == null || inters.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, String>> list = new ArrayList<>();
            for (String i : inters) {
                Map<String, String> m = new HashMap<>();
                m.put("drugCode", i);
                m.put("effect", i);
                m.put("level", "MODERATE");
                list.add(m);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("相互作用序列化失败，原样存 null", e);
            return null;
        }
    }

    /** 解析禁忌 JSON 串 → DrugRiskInfoDTO.Contraindication 列表 */
    @SuppressWarnings("unchecked")
    private List<DrugRiskInfoDTO.Contraindication> parseContraindications(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<DrugRiskInfoDTO.Contraindication> result = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                result.add(new DrugRiskInfoDTO.Contraindication(
                        asString(m.get("condition")),
                        asString(m.get("level")),
                        asString(m.get("note"))));
            }
            return result;
        } catch (Exception e) {
            log.warn("禁忌 JSON 解析失败: {}", json, e);
            return List.of();
        }
    }

    /** 解析相互作用 JSON 串 → DrugRiskInfoDTO.Interaction 列表 */
    @SuppressWarnings("unchecked")
    private List<DrugRiskInfoDTO.Interaction> parseInteractions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<DrugRiskInfoDTO.Interaction> result = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                result.add(new DrugRiskInfoDTO.Interaction(
                        asString(m.get("drugCode")),
                        asString(m.get("effect")),
                        asString(m.get("level"))));
            }
            return result;
        } catch (Exception e) {
            log.warn("相互作用 JSON 解析失败: {}", json, e);
            return List.of();
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /** 生成药品编号：D + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_drug_no 兜底 */
    private static String generateDrugNo() {
        long id = IdWorker.getId();
        return "D" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
