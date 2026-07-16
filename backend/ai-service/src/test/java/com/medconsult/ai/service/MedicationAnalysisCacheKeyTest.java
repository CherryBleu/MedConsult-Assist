package com.medconsult.ai.service;

import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 用药分析缓存 key 的患者隔离回归测试。
 *
 * <p>同一处方在不同患者/不同过敏史下风险结论不同，整响应缓存绝不能只按处方命中。
 */
class MedicationAnalysisCacheKeyTest {

    @Test
    void cacheKeyShouldIncludePatientId() {
        List<PrescriptionDto> prescriptions = List.of(new PrescriptionDto("1", "阿莫西林", "0.5g", "bid", "口服", 3));
        MedicationAnalysisRequest patientA = new MedicationAnalysisRequest(
                "1001", "MR1", "RX1", prescriptions,
                new PatientContext(30, "FEMALE", List.of("青霉素"), List.of(), List.of()), true);
        MedicationAnalysisRequest patientB = new MedicationAnalysisRequest(
                "1002", "MR1", "RX1", prescriptions,
                new PatientContext(30, "FEMALE", List.of("青霉素"), List.of(), List.of()), true);

        assertNotEquals(MedicationAnalysisService.medicationCacheKeyFor(patientA),
                MedicationAnalysisService.medicationCacheKeyFor(patientB));
    }

    @Test
    void cacheKeyShouldIncludePatientContext() {
        List<PrescriptionDto> prescriptions = List.of(new PrescriptionDto("1", "布洛芬", "0.2g", "tid", "口服", 3));
        MedicationAnalysisRequest noStomachHistory = new MedicationAnalysisRequest(
                "1001", "MR1", "RX1", prescriptions,
                new PatientContext(30, "FEMALE", List.of(), List.of(), List.of()), true);
        MedicationAnalysisRequest withStomachHistory = new MedicationAnalysisRequest(
                "1001", "MR1", "RX1", prescriptions,
                new PatientContext(30, "FEMALE", List.of(), List.of("胃溃疡"), List.of()), true);

        assertNotEquals(MedicationAnalysisService.medicationCacheKeyFor(noStomachHistory),
                MedicationAnalysisService.medicationCacheKeyFor(withStomachHistory));
    }
}
