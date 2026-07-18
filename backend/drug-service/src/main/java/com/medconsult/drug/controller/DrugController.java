package com.medconsult.drug.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.drug.dto.DrugDTO;
import com.medconsult.drug.service.DrugService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "药品库存接口", description = "药品管理 + 库存出入库 + 流水 + 预警（§2.7）")
public class DrugController {

    private final DrugService drugService;

    /** §2.7.1 创建药品（current_stock 初始 0） */
    @PostMapping
    @Operation(summary = "创建药品")
    public Result<DrugDTO.DrugSummary> create(@Valid @RequestBody DrugDTO.CreateDrugRequest req) {
        return Result.ok(drugService.createDrug(req));
    }

    /** §2.7.2 分页查询药品列表 */
    @GetMapping
    @Operation(summary = "查询药品列表")
    public Result<PageResult<DrugDTO.DrugListItem>> list(
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(drugService.listDrugs(page, pageSize, keyword));
    }

    /** §2.7.3 药品入库（Redis 锁内事务） */
    @PostMapping("/{drugId}/stock/inbound")
    @Operation(summary = "药品入库")
    public Result<DrugDTO.InboundResponse> inbound(@Parameter(description = "药品编号", required = true) @PathVariable String drugId,
                                                    @Valid @RequestBody DrugDTO.InboundRequest req) {
        return Result.ok(drugService.inbound(drugId, req));
    }

    /** §2.7.4 药品出库（Redis 锁内 FEFO 选批扣减） */
    @PostMapping("/{drugId}/stock/outbound")
    @Operation(summary = "药品出库")
    public Result<DrugDTO.OutboundResponse> outbound(@Parameter(description = "药品编号", required = true) @PathVariable String drugId,
                                                      @Valid @RequestBody DrugDTO.OutboundRequest req) {
        return Result.ok(drugService.outbound(drugId, req));
    }

    /** §2.7.5 查询某药品的库存流水 */
    @GetMapping("/{drugId}/stock/flows")
    @Operation(summary = "查询库存流水")
    public Result<PageResult<DrugDTO.StockFlowListItem>> listFlows(
            @Parameter(description = "药品编号", required = true) @PathVariable String drugId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(drugService.listFlows(drugId, page, pageSize));
    }

    /**
     * §2.7.5b 全局库存流水（管理员视角，跨所有药品）。每条带 drugId/drugName。
     * <p>仅 PHARMACY_ADMIN / HOSPITAL_ADMIN 可访问（库存流水含经营数据，非对患者公开）。
     * <p>路径 /stock/flows 是常量段，优先于 /{drugId}/stock/flows 的路径变量匹配。
     */
    @GetMapping("/stock/flows")
    @Operation(summary = "全局库存流水")
    public Result<PageResult<DrugDTO.StockFlowListItem>> listAllFlows(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        com.medconsult.common.security.JwtPayload p = com.medconsult.common.security.SecurityContext.requireUser();
        boolean admin = p.hasRole("HOSPITAL_ADMIN") || p.hasRole("PHARMACY_ADMIN");
        if (!admin) {
            throw new com.medconsult.common.core.BusinessException(com.medconsult.common.core.ErrorCode.FORBIDDEN,
                    "仅医院管理员/药师可查看全局库存流水");
        }
        return Result.ok(drugService.listAllFlows(page, pageSize));
    }

    /** §2.7.6 查询库存预警（LOW_STOCK / NEAR_EXPIRY） */
    @GetMapping("/stock/alerts")
    @Operation(summary = "查询库存预警")
    public Result<PageResult<DrugDTO.AlertItem>> listAlerts(
            @Parameter(description = "预警类型") @RequestParam(required = false) String type,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(drugService.listAlerts(type, page, pageSize));
    }
}
