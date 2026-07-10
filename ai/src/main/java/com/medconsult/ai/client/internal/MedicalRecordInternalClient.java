package com.medconsult.ai.client.internal;

import com.medconsult.ai.config.FeignInternalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "medconsult-medical-record-service", configuration = FeignInternalConfig.class)
public interface MedicalRecordInternalClient {
    @GetMapping("/internal/medical-records/{id}/full")
    MedicalRecordFullResponse getFullRecord(@PathVariable("id") String recordId);

    record MedicalRecordFullResponse(
            String recordId,
            String patientId,
            String chiefComplaint,
            String presentIllness,
            String pastHistory,
            String physicalExam,
            List<String> diagnosis,
            List<Map<String, Object>> medications,
            String doctorAdvice
    ) {
    }
}
