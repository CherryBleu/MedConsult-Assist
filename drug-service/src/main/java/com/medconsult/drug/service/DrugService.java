package com.medconsult.drug.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import com.medconsult.drug.dto.DrugDTO;

import java.util.List;

/**
 * 药品库存服务接口（对齐《接口文档》§2.7 + 架构文档 §2.3 内部接口）。
 *
 * <p>对外 6 个接口：createDrug / listDrugs / inbound / outbound / listFlows / listAlerts。
 * <p>对内 3 个接口：getRiskInfo / ffeoBatches / getCurrentStock。
 * <p>库存出库并发控制由 Redis 分布式锁保证（架构文档 §7.1，lock:drug:{drugId}:stock），
 * FEFO（近效期优先）为默认选批策略（医疗强制，《需求文档》§4.1.5 规则 2）。
 */
public interface DrugService {

    /** §2.7.1 创建药品（current_stock 初始 0） */
    DrugDTO.DrugSummary createDrug(DrugDTO.CreateDrugRequest req);

    /** §2.7.2 分页查询药品，可按 keyword（generic_name/trade_name 模糊）过滤 */
    PageResult<DrugDTO.DrugListItem> listDrugs(int page, int pageSize, String keyword);

    /** §2.7.3 药品入库（Redis 锁内事务：建/更新批次 + current_stock += quantity + INBOUND flow） */
    DrugDTO.InboundResponse inbound(String drugNo, DrugDTO.InboundRequest req);

    /** §2.7.4 药品出库（Redis 锁内事务：FEFO 选批扣减 + current_stock -= quantity + OUTBOUND flow） */
    DrugDTO.OutboundResponse outbound(String drugNo, DrugDTO.OutboundRequest req);

    /** §2.7.5 分页查询某药品的库存流水（按 created_at DESC） */
    PageResult<DrugDTO.StockFlowListItem> listFlows(String drugNo, int page, int pageSize);

    /** §2.7.6 分页查询库存预警：LOW_STOCK（current_stock &lt; threshold）/ NEAR_EXPIRY（30 天内过期） */
    PageResult<DrugDTO.AlertItem> listAlerts(String type, int page, int pageSize);

    // ===== 内部接口（/internal/drugs，供 ai-service / medical-record-service 通过 feign 调用）=====

    /** 内部：用药风险信息（禁忌 / 相互作用，DrugRiskInfoDTO） */
    DrugRiskInfoDTO getRiskInfo(Long drugId);

    /** 内部：FEFO 选批查询（返回按 expire_date ASC 排序的可用批次，不扣减） */
    List<DrugDTO.BatchInfo> ffeoBatches(Long drugId, int quantity);

    /** 内部：某药品当前总库存（drug.current_stock） */
    int getCurrentStock(Long drugId);
}
