package com.medconsult.common.feign.client;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.DispenseDTO;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * drug-service 的 Feign 客户端（架构文档 §2.3 / §6.2）。
 *
 * <p>供 medical-record-service / ai-service 调用 drug-service 的 /internal/drugs/* 内部接口。
 * name = {@code "drug-service"} 对应 Nacos 注册的服务名。
 *
 * <p><b>身份透传</b>：由 common-feign 的 {@link com.medconsult.common.feign.AuthRelayInterceptor}
 * 自动处理——用户链路（如药师触发 dispense）透传 userToken，无用户链路用服务自身 token。
 *
 * <p><b>错误处理</b>：下游返回 4xx/5xx 时由 {@link com.medconsult.common.feign.FeignErrorDecoder}
 * 解码为 BusinessException（保留下游 ErrorCode），调用方可直接 try/catch。
 *
 * <p>路径变量语义：
 * <ul>
 *   <li>{@code drugId}（Long）：risk-info / current-stock 用主键</li>
 *   <li>{@code drugNo}（String）：outbound 用业务编号</li>
 * </ul>
 */
@FeignClient(name = "drug-service", contextId = "drugFeignClient")
public interface DrugFeignClient {

    /** 内部：用药风险信息（禁忌 / 相互作用）—— ai-service Function Calling 用 */
    @GetMapping("/internal/drugs/{drugId}/risk-info")
    Result<DrugRiskInfoDTO> getRiskInfo(@PathVariable("drugId") Long drugId);

    /** 内部：当前总库存—— medical-record dispense 前预校验 */
    @GetMapping("/internal/drugs/{drugId}/current-stock")
    Result<Integer> getCurrentStock(@PathVariable("drugId") Long drugId);

    /**
     * 内部：FEFO 出库扣减（架构文档 §6.2 调剂发药触发）。
     *
     * <p>供 medical-record dispense 同步调用。drugNo 是业务编号。
     * 库存不足时下游返回 CONFLICT，FeignErrorDecoder 转为 BusinessException(CONFLICT)。
     */
    @PostMapping("/internal/drugs/{drugNo}/outbound")
    Result<DispenseDTO.OutboundResponse> outbound(
            @PathVariable("drugNo") String drugNo,
            @RequestBody DispenseDTO.OutboundRequest req);

    /**
     * 内部：按处方明细回滚出库（补偿 medical-record dispense 调剂失败）。
     * <p>把该明细此前 FEFO 扣减的库存原样还回对应批次。同明细重复调用幂等。
     *
     * @param drugNo            药品业务编号
     * @param prescriptionItemId 处方明细 ID
     * @return 回滚的 flow 条数（0 = 无可回滚）
     */
    @PostMapping("/internal/drugs/{drugNo}/rollback-outbound")
    Result<Integer> rollbackOutbound(
            @PathVariable("drugNo") String drugNo,
            @RequestParam("prescriptionItemId") Long prescriptionItemId);
}
