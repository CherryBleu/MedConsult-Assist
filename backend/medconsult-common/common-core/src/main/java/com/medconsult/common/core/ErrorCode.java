package com.medconsult.common.core;

/**
 * 系统统一错误码。
 *
 * <p>取值与《接口文档.md》§1「通用错误码」表完全对齐。错误码结构：
 * <ul>
 *   <li>前 3 位 = HTTP 状态码（便于网关/客户端直接映射）</li>
 *   <li>后 3 位 = 业务序号</li>
 * </ul>
 *
 * <p>新增错误码时遵循：HTTP 段必须与 {@link #httpStatus} 一致；业务序号在同 HTTP 段内唯一。
 */
public enum ErrorCode {

    SUCCESS             (0,      "success",              200),
    PARAM_ERROR         (400001, "请求参数错误",           400),
    UNAUTHORIZED        (401001, "未登录或 Token 失效",    401),
    FORBIDDEN           (403001, "无访问权限",             403),
    NOT_FOUND           (404001, "资源不存在",             404),
    CONFLICT            (409001, "业务冲突",               409),
    INTERNAL_ERROR      (500001, "系统内部错误",           500),
    AI_EXTERNAL_FAILED  (502001, "外部 AI 服务调用失败",   502);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 对应的 HTTP 状态码。供 common-web 的全局异常处理器映射 Response 使用。
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 按 code 反查（用于解析外部传入的错误码或日志检索）。未匹配返回 null。
     */
    public static ErrorCode ofCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return null;
    }
}
