package com.medconsult.drug.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.drug.dto.DrugDTO;
import com.medconsult.drug.service.DrugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 药品库存管理对外接口（对齐《接口文档》§2.7）。
 *
 * <p>路径前缀 /api/v1/drugs（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code drugId} 实为 {@code drug_no}（业务可读编号，对外暴露）。
 */
@RestController
@RequestMapping("/api/v1/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    /** §2.7.1 创建药品（current_stock 初始 0） */
    @PostMapping
    public Result<DrugDTO.DrugSummary> create(@Valid @RequestBody DrugDTO.CreateDrugRequest req) {
        return Result.ok(drugService.createDrug(req));
    }

    /** §2.7.2 分页查询药品列表 */
    @GetMapping
    public Result<PageResult<DrugDTO.DrugListItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(drugService.listDrugs(page, pageSize, keyword));
    }

    /** §2.7.3 药品入库（Redis 锁内事务） */
    @PostMapping("/{drugId}/stock/inbound")
    public Result<DrugDTO.InboundResponse> inbound(@PathVariable String drugId,
                                                    @Valid @RequestBody DrugDTO.InboundRequest req) {
        return Result.ok(drugService.inbound(drugId, req));
    }

    /** §2.7.4 药品出库（Redis 锁内 FEFO 选批扣减） */
    @PostMapping("/{drugId}/stock/outbound")
    public Result<DrugDTO.OutboundResponse> outbound(@PathVariable String drugId,
                                                      @Valid @RequestBody DrugDTO.OutboundRequest req) {
        return Result.ok(drugService.outbound(drugId, req));
    }

    /** §2.7.5 查询某药品的库存流水 */
    @GetMapping("/{drugId}/stock/flows")
    public Result<PageResult<DrugDTO.StockFlowListItem>> listFlows(
            @PathVariable String drugId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(drugService.listFlows(drugId, page, pageSize));
    }

    /** §2.7.6 查询库存预警（LOW_STOCK / NEAR_EXPIRY） */
    @GetMapping("/stock/alerts")
    public Result<PageResult<DrugDTO.AlertItem>> listAlerts(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(drugService.listAlerts(type, page, pageSize));
    }
}
