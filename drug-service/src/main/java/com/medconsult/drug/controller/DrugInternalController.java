package com.medconsult.drug.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import com.medconsult.drug.dto.DrugDTO;
import com.medconsult.drug.service.DrugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 药品库存服务对内接口（架构文档 §2.3，/internal/drugs）。
 *
 * <p>路径前缀 /internal/drugs（不配 Gateway 路由，由 JwtAuthFilter 拦截，仅服务间 Feign 调用）。
 *
 * <p><b>路径变量语义</b>（注意两种）：
 * <ul>
 *   <li>{@code drugId}（Long 主键）：用于 risk-info / current-stock / ffeo，跨服务内部调用主键更稳定</li>
 *   <li>{@code drugNo}（String 业务编号）：用于 outbound，因 DrugService.outbound 原生接受 drugNo，
 *       与对外 /api/v1/drugs/{drugId}/stock/outbound 一致（对外 drugId 实为 drug_no）</li>
 * </ul>
 *
 * <p>调用方：
 * <ul>
 *   <li>ai-service：getRiskInfo 拿禁忌/相互作用，Function Calling 用药分析</li>
 *   <li>medical-record-service：ffeoBatches 调剂选批 / getCurrentStock 处方前校验库存 /
 *       outbound 调剂发药 FEFO 出库（同步 Feign，架构文档 §6.2）</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/drugs")
@RequiredArgsConstructor
public class DrugInternalController {

    private final DrugService drugService;

    /** 用药风险信息（禁忌 / 相互作用，DrugRiskInfoDTO） */
    @GetMapping("/{drugId}/risk-info")
    public Result<DrugRiskInfoDTO> getRiskInfo(@PathVariable Long drugId) {
        return Result.ok(drugService.getRiskInfo(drugId));
    }

    /** FEFO 选批查询（按 expire_date ASC，不扣减，供调剂选批参考） */
    @GetMapping("/batch/ffeo")
    public Result<List<DrugDTO.BatchInfo>> ffeoBatches(
            @RequestParam Long drugId,
            @RequestParam(defaultValue = "1") int quantity) {
        return Result.ok(drugService.ffeoBatches(drugId, quantity));
    }

    /** 当前总库存（drug.current_stock） */
    @GetMapping("/{drugId}/current-stock")
    public Result<Integer> getCurrentStock(@PathVariable Long drugId) {
        return Result.ok(drugService.getCurrentStock(drugId));
    }

    /**
     * 内部出库（FEFO 扣减，架构文档 §6.2 调剂发药触发）。
     *
     * <p>供 medical-record-service 的 dispense 接口同步调用：每条处方明细对应一次出库。
     * drugNo 是业务编号（如 DXXX），service 层 requireByDrugNo 解析为主键后 Redis 锁内 FEFO 扣减。
     *
     * <p><b>同步 Feign 的跨服务事务限制</b>：本端点的扣减是 drug-service 独立事务，调用方
     * (medical-record) 事务回滚无法回滚此处扣减。调用方 dispense 失败时通过
     * {@link #rollbackOutbound} 反向补偿（按 prescriptionItemId 查 OUTBOUND flow 还回原批次）。
     */
    @PostMapping("/{drugNo}/outbound")
    public Result<DrugDTO.OutboundResponse> outbound(
            @PathVariable String drugNo,
            @Valid @RequestBody DrugDTO.OutboundRequest req) {
        return Result.ok(drugService.outbound(drugNo, req));
    }

    /**
     * 内部：按处方明细回滚出库（补偿 medical-record dispense 调剂失败）。
     * <p>按 prescriptionItemId 反查该明细的 OUTBOUND flow，把数量还回对应批次 + current_stock。
     * 同明细重复调用幂等。
     */
    @PostMapping("/{drugNo}/rollback-outbound")
    public Result<Integer> rollbackOutbound(
            @PathVariable String drugNo,
            @RequestParam Long prescriptionItemId) {
        return Result.ok(drugService.rollbackOutboundByItem(drugNo, prescriptionItemId));
    }
}

