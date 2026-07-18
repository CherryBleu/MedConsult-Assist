package com.medconsult.ai.service;

import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DrugFeignClient;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.DrugRiskBatchRequest;
import com.medconsult.common.feign.dto.DrugRiskBatchResponse;
import com.medconsult.common.feign.dto.DrugRiskInfoDTO;
import com.medconsult.common.feign.dto.PatientContextDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link MedicationFunctionService#containsAny(String, String...)} 词边界匹配的单元测试。
 *
 * <p>纯 JUnit5，不启动 Spring 容器。核心是医疗安全回归保护：
 * 拼接子串（如 "ibuprofenspecial"）必须被词边界阻断，避免 AI 给出错误的用药相互作用警告。
 */
class MedicationFunctionServiceTest {
    private PatientFeignClient patientClient;
    private DrugFeignClient drugClient;
    private MedicationFunctionService service;

    @BeforeEach
    void setUp() {
        patientClient = mock(PatientFeignClient.class);
        drugClient = mock(DrugFeignClient.class);
        service = new MedicationFunctionService(patientClient, drugClient);
    }

    @Test
    void executeShouldUseProvidedPatientContextAndBatchDrugRiskInfo() {
        PatientContext context = new PatientContext(65, "FEMALE", List.of("penicillin"),
                List.of("gastritis"), List.of("aspirin"));
        DrugRiskInfoDTO riskInfo = new DrugRiskInfoDTO(101L, "ibuprofen",
                List.of(new DrugRiskInfoDTO.Contraindication("pregnancy", "RELATIVE", "review")),
                List.of(new DrugRiskInfoDTO.Interaction("ASPIRIN", "bleeding", "MEDIUM")));
        when(drugClient.getRiskInfoBatch(any())).thenReturn(Result.ok(
                new DrugRiskBatchResponse(List.of(riskInfo), List.of(102L))));
        ArgumentCaptor<DrugRiskBatchRequest> requestCaptor = ArgumentCaptor.forClass(DrugRiskBatchRequest.class);

        MedicationFunctionService.FunctionResult result = service.execute(request("P22", context,
                prescription("DRUG101", "ibuprofen"),
                prescription("102", "amoxicillin")));

        verifyNoInteractions(patientClient);
        verify(drugClient).getRiskInfoBatch(requestCaptor.capture());
        assertEquals(List.of(101L, 102L), requestCaptor.getValue().drugIds());
        assertEquals(context, result.patientContext());
        assertEquals("MEDIUM", result.overallRiskLevel());
        assertEquals(2, result.reminders().size());
        assertEquals(2, result.contraindicationRisks().size());
        assertEquals(2, result.interactionRisks().size());
        assertTrace(result, "queryPatientAllergies", "SUCCESS", "penicillin", null);
        assertTrace(result, "queryCurrentMedications", "SUCCESS", "aspirin", null);
        assertTrace(result, "queryDrugRiskInfo", "SUCCESS", "contraindications=1,interactions=1", null);
        assertTrace(result, "queryDrugRiskInfo", "FAILED", "missing", "NOT_FOUND");
        assertTrace(result, "queryDrugContraindications", "SUCCESS", "contraindications=1", null);
        assertTrace(result, "queryDrugInteractions", "SUCCESS", "interactions=1", null);
    }

    @Test
    void executeShouldSkipPlaceholderPrescriptionsWhenPatientIdIsMissing() {
        MedicationFunctionService.FunctionResult result = service.execute(request(" ", null,
                prescription("101", "placeholder"),
                prescription(null, "test")));

        assertEquals("LOW", result.overallRiskLevel());
        assertTrue(result.reminders().isEmpty());
        assertTrue(result.contraindicationRisks().isEmpty());
        assertTrue(result.interactionRisks().isEmpty());
        assertTrace(result, "queryPatientAllergies", "SKIPPED", "empty", null);
        assertTrace(result, "queryCurrentMedications", "SKIPPED", "empty", null);
        verifyNoInteractions(patientClient, drugClient);
    }

