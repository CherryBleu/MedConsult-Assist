package com.medconsult.common.security;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 编解码器（架构文档 §4.2）。
 *
 * <p>用 HS256（HMAC-SHA）对称签名——服务间共享密钥，部署期由 Nacos 配置 + KMS 注入，
 * 不入仓库。密钥长度必须 ≥ 32 字节（jjwt 强制要求，防弱密钥）。
 *
 * <p>为什么不非对称（RS256）：本系统服务间同属一个信任域（内网，§10.2），对称密钥更简单、
 * 验签更快；服务向外暴露的网关层才是信任边界。
 *
 * <p>claim 约定（与 {@link JwtPayload} 字段一一映射）：
 * <pre>
 *   sub        = userId 或 serviceCode
 *   typ        = "USER" / "SERVICE"（SubjectType）
 *   name       = 主体名
 *   roles      = 角色码列表（claim 数组）
 *   primary    = 主角色
 *   pid / did  = patientId / doctorId
 *   scope      = 权限点列表
 * </pre>
 */
public class JwtCodec {

    /** claim keys（短名，压缩 token 体积） */
    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_PRIMARY = "primary";
    static final String CLAIM_PATIENT_ID = "pid";
    static final String CLAIM_DOCTOR_ID = "did";
    static final String CLAIM_SCOPE = "scope";
    static final String CLAIM_SERVICE = "svc";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    /**
     * @param secret HS256 共享密钥，≥ 32 字节。建议 64 字节随机串。
     */
    public JwtCodec(String secret) {
        if (secret == null) {
            throw new IllegalArgumentException("JWT secret 不能为空");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret 长度不足：" + bytes.length + " 字节，至少需 " + MIN_SECRET_BYTES
                            + " 字节（防弱密钥）");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 签发用户 Token。
     *
     * @param ttlSeconds 有效期（秒）
     * @param jti        唯一 ID（登出黑名单键；null 则自动生成）
     */
    public String signUser(Long userId, String name, List<String> roles, String primaryRole,
                           Long patientId, Long doctorId, List<String> scope,
                           long ttlSeconds, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, JwtPayload.SubjectType.USER.name())
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PRIMARY, primaryRole)
                .claim(CLAIM_PATIENT_ID, patientId)
                .claim(CLAIM_DOCTOR_ID, doctorId)
                .claim(CLAIM_SCOPE, scope)
                .id(jti != null ? jti : java.util.UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 签发服务 Token（服务身份，AI_SERVICE 等，架构文档 §4.2）。
     */
    public String signService(String serviceCode, String name, List<String> scope,
                              long ttlSeconds, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(serviceCode)
                .claim(CLAIM_TYPE, JwtPayload.SubjectType.SERVICE.name())
                .claim(CLAIM_SERVICE, serviceCode)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_SCOPE, scope)
                .id(jti != null ? jti : java.util.UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验签名 + 过期。失败抛 {@link BusinessException}（UNAUTHORIZED），
     * 由 common-web 的 GlobalExceptionHandler 统一转 401。
     */
    @SuppressWarnings("unchecked")
    public JwtPayload parse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            JwtPayload.SubjectType type = JwtPayload.SubjectType.valueOf(
                    c.get(CLAIM_TYPE, String.class));

            Long exp = c.getExpiration() != null ? c.getExpiration().toInstant().getEpochSecond() : null;

            if (type == JwtPayload.SubjectType.SERVICE) {
                return new JwtPayload(
                        type,
                        null,
                        c.get(CLAIM_SERVICE, String.class),
                        c.get(CLAIM_NAME, String.class),
                        null, null, null, null,
                        asStringList(c.get(CLAIM_SCOPE)),
                        c.getId(),
                        exp
                );
            }
            // USER
            return new JwtPayload(
                    type,
                    parseLong(c.getSubject()),
                    null,
                    c.get(CLAIM_NAME, String.class),
                    asStringList(c.get(CLAIM_ROLES)),
                    c.get(CLAIM_PRIMARY, String.class),
                    parseLong(c.get(CLAIM_PATIENT_ID)),
                    parseLong(c.get(CLAIM_DOCTOR_ID)),
                    asStringList(c.get(CLAIM_SCOPE)),
                    c.getId(),
                    exp
            );
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 无效或已损坏");
        }
    }

    private static List<String> asStringList(Object o) {
        if (o == null) return null;
        if (o instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(o));
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return parseLong(String.valueOf(o));
    }
}
