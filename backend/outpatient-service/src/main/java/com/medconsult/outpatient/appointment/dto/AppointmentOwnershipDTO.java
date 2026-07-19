package com.medconsult.outpatient.appointment.dto;

/**
 * Local ownership payload for the outpatient internal appointment API.
 *
 * <p>Keeping this DTO inside outpatient-service allows the module to compile and
 * run independently even when sibling common-feign artifacts are stale in the
 * local Maven repository. The JSON shape stays aligned with downstream callers.
 */
public record AppointmentOwnershipDTO(
        Long appointmentId,
        String appointmentNo,
        Long patientId,
        Long doctorId
) {
}
