package com.medconsult.common.feign;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Feign 链路上下文接线过滤器（架构文档 §2.4）。
 *
 * <p>把当前 HTTP 请求的元数据写入 {@link RequestContext} 的 ThreadLocal，供
 * {@link AuthRelayInterceptor} 在 Feign 调用时透传：
 * <ul>
 *   <li>原始用户 token（直连场景的 Authorization 头）→ 下游可二次校验签名或审计</li>
 *   <li>traceId（从请求头或 MDC）→ 链路串联</li>
 * </ul>
 *
 * <p><b>ThreadLocal 清理（防泄漏）</b>：在 finally 里调 {@link RequestContext#clearAll()}，
 * 否则 Tomcat 线程池 / 虚拟线程复用会把上一个请求的 token/traceId 泄漏到下一个请求，
 * 导致身份串用（A 用户的 Feign 调用带上 B 用户的 token）。
 *
 * <p>只在 servlet 环境生效（@ConditionalOnClass 在 AutoConfiguration 注册时判定），
 * reactive gateway 不引入本 filter。
 *
 * <p>运行顺序：在业务 filter 之后、dispatcher servlet 之前（Feign 调用发生在 controller/service 阶段，
 * 此时 ThreadLocal 已就绪）。用 {@link Ordered#HIGHEST_PRECEDENCE} + 1，略低于 TraceIdFilter
 * （TraceIdFilter 先写好 traceId，本 filter 复用）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestContextRelayFilter extends OncePerRequestFilter {

    public static final String HDR_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 原始用户 token：直连场景从 Authorization 头取。
            // 经网关的请求 Authorization 已被剥离——那是设计意图（防业务侧误用原始 token）；
            // 但 Feign 向下游透传时，下游若需二次校验签名仍需要它。故直连场景保留，网关场景
            // 走服务 token 兜底（AuthRelayInterceptor 的 else 分支）。
            String auth = request.getHeader(HDR_AUTHORIZATION);
            if (auth != null && auth.startsWith(BEARER_PREFIX)) {
                RequestContext.setUserToken(auth.substring(BEARER_PREFIX.length()));
            }

            // 2. callerService：从 spring.application.name 注入（见 AutoConfiguration 装配）。
            //    本 filter 不重复 set——callerService 是服务级常量，由 AutoConfiguration 启动时设一次即可。
            //    此处只透传 traceId（TraceIdFilter 已写 MDC，RequestContext.getTraceId 会兜底读 MDC）。

            filterChain.doFilter(request, response);
        } finally {
            // 关键：请求结束清理 ThreadLocal，防线程复用导致身份/traceId 串用泄漏
            RequestContext.clearAll();
        }
    }
}
