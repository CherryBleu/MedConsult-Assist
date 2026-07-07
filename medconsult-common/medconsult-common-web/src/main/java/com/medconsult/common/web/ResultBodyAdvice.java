package com.medconsult.common.web;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.OffsetDateTime;

/**
 * 统一响应包装器（架构文档 §3.2）。
 *
 * <p>两种行为：
 * <ol>
 *   <li>Controller 直接返回业务数据（非 Result）→ 自动包装为 {@link Result#ok(Object)}</li>
 *   <li>Controller 已返回 {@link Result} → 不二次包装，只回填 traceId / timestamp</li>
 * </ol>
 *
 * <p>traceId 来自 {@link TraceIdFilter} 写入的 request attribute；timestamp 取当前时间。
 * 这样 Controller 无需关心链路元数据，业务代码保持干净。
 *
 * <p>排除：SSE（text/event-stream）、文件下载、Actuator 端点等不应包装的返回类型——
 * 通过 supports() 判断 Content-Type 与返回类型跳过。
 */
@RestControllerAdvice
@Order
public class ResultBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 不拦截 String.class 原始字符串（如健康检查）；不拦截 void；
        // 其他 POJO/PageResult/Result 都进入 beforeBody
        // 这里返回 true，具体过滤在 beforeBody 里按 produces 类型判断
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // 跳过非 JSON 响应（SSE / 文件下载 / 纯文本）
        if (!MediaType.APPLICATION_JSON.includes(selectedContentType)
                && !selectedContentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return body;
        }

        String traceId = resolveTraceId(request);
        OffsetDateTime now = OffsetDateTime.now();

        // 1. 已是 Result：只回填 traceId/timestamp（若为空）
        if (body instanceof Result<?> result) {
            return refillMeta(result, traceId, now);
        }

        // 2. 非 Result：包装
        // 注意：直接返回 String 时，Spring 用 StringHttpMessageConverter，
        // 需手动转 JSON 字符串才能被前端解析；此处统一走 Object→Jackson，需保证 contentType 是 JSON
        Result<Object> wrapped = Result.ok(body);
        return refillMeta(wrapped, traceId, now);
    }

    private Result<?> refillMeta(Result<?> result, String traceId, OffsetDateTime now) {
        // record 不可变，重新构造
        return new Result<>(
                result.code(),
                result.message(),
                result.data(),
                result.traceId() != null ? result.traceId() : traceId,
                result.timestamp() != null ? result.timestamp() : now
        );
    }

    private String resolveTraceId(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servlet) {
            HttpServletRequest req = servlet.getServletRequest();
            Object attr = req.getAttribute(TraceIdFilter.REQUEST_ATTR_KEY);
            if (attr instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
