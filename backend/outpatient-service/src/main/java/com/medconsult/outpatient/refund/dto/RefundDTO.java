package com.medconsult.outpatient.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款请求/响应 DTO。
 */
public class RefundDTO {

    @Data
    @Schema(description = "申请退款请求")
    public static class RefundRequest {
        @Schema(description = "退款原因")
        private String reason;

        @Schema(description = "客户端幂等键")
        private String idempotencyKey;
    }

    @Schema(description = "退款响应")
    public record RefundResponse(
            @Schema(description = "退款单号") String refundNo,
            @Schema(description = "预约编号") String appointmentId,
            @Schema(description = "退款状态") String refundStatus,
            @Schema(description = "支付状态") String paymentStatus,
            @Schema(description = "退款提供方") String provider,
            @Schema(description = "退款金额") BigDecimal refundAmount
    ) {}
}
