package com.medconsult.common.core;

import com.medconsult.common.web.RequestContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, RequestContext.traceId(), OffsetDateTime.now(ZoneOffset.ofHours(8)));
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, RequestContext.traceId(), OffsetDateTime.now(ZoneOffset.ofHours(8)));
    }
}
