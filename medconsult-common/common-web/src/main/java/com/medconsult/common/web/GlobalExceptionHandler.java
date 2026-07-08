package com.medconsult.common.web;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器（架构文档 §3.2）。
 *
 * <p>把各类异常转成 {@link Result}，HTTP 状态码取自对应 {@link ErrorCode#getHttpStatus()}。
 * 这样：
 * <ul>
 *   <li>业务代码只管抛 {@link BusinessException}，无需手写 try-catch</li>
 *   <li>前端拿到统一结构 + 正确 HTTP 状态码，便于区分 4xx/5xx</li>
 *   <li>5xx 异常落 ERROR 日志（含 traceId），4xx 业务异常落 WARN 日志</li>
 * </ul>
 *
 * <p>注意：{@code @RestControllerAdvice} 会捕获所有 @RestController 的异常。若某接口
 * 需要返回非 Result 结构（如 SSE、文件下载），用 {@code @ExceptionHandler} 局部覆盖或
 * 不返回 Result 类型即可绕过。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ===== 业务异常：按 ErrorCode 映射 HTTP 状态 =====

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode ec = ex.getErrorCode();
        int http = ec.getHttpStatus();
        Result<Void> body = Result.fail(ec, ex.getMessage());

        // 业务异常（4xx）记 WARN，便于排查但不刷 ERROR
        if (http >= 500) {
            log.error("[{}] 业务异常 5xx: ec={}, msg={}", request.getRequestURI(), ec, ex.getMessage(), ex);
        } else {
            log.warn("[{}] 业务拒绝: ec={}, msg={}", request.getRequestURI(), ec, ex.getMessage());
        }
        return ResponseEntity.status(http).body(body);
    }

    // ===== 参数校验失败：400 + 字段级 detail =====

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, Object>>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[{}] 参数校验失败: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(400).body(failWithDetail(fieldErrors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Map<String, Object>>> handleBind(BindException ex, HttpServletRequest request) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[{}] 表单绑定校验失败: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(400).body(failWithDetail(fieldErrors));
    }

    /**
     * 构造带字段级错误详情的失败响应。data 字段为 {field: message} 映射，供前端做字段标红。
     */
    private static Result<Map<String, Object>> failWithDetail(Map<String, Object> fieldErrors) {
        return new Result<>(
                ErrorCode.PARAM_ERROR.getCode(),
                "请求参数校验失败",
                fieldErrors,
                null, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("[{}] 缺少必填参数: {}", request.getRequestURI(), ex.getParameterName());
        Result<Void> body = Result.fail(ErrorCode.PARAM_ERROR, "缺少必填参数: " + ex.getParameterName());
        return ResponseEntity.status(400).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("[{}] 参数类型不匹配: {}={}", request.getRequestURI(), ex.getName(), ex.getValue());
        Result<Void> body = Result.fail(ErrorCode.PARAM_ERROR, "参数类型不匹配: " + ex.getName());
        return ResponseEntity.status(400).body(body);
    }

    /**
     * Spring 6.1+ 统一的方法级校验失败（@RequestParam / @PathVariable 上的 @NotBlank 等）。
     * 不处理会落 Spring 默认错误页（空 body 或 BasicErrorController 的 {timestamp,status,error}）。
     *
     * <p>不同 Spring 6.x 小版本 API 略有差异，此处只用 {@link Exception#getMessage()} 兜底，
     * 避免绑定具体 getter（如 getParameterValidationResults 在部分版本不存在）。
     */
    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(
            org.springframework.web.method.annotation.HandlerMethodValidationException ex, HttpServletRequest request) {
        log.warn("[{}] 方法参数校验失败: {}", request.getRequestURI(), ex.getMessage());
        Result<Void> body = Result.fail(ErrorCode.PARAM_ERROR,
                "请求参数校验失败" + (ex.getMessage() != null ? ": " + ex.getMessage() : ""));
        return ResponseEntity.status(400).body(body);
    }

    /**
     * Jakarta Bean Validation 在 service 层或非 controller 触发时的兜底。
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, Object>>> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.put(v.getPropertyPath().toString(), v.getMessage()));
        log.warn("[{}] Bean 校验失败: {}", request.getRequestURI(), errors);
        return ResponseEntity.status(400).body(failWithDetail(errors));
    }

    // ===== 资源不存在：404 =====

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        Result<Void> body = Result.fail(ErrorCode.NOT_FOUND, "接口不存在: " + ex.getRequestURL());
        return ResponseEntity.status(404).body(body);
    }

    // ===== 兜底：未识别异常 → 500 =====

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Throwable ex, HttpServletRequest request) {
        log.error("[{}] 未捕获异常", request.getRequestURI(), ex);
        Result<Void> body = Result.fail(ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.status(500).body(body);
    }
}