    @Test
    void executeShouldTraceInvalidDrugIdAndMissingBatchItems() {
        PatientContext context = new PatientContext(null, null, null, null, null);
        when(drugClient.getRiskInfoBatch(any())).thenReturn(Result.ok(new DrugRiskBatchResponse(null, null)));
        ArgumentCaptor<DrugRiskBatchRequest> requestCaptor = ArgumentCaptor.forClass(DrugRiskBatchRequest.class);

        MedicationFunctionService.FunctionResult result = service.execute(request("P99", context,
                prescription("RX-ABC", "paracetamol"),
                prescription("101", "acetaminophen")));

        verify(drugClient).getRiskInfoBatch(requestCaptor.capture());
        assertEquals(List.of(101L), requestCaptor.getValue().drugIds());
        assertEquals("LOW", result.overallRiskLevel());
        assertEquals(2, result.reminders().size());
        assertTrace(result, "queryDrugRiskInfo", "FAILED", "invalid drugId", "INVALID_DRUG_ID");
        assertTrace(result, "queryDrugRiskInfo", "FAILED", "empty", "NOT_FOUND");
    }

    @Test
    void executeShouldTraceEmptyPatientAndDrugResponses() {
        when(patientClient.context(42L)).thenReturn(Result.ok(null));
        when(drugClient.getRiskInfoBatch(any())).thenReturn(Result.ok(null));

        MedicationFunctionService.FunctionResult result = service.execute(request("42", null,
                prescription("101", "paracetamol")));

        assertEquals("LOW", result.overallRiskLevel());
        assertTrace(result, "queryPatientAllergies", "FAILED", "empty", "EMPTY_RESPONSE");
        assertTrace(result, "queryCurrentMedications", "FAILED", "empty", "EMPTY_RESPONSE");
        assertTrace(result, "queryDrugRiskInfo", "FAILED", "empty", "EMPTY_RESPONSE");
    }

    @Test
    void executeShouldDegradeWhenPatientAndDrugServicesFail() {
        when(patientClient.context(42L)).thenThrow(new IllegalStateException("patient unavailable"));
        when(drugClient.getRiskInfoBatch(any())).thenThrow(new IllegalStateException("drug unavailable"));

        MedicationFunctionService.FunctionResult result = service.execute(request("42", null,
                prescription("101", "paracetamol")));

        assertEquals("LOW", result.overallRiskLevel());
        assertEquals(new PatientContext(null, null, List.of(), List.of(), List.of()), result.patientContext());
        assertTrace(result, "queryPatientAllergies", "FAILED", "empty", "IllegalStateException");
        assertTrace(result, "queryCurrentMedications", "FAILED", "empty", "IllegalStateException");
        assertTrace(result, "queryDrugRiskInfo", "FAILED", "degraded to local rules", "IllegalStateException");
    }

    @Test
    void executeShouldUsePatientServiceContextAndSafeNullLists() {
        when(patientClient.context(42L)).thenReturn(Result.ok(new PatientContextDTO(
                42L, "masked", 30, "MALE", null, null, List.of("metformin"))));
        when(drugClient.getRiskInfoBatch(any())).thenReturn(Result.ok(new DrugRiskBatchResponse(List.of(), List.of())));

        MedicationFunctionService.FunctionResult result = service.execute(request("PAT42", null,
                prescription("101", "paracetamol")));

        assertEquals(List.of(), result.patientContext().allergies());
        assertEquals(List.of(), result.patientContext().pastMedicalHistory());
        assertEquals(List.of("metformin"), result.patientContext().currentMedications());
        assertTrace(result, "queryPatientAllergies", "SUCCESS", "empty", null);
        assertTrace(result, "queryCurrentMedications", "SUCCESS", "metformin", null);
        assertTrace(result, "queryDrugRiskInfo", "FAILED", "empty", "NOT_FOUND");
    }

    @Test
    void toolTraceShouldRejectUnknownToolStatusAndInvalidInputSummary() {
        Map<String, Object> trace = MedicationFunctionService.toolTrace(
                "queryDrugRiskInfo", "drugId=101", "SUCCESS", "ok", null);

        assertFalse(trace.containsKey("errorCode"));
        assertThrows(IllegalArgumentException.class, () -> MedicationFunctionService.toolTrace(
                "unknownTool", "drugId=101", "SUCCESS", "ok", null));
        assertThrows(IllegalArgumentException.class, () -> MedicationFunctionService.toolTrace(
                "queryDrugRiskInfo", "drugId=101", "DONE", "ok", null));
        assertThrows(IllegalArgumentException.class, () -> MedicationFunctionService.toolTrace(
                "queryDrugRiskInfo", "patientId=101", "SUCCESS", "ok", null));
    }

