package com.medconsult.outpatient.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Doctor DTOs.
 */
public class DoctorDTO {

    @Schema(description = "Doctor list item")
    public record ListItem(
            @Schema(description = "Doctor business number") String doctorId,
            @Schema(description = "Doctor primary key") Long id,
            @Schema(description = "Doctor name") String doctorName,
            @Schema(description = "Department business number") String departmentId,
            @Schema(description = "Department name") String departmentName,
            @Schema(description = "Title") String title,
            @Schema(description = "Specialties") List<String> specialties,
            @Schema(description = "Enabled") boolean enabled,
            @Schema(description = "Registration fee") BigDecimal registrationFee
    ) {}

    @Schema(description = "Doctor detail")
    public record Detail(
            @Schema(description = "Doctor business number") String doctorId,
            @Schema(description = "Doctor primary key") Long id,
            @Schema(description = "Doctor name") String doctorName,
            @Schema(description = "Department business number") String departmentId,
            @Schema(description = "Department name") String departmentName,
            @Schema(description = "Title") String title,
            @Schema(description = "Specialties") List<String> specialties,
            @Schema(description = "Introduction") String introduction,
            @Schema(description = "Enabled") boolean enabled,
            @Schema(description = "Registration fee") BigDecimal registrationFee
    ) {}

    @Schema(description = "Create doctor request")
    public record CreateRequest(
            @Schema(description = "Doctor name") @NotBlank(message = "doctor name must not be blank") @Size(max = 50) String name,
            @Schema(description = "Department business number") @NotBlank(message = "department must not be blank") String departmentId,
            @Schema(description = "Title") @Size(max = 50) String title,
            @Schema(description = "Comma separated specialties") @Size(max = 500) String specialties,
            @Schema(description = "Introduction") @Size(max = 1000) String introduction,
            @Schema(description = "Enabled") Boolean enabled
    ) {}

    @Schema(description = "Update doctor request")
    public record UpdateRequest(
            @Schema(description = "Doctor name") @Size(max = 50) String name,
            @Schema(description = "Department business number") String departmentId,
            @Schema(description = "Title") @Size(max = 50) String title,
            @Schema(description = "Comma separated specialties") @Size(max = 500) String specialties,
            @Schema(description = "Introduction") @Size(max = 1000) String introduction,
            @Schema(description = "Enabled") Boolean enabled
    ) {}

    @Schema(description = "Doctor mutation response")
    public record MutationResponse(
            @Schema(description = "Doctor business number") String doctorId
    ) {}
}
