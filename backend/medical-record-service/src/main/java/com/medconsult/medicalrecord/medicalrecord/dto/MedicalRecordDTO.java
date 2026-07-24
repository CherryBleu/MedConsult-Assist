package com.medconsult.medicalrecord.medicalrecord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for electronic medical records.
 *
 * <p>{@code recordId} is the public {@code record_no}. Patient/doctor request IDs are business
 * numbers resolved to local numeric primary keys by the service layer.
 */
public class MedicalRecordDTO {

    @Data
    @Schema(description = "Create medical record request")
    public static class CreateRequest {
        @NotBlank(message = "patientId must not be blank")
        @Schema(description = "Patient number or id")
        private String patientId;

        @NotBlank(message = "doctorId must not be blank")
        @Schema(description = "Doctor number or id")
        private String doctorId;

        @Schema(description = "Appointment number")
        private String appointmentId;

        @Size(max = 1000, message = "chiefComplaint must not exceed 1000 characters")
        @Schema(description = "Chief complaint")
        private String chiefComplaint;

        @Size(max = 4000, message = "presentIllness must not exceed 4000 characters")
        @Schema(description = "Present illness")
        private String presentIllness;

        @Size(max = 4000, message = "pastHistory must not exceed 4000 characters")
        @Schema(description = "Past history")
        private String pastHistory;

        @Size(max = 4000, message = "physicalExam must not exceed 4000 characters")
        @Schema(description = "Physical exam")
        private String physicalExam;

        @Schema(description = "Initial diagnosis")
        private List<String> initialDiagnosis;

        @Size(max = 4000, message = "doctorAdvice must not exceed 4000 characters")
        @Schema(description = "Doctor advice")
        private String doctorAdvice;

        @Schema(description = "Draft prescription items")
        private List<DraftPrescriptionItem> prescriptions;
    }

    @Schema(description = "Create medical record response")
    public record CreateResponse(
            @Schema(description = "Record number") String recordId,
            @Schema(description = "Status: DRAFT / ARCHIVED / REVISED") String status
    ) {}

    @Schema(description = "Medical record detail response")
    public record DetailResponse(
            @Schema(description = "Record number") String recordId,
            @Schema(description = "Patient id") String patientId,
            @Schema(description = "Patient name") String patientName,
            @Schema(description = "Doctor id") String doctorId,
            @Schema(description = "Doctor name") String doctorName,
            @Schema(description = "Department name") String departmentName,
            @Schema(description = "Chief complaint") String chiefComplaint,
            @Schema(description = "Present illness") String presentIllness,
            @Schema(description = "Past history") String pastHistory,
            @Schema(description = "Physical exam") String physicalExam,
            @Schema(description = "Initial diagnosis") List<String> initialDiagnosis,
            @Schema(description = "Final diagnosis") List<String> finalDiagnosis,
            @Schema(description = "Doctor advice") String doctorAdvice,
            @Schema(description = "Record status") String status,
            @Schema(description = "Created time") LocalDateTime createdAt,
            @Schema(description = "Archived time") LocalDateTime archivedAt,
            @Schema(description = "Prescription items") List<PrescriptionItemResponse> prescriptions
    ) {}

    @Schema(description = "Prescription item embedded in medical record detail")
    public record PrescriptionItemResponse(
            @Schema(description = "Prescription number") String prescriptionId,
            @Schema(description = "Prescription status") String prescriptionStatus,
            @Schema(description = "Drug number") String drugNo,
            @Schema(description = "Drug name") String drugName,
            @Schema(description = "Frontend-compatible drug name") String name,
            @Schema(description = "Specification") String specification,
            @Schema(description = "Dosage") String dosage,
            @Schema(description = "Frequency") String frequency,
            @Schema(description = "Route") String route,
            @Schema(description = "Days") Integer days,
            @Schema(description = "Quantity") BigDecimal quantity,
            @Schema(description = "Unit") String unit,
            @Schema(description = "Unit price") BigDecimal unitPrice,
            @Schema(description = "Subtotal") BigDecimal subtotal
    ) {}

    @Data
    @Schema(description = "Draft prescription item stored on a medical record draft")
    public static class DraftPrescriptionItem {
        private String drugNo;
        private String drugName;
        private String name;
        private String specification;
        private String dosage;
        private String frequency;
        private String route;
        private Integer days;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    @Schema(description = "Medical record list item")
    public record ListItem(
            @Schema(description = "Record number") String recordId,
            @Schema(description = "Patient id") String patientId,
            @Schema(description = "Patient name") String patientName,
            @Schema(description = "Doctor id") String doctorId,
            @Schema(description = "Doctor name") String doctorName,
            @Schema(description = "Department name") String departmentName,
            @Schema(description = "Chief complaint") String chiefComplaint,
            @Schema(description = "Initial diagnosis") List<String> initialDiagnosis,
            @Schema(description = "Final diagnosis") List<String> finalDiagnosis,
            @Schema(description = "Created time") LocalDateTime createdAt,
            @Schema(description = "Archived time") LocalDateTime archivedAt,
            @Schema(description = "Status") String status
    ) {}

    @Data
    @Schema(description = "Update draft medical record request")
    public static class UpdateDraftRequest {
        @Size(max = 4000, message = "presentIllness must not exceed 4000 characters")
        @Schema(description = "Present illness")
        private String presentIllness;

        @Size(max = 4000, message = "pastHistory must not exceed 4000 characters")
        @Schema(description = "Past history")
        private String pastHistory;

        @Size(max = 4000, message = "physicalExam must not exceed 4000 characters")
        @Schema(description = "Physical exam")
        private String physicalExam;

        @Size(max = 1000, message = "chiefComplaint must not exceed 1000 characters")
        @Schema(description = "Chief complaint")
        private String chiefComplaint;

        @Schema(description = "Initial diagnosis")
        private List<String> initialDiagnosis;

        @Schema(description = "Final diagnosis")
        private List<String> finalDiagnosis;

        @Size(max = 4000, message = "doctorAdvice must not exceed 4000 characters")
        @Schema(description = "Doctor advice")
        private String doctorAdvice;

        @Schema(description = "Draft prescription items")
        private List<DraftPrescriptionItem> prescriptions;
    }

    @Schema(description = "Update medical record response")
    public record UpdateResponse(
            @Schema(description = "Record number") String recordId,
            @Schema(description = "Updated time") LocalDateTime updatedAt
    ) {}

    @Data
    @Schema(description = "Archive medical record request")
    public static class ArchiveRequest {
        @NotBlank(message = "confirmBy must not be blank")
        @Schema(description = "Confirming doctor number")
        private String confirmBy;

        @Size(max = 500, message = "confirmNote must not exceed 500 characters")
        @Schema(description = "Confirm note")
        private String confirmNote;
    }

    @Schema(description = "Archive medical record response")
    public record ArchiveResponse(
            @Schema(description = "Record number") String recordId,
            @Schema(description = "Current status") String status
    ) {}

    public record FullRecordResponse(
            String recordNo,
            Long patientId,
            Long doctorId,
            String chiefComplaint,
            String presentIllness,
            String pastHistory,
            String physicalExam,
            List<String> initialDiagnosis,
            List<String> finalDiagnosis,
            String doctorAdvice,
            String status,
            LocalDateTime createdAt,
            LocalDateTime archivedAt
    ) {}
}