    @Test
    void concatenatedSubstringShouldNotMatch() {
        // 本次修复的核心 case：药名被直接拼进另一个单词，前后都是单词字符，无词边界
        assertFalse(MedicationFunctionService.containsAny("ibuprofenspecial", "ibuprofen"));
        assertFalse(MedicationFunctionService.containsAny("aspirinxyz", "aspirin"));
    }

    @Test
    void shouldMatchCaseInsensitively() {
        // term 小写、text 含大写：CASE_INSENSITIVE 保证匹配（旧代码 text 未 lower 会漏配）
        assertTrue(MedicationFunctionService.containsAny("Ibuprofen 200mg", "ibuprofen"));
        assertTrue(MedicationFunctionService.containsAny("ASPIRIN tablet", "aspirin"));
    }

    @Test
    void hyphenIsWordBoundarySoExtendedReleaseStillMatches() {
        // 连字符是词边界：缓释片 (ibuprofen-ER) 与原药同效，应当匹配
        assertTrue(MedicationFunctionService.containsAny("ibuprofen-er", "ibuprofen"));
    }

    @Test
    void uppercaseInTextShouldStillMatch() {
        // 回归保护：text 首字母大写，term 全小写
        assertTrue(MedicationFunctionService.containsAny("aspirin", "Aspirin"));
        assertTrue(MedicationFunctionService.containsAny("Take Ibuprofen daily", "ibuprofen"));
    }

    @Test
    void chineseShouldUseContainsSemantics() {
        // 中文无空格分词，走 contains：肠溶片剂型匹配通用名"阿司匹林"
        assertTrue(MedicationFunctionService.containsAny("阿司匹林肠溶片", "阿司匹林"));
        assertTrue(MedicationFunctionService.containsAny("布洛芬缓释胶囊", "布洛芬"));
    }

    @Test
    void nullTextShouldNotThrow() {
        assertFalse(MedicationFunctionService.containsAny(null, "ibuprofen"));
        assertFalse(MedicationFunctionService.containsAny(null, (String) null));
    }

    @Test
    void nullOrEmptyTermsShouldBeSkipped() {
        assertFalse(MedicationFunctionService.containsAny("ibuprofen", (String) null));
        assertFalse(MedicationFunctionService.containsAny("ibuprofen", ""));
        // 混入 null/空 term 不应影响后续有效 term 的匹配
        assertTrue(MedicationFunctionService.containsAny("ibuprofen 200mg", null, "", "ibuprofen"));
    }

    @Test
    void multipleTermsAnyMatchShouldReturnTrue() {
        assertTrue(MedicationFunctionService.containsAny("amoxicillin capsule", "ibuprofen", "aspirin", "amoxicillin"));
    }

    @Test
    void noMatchShouldReturnFalse() {
        assertFalse(MedicationFunctionService.containsAny("paracetamol 500mg", "ibuprofen", "aspirin"));
    }

    private static MedicationAnalysisRequest request(String patientId, PatientContext context,
                                                     PrescriptionDto... prescriptions) {
        return new MedicationAnalysisRequest(patientId, "MR1", "RX1", List.of(prescriptions), context, true);
    }

    private static PrescriptionDto prescription(String drugId, String drugName) {
        return new PrescriptionDto(drugId, drugName, "1 tablet", "bid", "oral", 3);
    }

    private static void assertTrace(MedicationFunctionService.FunctionResult result, String toolName,
                                    String status, String resultSummary, String errorCode) {
        boolean matched = result.functionTrace().stream().anyMatch(trace ->
                toolName.equals(trace.get("toolName"))
                        && status.equals(trace.get("status"))
                        && resultSummary.equals(trace.get("resultSummary"))
                        && java.util.Objects.equals(errorCode, trace.get("errorCode")));
        assertTrue(matched, () -> "missing trace " + toolName + "/" + status + "/" + resultSummary
                + "/" + errorCode + " in " + result.functionTrace());
    }

    @Test
    void containsLatinHelper() {
        assertTrue(MedicationFunctionService.containsLatin("ibuprofen"));
        assertTrue(MedicationFunctionService.containsLatin("阿司匹林 aspirin"));
        assertFalse(MedicationFunctionService.containsLatin("阿司匹林"));
        assertFalse(MedicationFunctionService.containsLatin("布洛芬"));
        assertFalse(MedicationFunctionService.containsLatin(""));
    }
}
