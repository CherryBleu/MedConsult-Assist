package com.medconsult.ai.service;

import com.medconsult.ai.client.internal.DrugInternalClient;
import com.medconsult.ai.client.internal.PatientInternalClient;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MedicationFunctionService {
    private final PatientInternalClient patientClient;
    private final DrugInternalClient drugClient;

    public MedicationFunctionService(PatientInternalClient patientClient, DrugInternalClient drugClient) {
        this.patientClient = patientClient;
        this.drugClient = drugClient;
    }

    public FunctionResult execute(MedicationAnalysisRequest request) {
        PatientContext context = enrichPatientContext(request);
        List<DrugInternalClient.DrugRiskInfoResponse> drugRiskInfos = queryDrugRiskInfos(request.prescriptions());
        List<Map<String, Object>> contraindications = new ArrayList<>();
        List<Map<String, Object>> interactions = new ArrayList<>();
        List<Map<String, Object>> reminders = new ArrayList<>();
        List<Map<String, Object>> functionTrace = new ArrayList<>();

        functionTrace.add(trace("queryPatientAllergies", summarizeList(context.allergies())));
        functionTrace.add(trace("queryCurrentMedications", summarizeList(context.currentMedications())));

        for (PrescriptionDto prescription : request.prescriptions()) {
            reminders.add(Map.of(
                    "drugName", prescription.drugName(),
                    "reminder", "Use according to the prescribed dosage and frequency. Contact a doctor if discomfort occurs."
            ));
            detectLocalContraindication(context, prescription, contraindications);
            applyDrugRiskInfo(prescription, drugRiskInfos, contraindications, interactions, functionTrace);
        }
        detectInlineInteractions(request, context, interactions);

        String overallRisk = contraindications.isEmpty() && interactions.isEmpty() ? "LOW" : "MEDIUM";
        return new FunctionResult(overallRisk, contraindications, interactions, reminders, functionTrace, context);
    }

    private PatientContext enrichPatientContext(MedicationAnalysisRequest request) {
        if (request.patientContext() != null) {
            return request.patientContext();
        }
        if (!StringUtils.hasText(request.patientId())) {
            return new PatientContext(null, null, List.of(), List.of(), List.of());
        }
        try {
            PatientInternalClient.PatientContextResponse response = patientClient.getPatientContext(request.patientId());
            return new PatientContext(response.age(), response.gender(), safe(response.allergies()),
                    safe(response.pastMedicalHistory()), safe(response.currentMedications()));
        } catch (RuntimeException ex) {
            return new PatientContext(null, null, List.of(), List.of(), List.of());
        }
    }

    private List<DrugInternalClient.DrugRiskInfoResponse> queryDrugRiskInfos(List<PrescriptionDto> prescriptions) {
        List<DrugInternalClient.DrugRiskInfoResponse> result = new ArrayList<>();
        for (PrescriptionDto prescription : prescriptions) {
            if (!StringUtils.hasText(prescription.drugId())) {
                continue;
            }
            try {
                result.add(drugClient.getRiskInfo(prescription.drugId()));
            } catch (RuntimeException ignored) {
                // Keep local/dev flows usable when drug-service is not available yet.
            }
        }
        return result;
    }

    private static void detectLocalContraindication(PatientContext context, PrescriptionDto prescription,
                                                    List<Map<String, Object>> contraindications) {
        String drugName = lower(prescription.drugName());
        boolean nsaid = containsAny(drugName, "ibuprofen", "aspirin", "布洛芬", "阿司匹林");
        boolean stomachHistory = context.pastMedicalHistory() != null
                && context.pastMedicalHistory().stream()
                .anyMatch(item -> containsAny(lower(item), "gastritis", "ulcer", "胃", "胃炎", "溃疡"));
        if (nsaid && stomachHistory) {
            contraindications.add(Map.of(
                    "drugName", prescription.drugName(),
                    "riskLevel", "MEDIUM",
                    "description", "Patients with gastric disease history should use NSAIDs cautiously.",
                    "suggestion", "Ask the pharmacist or doctor to reassess gastrointestinal bleeding risk."
            ));
        }
    }

    private static void detectInlineInteractions(MedicationAnalysisRequest request, PatientContext context,
                                                 List<Map<String, Object>> interactions) {
        String prescribed = request.prescriptions().stream().map(PrescriptionDto::drugName).reduce("", (a, b) -> a + " " + b);
        String current = context.currentMedications() == null ? "" : String.join(" ", context.currentMedications());
        if (containsAny(prescribed, "布洛芬", "ibuprofen") && containsAny(prescribed + " " + current, "阿司匹林", "aspirin")) {
            interactions.add(Map.of(
                    "drugA", "ibuprofen",
                    "drugB", "aspirin",
                    "riskLevel", "MEDIUM",
                    "description", "Combined use may increase gastrointestinal adverse reaction or bleeding risk."
            ));
        }
    }

    private static void applyDrugRiskInfo(PrescriptionDto prescription,
                                          List<DrugInternalClient.DrugRiskInfoResponse> drugRiskInfos,
                                          List<Map<String, Object>> contraindications,
                                          List<Map<String, Object>> interactions,
                                          List<Map<String, Object>> functionTrace) {
        for (DrugInternalClient.DrugRiskInfoResponse info : drugRiskInfos) {
            if (!matches(prescription, info)) {
                continue;
            }
            functionTrace.add(trace("queryDrugContraindications", info.drugId() + " contraindications=" + safe(info.contraindications()).size()));
            functionTrace.add(trace("queryDrugInteractions", info.drugId() + " interactions=" + safe(info.interactions()).size()));
            for (Map<String, Object> item : safe(info.contraindications())) {
                Map<String, Object> risk = new LinkedHashMap<>(item);
                risk.putIfAbsent("drugName", prescription.drugName());
                risk.putIfAbsent("riskLevel", "MEDIUM");
                contraindications.add(risk);
            }
            for (Map<String, Object> item : safe(info.interactions())) {
                Map<String, Object> risk = new LinkedHashMap<>(item);
                risk.putIfAbsent("drugA", prescription.drugName());
                risk.putIfAbsent("riskLevel", "MEDIUM");
                interactions.add(risk);
            }
        }
    }

    private static boolean matches(PrescriptionDto prescription, DrugInternalClient.DrugRiskInfoResponse info) {
        return (StringUtils.hasText(prescription.drugId()) && prescription.drugId().equals(info.drugId()))
                || (StringUtils.hasText(info.drugName()) && prescription.drugName().contains(info.drugName()));
    }

    private static Map<String, Object> trace(String functionName, String resultSummary) {
        return Map.of("functionName", functionName, "resultSummary", resultSummary);
    }

    private static String summarizeList(List<String> values) {
        return values == null || values.isEmpty() ? "empty" : String.join(",", values);
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text != null && text.contains(lower(term))) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }

    public record FunctionResult(
            String overallRiskLevel,
            List<Map<String, Object>> contraindicationRisks,
            List<Map<String, Object>> interactionRisks,
            List<Map<String, Object>> reminders,
            List<Map<String, Object>> functionTrace,
            PatientContext patientContext
    ) {
    }
}
