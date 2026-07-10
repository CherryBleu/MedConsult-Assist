package com.medconsult.ai.client.internal;

import com.medconsult.ai.config.FeignInternalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "medconsult-drug-service", configuration = FeignInternalConfig.class)
public interface DrugInternalClient {
    @GetMapping("/internal/drugs/{id}/risk-info")
    DrugRiskInfoResponse getRiskInfo(@PathVariable("id") String drugId);

    record DrugRiskInfoResponse(
            String drugId,
            String drugName,
            List<Map<String, Object>> contraindications,
            List<Map<String, Object>> interactions
    ) {
    }
}
