package com.medconsult.ai.service;

import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DrugFeignClient;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 受控 tool_call/functionTrace 回归测试。
 *
 * <p>医疗场景下工具调用必须由服务端上下文约束：患者只能来自请求/JWT 上下文，药品只能来自处方白名单，
 * 每次工具调用必须留下可审计 trace，且重复药品不能放大为 N+1 Feign 调用。
 */
class MedicationToolCallTraceTest {

    @Test
    void functionTraceShouldUseToolCallFieldsAndWhitelistPrescriptionDrugs() {
        PatientFeignClient patientClient = mock(PatientFeignClient.class);
        DrugFeignClient drugClient = mock(DrugFeignClient.class);
        when(drugClient.getRiskInfo(1L)).thenReturn(Result.ok(new DrugRiskInfoDTO(
                1L, "布洛芬",
                List.of(new DrugRiskInfoDTO.Contraindication("胃溃疡", "RELATIVE", "慎用")),
                List.of(new DrugRiskInfoDTO.Interaction("ASPIRIN", "增加出血风险", "MEDIUM"))
        )));
        MedicationFunctionService service = new MedicationFunctionService(patientClient, drugClient);
        MedicationAnalysisRequest request = new MedicationAnalysisRequest(
                "1001", "MR1", "RX1",
                List.of(new PrescriptionDto("1", "布洛芬", "0.2g", "tid", "口服", 3)),
                new PatientContext(45, "MALE", List.of(), List.of("胃溃疡"), List.of("阿司匹林")),
                true
        );

        MedicationFunctionService.FunctionResult result = service.execute(request);

        assertTrue(result.functionTrace().stream().anyMatch(trace -> "tool_call".equals(trace.get("type"))
                && "queryPatientAllergies".equals(trace.get("toolName"))
                && "SUCCESS".equals(trace.get("status"))));
        assertTrue(result.functionTrace().stream().anyMatch(trace -> "tool_call".equals(trace.get("type"))
                && "queryDrugRiskInfo".equals(trace.get("toolName"))
                && String.valueOf(trace.get("inputSummary")).contains("drugId=1")
                && "SUCCESS".equals(trace.get("status"))));
        assertTrue(result.functionTrace().stream().anyMatch(trace -> "queryDrugInteractions".equals(trace.get("toolName"))));
        verify(patientClient, never()).context(anyLong());
    }

    @Test
    void duplicatedDrugIdShouldBeFetchedOnce() {
        PatientFeignClient patientClient = mock(PatientFeignClient.class);
        DrugFeignClient drugClient = mock(DrugFeignClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(drugClient.getRiskInfo(1L)).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return Result.ok(DrugRiskInfoDTO.safe(1L, "布洛芬"));
        });
        MedicationFunctionService service = new MedicationFunctionService(patientClient, drugClient);
        MedicationAnalysisRequest request = new MedicationAnalysisRequest(
                "1001", "MR1", "RX1",
                List.of(
                        new PrescriptionDto("1", "布洛芬", "0.2g", "tid", "口服", 3),
                        new PrescriptionDto("1", "布洛芬缓释胶囊", "0.3g", "bid", "口服", 3)
                ),
                new PatientContext(45, "MALE", List.of(), List.of(), List.of()),
                true
        );

        service.execute(request);

        assertEquals(1, calls.get());
    }

    @Test
    void downstreamFailureShouldBeVisibleInToolTrace() {
        PatientFeignClient patientClient = mock(PatientFeignClient.class);
        DrugFeignClient drugClient = mock(DrugFeignClient.class);
        when(drugClient.getRiskInfo(1L)).thenThrow(new RuntimeException("drug-service timeout"));
        MedicationFunctionService service = new MedicationFunctionService(patientClient, drugClient);
        MedicationAnalysisRequest request = new MedicationAnalysisRequest(
                "1001", "MR1", "RX1",
                List.of(new PrescriptionDto("1", "布洛芬", "0.2g", "tid", "口服", 3)),
                new PatientContext(45, "MALE", List.of(), List.of(), List.of()),
                true
        );

        MedicationFunctionService.FunctionResult result = service.execute(request);

        assertTrue(result.functionTrace().stream().anyMatch(trace -> "queryDrugRiskInfo".equals(trace.get("toolName"))
                && "FAILED".equals(trace.get("status"))
                && String.valueOf(trace.get("errorCode")).contains("RuntimeException")));
    }
}
