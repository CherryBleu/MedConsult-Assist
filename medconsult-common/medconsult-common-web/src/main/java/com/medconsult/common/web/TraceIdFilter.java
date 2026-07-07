package com.medconsult.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪 ID 过滤器（架构文档 §7.4）。
 *
 * <p>职责：
 * <ul>
 *   <li>从请求头 {@code X-Trace-Id} 取 traceId（网关或上游服务注入）</li>
 *   <li>缺失时本地生成兜底（32 位无连字符 UUID）</li>
 *   <li>写入 {@link MDC}（key = {@value #MDC_KEY}），全链路日志可串</li>
 *   <li>回写响应头 {@code X-Trace-Id}，客户端可关联请求</li>
 *   <li>作为 request attribute 暴露，供 {@code ResultBodyAdvice} 回填 Result.traceId</li>
 * </ul>
 *
 * <p>运行顺序：最高优先级，在 Spring Security / 业务过滤器之前执行，确保所有日志含 traceId。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 请求头名 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC key（日志模式 [%X{traceId}] 引用） */
    public static final String MDC_KEY = "traceId";

    /** request attribute key，供 BodyAdvice 读取 */
    public static final String REQUEST_ATTR_KEY = TraceIdFilter.class.getName() + ".traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 取或生成 traceId
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
        }
        // 规整：去首尾空白、限长（防恶意超长头）
        traceId = traceId.trim();
        if (traceId.length() > 64) {
            traceId = traceId.substring(0, 64);
        }

        // 2. 写 MDC + request attribute + 响应头
        MDC.put(MDC_KEY, traceId);
        request.setAttribute(REQUEST_ATTR_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须 finally 清理 MDC，否则线程（Tomcat 线程/虚拟线程复用）会泄漏到下一个请求
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 生成本地兜底 traceId。32 位无连字符 UUID，足够全局唯一。
     */
    protected String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
