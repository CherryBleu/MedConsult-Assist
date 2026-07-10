package com.medconsult.ai.security;

import com.medconsult.ai.client.internal.AuthInternalClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.common.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InternalApiKeyFilter extends OncePerRequestFilter {
    private static final Set<String> ALL_AI_SCOPE = Set.of("ai:*", "ai:invoke", "ai:admin");
    private static final Map<String, String> REQUIRED_SCOPES = Map.ofEntries(
            Map.entry("POST /internal/ai/medication-analysis", "ai:medication-analysis"),
            Map.entry("POST /internal/ai/medication-analysis/stream", "ai:medication-analysis"),
            Map.entry("POST /internal/ai/triage", "ai:triage"),
            Map.entry("POST /internal/ai/triage/stream", "ai:triage"),
            Map.entry("POST /internal/ai/medical-record-summary", "ai:medical-record-summary"),
            Map.entry("POST /internal/ai/medical-record-summary/stream", "ai:medical-record-summary"),
            Map.entry("POST /internal/ai/report-analysis", "ai:report-analysis"),
            Map.entry("POST /internal/ai/image-detection", "ai:image-detection"),
            Map.entry("GET /internal/ai/image-detection", "ai:image-detection:read"),
            Map.entry("POST /internal/files/upload", "ai:file-upload"),
            Map.entry("POST /internal/files/upload/chunk", "ai:file-upload")
    );

    private final AiProperties properties;
    private final AuthInternalClient authClient;

    public InternalApiKeyFilter(AiProperties properties, AuthInternalClient authClient) {
        this.properties = properties;
        this.authClient = authClient;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        InternalServicePrincipal principal = verifyServiceToken(request);
        if (principal == null && legacyApiKeyEnabled() && validLegacyApiKey(request)) {
            principal = new InternalServicePrincipal(legacyCaller(request), List.copyOf(ALL_AI_SCOPE));
        }
        if (principal == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 401001, "invalid internal service credential");
            return;
        }
        String requiredScope = requiredScope(request);
        if (!hasScope(principal, requiredScope)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, 403001, "internal service scope denied");
            return;
        }
        request.setAttribute(RequestContext.INTERNAL_PRINCIPAL_ATTRIBUTE, principal);
        filterChain.doFilter(request, response);
    }

    private InternalServicePrincipal verifyServiceToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        if (authServiceVerifyEnabled()) {
            try {
                AuthInternalClient.ServiceVerifyResponse verified = authClient.verifyService(authorization);
                if (verified == null || !StringUtils.hasText(verified.serviceCode())) {
                    return null;
                }
                return new InternalServicePrincipal(verified.serviceCode(), safeScopes(verified.scope()));
            } catch (RuntimeException ex) {
                return null;
            }
        }
        String expected = properties.internal() == null ? null : properties.internal().serviceToken();
        return StringUtils.hasText(expected) && constantTimeEquals(expected, authorization.substring("Bearer ".length()))
                ? new InternalServicePrincipal(legacyCaller(request), List.copyOf(ALL_AI_SCOPE))
                : null;
    }

    private boolean validLegacyApiKey(HttpServletRequest request) {
        String expected = properties.internal() == null ? null : properties.internal().apiKey();
        String actual = request.getHeader(RequestContext.INTERNAL_API_KEY_HEADER);
        return StringUtils.hasText(expected) && constantTimeEquals(expected, actual);
    }

    private boolean authServiceVerifyEnabled() {
        return properties.internal() != null && properties.internal().authServiceVerifyEnabled();
    }

    private boolean legacyApiKeyEnabled() {
        return properties.internal() != null && properties.internal().legacyApiKeyEnabled();
    }

    private static List<String> safeScopes(List<String> scopes) {
        return scopes == null ? List.of() : scopes;
    }

    private static boolean hasScope(InternalServicePrincipal principal, String requiredScope) {
        return requiredScope == null
                || principal.scopes().stream().anyMatch(ALL_AI_SCOPE::contains)
                || principal.hasScope(requiredScope);
    }

    private static String requiredScope(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String exact = REQUIRED_SCOPES.get(method + " " + uri);
        if (exact != null) {
            return exact;
        }
        if ("GET".equals(method) && uri.startsWith("/internal/ai/image-detection/")) {
            return "ai:image-detection:read";
        }
        return null;
    }

    private static String legacyCaller(HttpServletRequest request) {
        String caller = request.getHeader(RequestContext.CALLER_SERVICE_HEADER);
        return StringUtils.hasText(caller) ? caller : "legacy-service";
    }

    private static void writeError(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\"}");
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
