package com.medconsult.drug.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import com.medconsult.drug.dto.DrugDTO;
import com.medconsult.drug.service.DrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 药品库存服务对内接口（架构文档 §2.3，/internal/drugs）。
 *
 * <p>路径前缀 /internal/drugs（不配 Gateway 路由，由 JwtAuthFilter 拦截，仅服务间 Feign 调用）。
 * <p>路径变量 {@code drugId} 是 BIGINT 主键（跨服务调用用主键更稳定，区别于对外的 drug_no）。
 *
 * <p>调用方：
 * <ul>
 *   <li>ai-service：getRiskInfo 拿禁忌/相互作用，Function Calling 用药分析</li>
 *   <li>medical-record-service：ffeoBatches 调剂选批 / getCurrentStock 处方前校验库存</li>
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
}
