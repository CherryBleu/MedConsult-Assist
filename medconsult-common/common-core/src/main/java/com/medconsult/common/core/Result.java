package com.medconsult.common.core;

import java.time.OffsetDateTime;

/**
 * 统一响应体。字段与《接口文档.md》§1「统一响应」完全对齐：
 * <pre>
 * { "code": 0, "message": "success", "data": {}, "traceId": "trace-001", "timestamp": "2026-07-06T10:30:00+08:00" }
 * </pre>
 *
 * <p>用法：
 * <ul>
 *   <li>成功：{@code Result.ok(data)} / {@code Result.ok()}</li>
 *   <li>失败：{@code Result.fail(ErrorCode.PARAM_ERROR)} / {@code Result.fail(ec, "字段 xx 缺失")}</li>
 * </ul>
 *
 * <p>{@code traceId} 与 {@code timestamp} 默认留空，由 common-web 的拦截器/序列化层在响应写出前填充
 * （traceId 来自 MDC，timestamp 取当前时间）。这样业务代码无需关心链路追踪元数据。
 *
 * @param code      业务码，0 表示成功
 * @param message   提示信息
 * @param data      业务数据，成功时填充
 * @param traceId   链路追踪 ID（跨服务串联，架构文档 §7.4）
 * @param timestamp 响应时间（ISO 8601，+08:00）
 */
public record Result<T>(
        int code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {

    // ===== 成功 =====

    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null, null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, null, null);
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), message, data, null, null);
    }

    // ===== 失败 =====

    public static <T> Result<T> fail(ErrorCode ec) {
        return new Result<>(ec.getCode(), ec.getMessage(), null, null, null);
    }

    public static <T> Result<T> fail(ErrorCode ec, String customMessage) {
        return new Result<>(ec.getCode(), customMessage, null, null, null);
    }

    /**
     * 判断是否成功（code == 0）。便于客户端/网关统一判断。
     */
    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }
}
