package com.medconsult.ai.security;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.common.redis.RateLimiter;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRateLimitFilterTest {
    private RateLimiter rateLimiter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimiter.class);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilterShouldSkipAiPathsWhenRateLimitIsDisabled() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(rateLimiter,
                properties(false, 10, 30, true, Map.of(), redis("medical:")));
        MockHttpServletRequest request = request("POST", "/api/v1/ai/triage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiter, never()).acquire(anyString(), anyInt(), any(Duration.class));
    }

    @Test
    void doFilterShouldUseEndpointLimitAndJwtUserIdentityForPublicAiRequest() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(rateLimiter,
                properties(true, 60, 90, false,
                        Map.of("POST /api/v1/ai/triage", 3), redis("medical:")));
        MockHttpServletRequest request = request("POST", "/api/v1/ai/triage");
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, userPayload(7L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.acquire(
                "medical:ratelimit:ai:u:7:POST:/api/v1/ai/triage", 3, Duration.ofSeconds(90)))
                .thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterShouldSkipInternalAiRequestWhenInternalLimitIsDisabled() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(rateLimiter,
                properties(true, 60, 90, false, Map.of(), redis("medical:")));
        MockHttpServletRequest request = request("POST", "/internal/ai/medical-record-summary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiter, never()).acquire(anyString(), anyInt(), any(Duration.class));
    }

    @Test
    void doFilterShouldRejectInternalServiceRequestWhenLimitIsExceeded() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(rateLimiter,
                properties(true, 5, 15, true, Map.of(), redis("medical:")));
        MockHttpServletRequest request = request("POST", "/internal/ai/medical-record-summary");
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, servicePayload("medical-record-service"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.acquire(
                "medical:ratelimit:ai:s:medical-record-service:POST:/internal/ai/medical-record-summary",
                5, Duration.ofSeconds(15))).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertEquals(429, response.getStatus());
        assertEquals("15", response.getHeader("Retry-After"));
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals("{\"code\":429001,\"message\":\"AI request rate limit exceeded\"}",
                response.getContentAsString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterShouldUseDefaultLimitWindowPrefixAndIpFallback() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(rateLimiter,
                properties(true, 0, 0, false, Map.of("GET /api/v1/ai/call-log", 0), null));
        MockHttpServletRequest request = request("GET", "/api/v1/ai/call-log");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.acquire(
                "medical:ratelimit:ai:ip:10.0.0.5:GET:/api/v1/ai/call-log",
                60, Duration.ofSeconds(60))).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterShouldSkipNonAiPath() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(rateLimiter,
                properties(true, 60, 90, true, Map.of(), redis("medical:")));
        MockHttpServletRequest request = request("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiter, never()).acquire(anyString(), anyInt(), any(Duration.class));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private static AiProperties properties(boolean enabled,
                                           int maxRequests,
                                           int windowSeconds,
                                           boolean includeInternal,
                                           Map<String, Integer> endpointLimits,
                                           AiProperties.RedisProperties redis) {
        return new AiProperties(
                null, null, null, redis, null, null, null, null, null,
                new AiProperties.RateLimitProperties(
                        enabled, maxRequests, windowSeconds, includeInternal, endpointLimits),
                null);
    }

    private static AiProperties.RedisProperties redis(String keyPrefix) {
        return new AiProperties.RedisProperties(keyPrefix, 60);
    }

    private static JwtPayload userPayload(Long userId) {
        return new JwtPayload(
                JwtPayload.SubjectType.USER, userId, null, "patient", List.of("PATIENT"), "PATIENT",
                userId, null, null, "U" + userId, List.of(), "jti", Long.MAX_VALUE);
    }

    private static JwtPayload servicePayload(String serviceCode) {
        return new JwtPayload(
                JwtPayload.SubjectType.SERVICE, null, serviceCode, serviceCode, List.of(), null,
                null, null, null, null, List.of(), "jti", Long.MAX_VALUE);
    }
}
