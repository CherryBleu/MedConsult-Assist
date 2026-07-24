package com.medconsult.common.feign.dto;

/**
 * Internal doctor display profile used by services that only store doctor primary keys.
 */
public record DoctorProfileDTO(
        Long doctorId,
        String doctorName,
        Long departmentId,
        String departmentName
) {}
