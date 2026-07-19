package com.medconsult.common.feign.dto;

/**
 * Appointment ownership context for internal cross-service authorization checks.
 *
 * <p>Used by medical-record-service to validate that an appointment_no belongs to the
 * requested patient and doctor before creating clinical documents.
 */
public record AppointmentOwnershipDTO(
        Long appointmentId,
        String appointmentNo,
        Long patientId,
        Long doctorId
) {
}
