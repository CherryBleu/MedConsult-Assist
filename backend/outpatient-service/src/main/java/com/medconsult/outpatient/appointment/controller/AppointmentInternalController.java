package com.medconsult.outpatient.appointment.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.AppointmentOwnershipDTO;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal appointment APIs for cross-service authorization checks.
 *
 * <p>Callers must use SERVICE JWT. User-facing authorization remains on /api/v1/appointments.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class AppointmentInternalController {

    private final AppointmentService appointmentService;

    @GetMapping("/appointments/no/{appointmentNo}/ownership")
    public Result<AppointmentOwnershipDTO> resolveOwnership(@PathVariable String appointmentNo) {
        SecurityContext.requireService("appointment:read");
        return Result.ok(appointmentService.internalResolveOwnership(appointmentNo));
    }
}
