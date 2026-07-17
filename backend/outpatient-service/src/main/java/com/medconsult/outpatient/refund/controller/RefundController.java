package com.medconsult.outpatient.refund.controller;

import com.medconsult.common.core.Result;
import com.medconsult.outpatient.refund.dto.RefundDTO;
import com.medconsult.outpatient.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约退款接口。
 */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "预约退款接口", description = "挂号费退款")
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/{appointmentId}/refund")
    @Operation(summary = "申请预约退款")
    public Result<RefundDTO.RefundResponse> apply(
            @Parameter(description = "预约编号", required = true) @PathVariable String appointmentId,
            @RequestBody(required = false) RefundDTO.RefundRequest req) {
        return Result.ok(refundService.apply(appointmentId, req));
    }
}
