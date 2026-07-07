package com.medconsult.common.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务异常基类。业务代码遇到不可继续的情况时抛出，由 common-web 的全局异常处理器
 * 统一转成 {@link Result} 并设置对应 HTTP 状态码。
 *
 * <p>用法：
 * <pre>
 *   throw new BusinessException(ErrorCode.NOT_FOUND, "患者档案不存在: " + patientId);
 *   throw new BusinessException(ErrorCode.CONFLICT).with("currentStatus", "COMPLETED");
 *   throw new BusinessException(ErrorCode.PARAM_ERROR, map -> map.put("field", "idNo"), e);
 * </pre>
 *
 * <p>更具体的业务异常（如 AuthException、StockNotEnoughException）建议继承本类，
 * 不必每个都重复错误码字段。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> detail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * 带 detail 的构造（用于校验失败等需要返回字段级错误信息的场景）。
     *
     * @param errorCode 错误码
     * @param detail    附加详情，会原样写入 Result.data（只读快照）
     */
    public BusinessException(ErrorCode errorCode, Map<String, Object> detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 附加详情（不可变副本）。供客户端做字段级错误展示。
     */
    public Map<String, Object> getDetail() {
        return detail;
    }

    /**
     * 流式补充单条 detail，返回新异常（原异常不变）。便于链式写法。
     */
    public BusinessException with(String key, Object value) {
        Map<String, Object> merged = detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail);
        merged.put(key, value);
        return new BusinessException(errorCode, merged);
    }
}
