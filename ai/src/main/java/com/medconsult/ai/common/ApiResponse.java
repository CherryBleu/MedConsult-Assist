package com.medconsult.ai.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, newTraceId(), OffsetDateTime.now(ZoneOffset.ofHours(8)));
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, newTraceId(), OffsetDateTime.now(ZoneOffset.ofHours(8)));
    }

    private static String newTraceId() {
        return "trace-" + UUID.randomUUID().toString().replace("-", "");
    }
}
