package com.medconsult.common.security;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContextTest {

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requireServiceWithScope_allowsMatchingScope() {
        SecurityContext.setPayload(servicePayload("ai-service", List.of("patient:read")));

        JwtPayload payload = SecurityContext.requireService("patient:read");

        assertEquals("ai-service", payload.serviceCode());
    }

    @Test
    void requireServiceWithScope_allowsWildcardScope() {
        SecurityContext.setPayload(servicePayload("medical-record-service", List.of("*")));

        JwtPayload payload = SecurityContext.requireService("drug:write");

        assertEquals("medical-record-service", payload.serviceCode());
    }

    @Test
    void requireServiceWithScope_rejectsMissingScope() {
        SecurityContext.setPayload(servicePayload("ai-service", List.of("patient:read")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> SecurityContext.requireService("drug:write"));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("drug:write"));
    }

    @Test
    void requireServiceWithScope_rejectsUserIdentityEvenWithScope() {
        SecurityContext.setPayload(new JwtPayload(
                JwtPayload.SubjectType.USER,
                42L,
                null,
                "张医生",
                List.of("DOCTOR"),
                "DOCTOR",
                null,
                1001L,
                null,
                "U42",
                List.of("patient:read"),
                "jti-user",
                0L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> SecurityContext.requireService("patient:read"));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    private static JwtPayload servicePayload(String serviceCode, List<String> scope) {
        return new JwtPayload(
                JwtPayload.SubjectType.SERVICE,
                null,
                serviceCode,
                serviceCode,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                scope,
                "jti-" + serviceCode,
                0L);
    }
}
