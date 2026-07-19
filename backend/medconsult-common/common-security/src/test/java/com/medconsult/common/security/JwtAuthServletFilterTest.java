package com.medconsult.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JwtAuthServletFilter} 身份来源契约测试。
 */
class JwtAuthServletFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtCodec jwtCodec;
    private JwtAuthServletFilter filter;

    @BeforeEach
    void setUp() {
        jwtCodec = new JwtCodec(SECRET);
        filter = new JwtAuthServletFilter(jwtCodec);
    }

    @Test
    void callerServiceHeaderOnly_doesNotAuthenticateService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/patients/1/context");
        request.addHeader("X-Caller-Service", "spoofed-service");

        JwtPayload payload = runFilter(request);

        assertNull(payload, "X-Caller-Service 只能作为调用方元数据，不能作为服务认证来源");
    }

    @Test
    void serviceJwtAuthenticatesService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/patients/1/context");
        request.addHeader("Authorization", "Bearer " + serviceToken("patient-service"));

        JwtPayload payload = runFilter(request);

        assertNotNull(payload);
        assertTrue(payload.isService());
        assertEquals("patient-service", payload.serviceCode());
    }

    @Test
    void callerServiceHeaderDoesNotOverrideServiceJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/patients/1/context");
        request.addHeader("X-Caller-Service", "spoofed-service");
        request.addHeader("Authorization", "Bearer " + serviceToken("medical-record-service"));

        JwtPayload payload = runFilter(request);

        assertNotNull(payload);
        assertTrue(payload.isService());
        assertEquals("medical-record-service", payload.serviceCode());
    }

    @Test
    void userFeignHeadersWithCallerServiceRemainUserIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/downstream");
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Roles", "DOCTOR");
        request.addHeader("X-User-Primary-Role", "DOCTOR");
        request.addHeader("X-User-Scope", "prescription:write");
        request.addHeader("X-Caller-Service", "medical-record-service");
        request.addHeader("Authorization", "Bearer " + userToken());

        JwtPayload payload = runFilter(request);

        assertNotNull(payload);
        assertTrue(payload.isUser());
        assertEquals(42L, payload.userId());
        assertEquals(List.of("DOCTOR"), payload.roles());
        assertEquals(List.of("prescription:write"), payload.scope());
    }

    @Test
    void invalidServiceJwtWithCallerHeaderDoesNotFallbackToHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/patients/1/context");
        request.addHeader("X-Caller-Service", "spoofed-service");
        request.addHeader("Authorization", "Bearer invalid.jwt.value");

        JwtPayload payload = runFilter(request);

        assertNull(payload, "无效 SERVICE JWT 不能退回到可伪造服务头");
    }

    private JwtPayload runFilter(MockHttpServletRequest request) throws Exception {
        AtomicReference<JwtPayload> observed = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                observed.set((JwtPayload) ((HttpServletRequest) servletRequest)
                        .getAttribute(SecurityContext.PAYLOAD_ATTR_KEY)));
        return observed.get();
    }

    private String serviceToken(String serviceCode) {
        return jwtCodec.signService(serviceCode, serviceCode, List.of("*"), 3600L, "svc-jti-" + serviceCode);
    }

    private String userToken() {
        return jwtCodec.signUser(42L, "张医生", List.of("DOCTOR"), "DOCTOR",
                null, 1001L, null, "U42", List.of("prescription:write"), 3600L, "user-jti");
    }
}
