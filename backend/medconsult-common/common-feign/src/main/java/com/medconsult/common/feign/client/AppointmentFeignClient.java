package com.medconsult.common.feign.client;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.AppointmentOwnershipDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for outpatient appointment internal APIs.
 *
 * <p>/internal/** calls are sent with SERVICE JWT by common-feign's relay interceptor.
 */
@FeignClient(name = "outpatient-service", contextId = "appointmentFeignClient")
public interface AppointmentFeignClient {

    /** Internal lookup: appointment_no -> primary key and patient/doctor ownership. */
    @GetMapping("/internal/appointments/no/{appointmentNo}/ownership")
    Result<AppointmentOwnershipDTO> resolveOwnership(@PathVariable("appointmentNo") String appointmentNo);
}
