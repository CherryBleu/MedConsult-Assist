package com.medconsult.ai.client.internal;

import com.medconsult.ai.config.FeignInternalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "medconsult-patient-service", configuration = FeignInternalConfig.class)
public interface PatientInternalClient {
    @GetMapping("/internal/patients/{id}/context")
    PatientContextResponse getPatientContext(@PathVariable("id") String patientId);

    @GetMapping("/internal/patients/{id}/allergies")
    List<String> getAllergies(@PathVariable("id") String patientId);

    record PatientContextResponse(
            String patientId,
            Integer age,
            String gender,
            List<String> allergies,
            List<String> pastMedicalHistory,
            List<String> currentMedications
    ) {
    }
}
