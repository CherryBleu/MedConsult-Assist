package com.medconsult.common.security;

import com.medconsult.common.core.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JwtCodec} 单元测试（不依赖 Spring 上下文）。
 *
 * <p>验证点：
 * <ul>
 *   <li>用户/服务 Token 签发 → 解析往返一致</li>
 *   <li>过期 Token 抛 UNAUTHORIZED</li>
 *   <li>签名错误/损坏抛 UNAUTHORIZED</li>
 *   <li>弱密钥构造被拒</li>
 * </ul>
 */
class JwtCodecTest {

    /** 64 字节密钥（满足 ≥32 要求） */
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtCodec codec;

    @BeforeEach
    void setUp() {
        codec = new JwtCodec(SECRET);
    }

    @Test
    void signAndParseUserToken_roundTrip() {
        String token = codec.signUser(
                1001L, "张三", List.of("DOCTOR", "PATIENT"), "DOCTOR",
                2001L, 3001L, List.of("prescription:write", "patient:read"),
                3600L, "jti-001");

        JwtPayload p = codec.parse(token);

        assertEquals(JwtPayload.SubjectType.USER, p.subjectType());
        assertEquals(1001L, p.userId());
        assertEquals("张三", p.name());
        assertEquals(List.of("DOCTOR", "PATIENT"), p.roles());
        assertEquals("DOCTOR", p.primaryRole());
        assertEquals(2001L, p.patientId());
        assertEquals(3001L, p.doctorId());
        assertEquals(List.of("prescription:write", "patient:read"), p.scope());
        assertEquals("jti-001", p.jti());
        assertNotNull(p.exp());
        assertTrue(p.isUser());
        assertFalse(p.isService());
    }

    @Test
    void signAndParseServiceToken_roundTrip() {
        String token = codec.signService(
                "ai-service", "AI 服务", List.of("patient:read", "drug:read"),
                7200L, "svc-jti-001");

        JwtPayload p = codec.parse(token);

        assertEquals(JwtPayload.SubjectType.SERVICE, p.subjectType());
        assertNull(p.userId());
        assertEquals("ai-service", p.serviceCode());
        assertEquals("AI 服务", p.name());
        assertEquals(List.of("patient:read", "drug:read"), p.scope());
        assertEquals("svc-jti-001", p.jti());
        assertTrue(p.isService());
        assertFalse(p.isUser());
    }

    @Test
    void expiredToken_throwsUnauthorized() throws InterruptedException {
        // ttl = 1 秒，等待过期
        String token = codec.signUser(1L, "x", List.of("PATIENT"), "PATIENT",
                null, null, List.of(), 1L, null);
        Thread.sleep(1100); // 过期

        BusinessException ex = assertThrows(BusinessException.class, () -> codec.parse(token));
        assertEquals(com.medconsult.common.core.ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    void tamperedToken_throwsUnauthorized() {
        String token = codec.signUser(1L, "x", List.of("PATIENT"), "PATIENT",
                null, null, List.of(), 3600L, null);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        BusinessException ex = assertThrows(BusinessException.class, () -> codec.parse(tampered));
        assertEquals(com.medconsult.common.core.ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void differentSecrets_verifyFails() {
        JwtCodec other = new JwtCodec(SECRET + "different-bytes-here!!!"); // 另一 ≥32 字节密钥
        String token = codec.signUser(1L, "x", List.of(), "PATIENT", null, null, List.of(), 3600L, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> other.parse(token));
        assertEquals(com.medconsult.common.core.ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void weakSecret_rejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new JwtCodec("short"));
        assertThrows(IllegalArgumentException.class, () -> new JwtCodec(null));
    }

    @Test
    void payloadHelpers_hasPermissionAndRole() {
        JwtPayload p = new JwtPayload(
                JwtPayload.SubjectType.USER, 1L, null, "x",
                List.of("DOCTOR"), "DOCTOR", null, null,
                List.of("prescription:write"), "j", 0L);

        assertTrue(p.hasPermission("prescription:write"));
        assertFalse(p.hasPermission("drug:delete"));
        assertTrue(p.hasRole("DOCTOR"));
        assertFalse(p.hasRole("PATIENT"));
    }

    @Test
    void wildcardScope_grantsAll() {
        JwtPayload p = new JwtPayload(
                JwtPayload.SubjectType.USER, 1L, null, "x",
                List.of(), null, null, null, List.of("*"), "j", 0L);
        assertTrue(p.hasPermission("anything:whatever"));
    }
}
