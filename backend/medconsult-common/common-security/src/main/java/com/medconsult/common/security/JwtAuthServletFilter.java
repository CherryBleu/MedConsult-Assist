package com.medconsult.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 业务服务侧的身份解析过滤器（架构文档 §4.4 信任模型落地）。
 *
 * <p>业务服务（auth/patient/outpatient/...）引入 {@code medconsult-common-security} 后，
 * 该过滤器会把当前请求的身份写入 {@link SecurityContext}，供 Controller、Service 与
 * {@link PermissionAspect} 读取。
 *
 * <p>解析策略（按优先级）：
 * <ol>
 *   <li><b>Gateway 透传头</b>：经网关的请求，网关已用 JwtAuthFilter 解析过 JWT 并注入
 *       {@code X-User-Id} / {@code X-User-Roles} / {@code X-User-Primary-Role} 等头，
 *       且剥离了原始 {@code Authorization}。此时直接信任网关头重建轻量 payload
 *       （业务端口不对外，网络隔离是信任前提，§4.4）。</li>
 *   <li><b>原始 Authorization 头</b>：直连业务端口的请求（如本地测试、内部调试），
 *       解析 {@code Bearer &lt;jwt&gt;} 走 {@link JwtCodec#parse}。</li>
 *   <li>都没有：视为匿名请求，不写 SecurityContext（由后续 @Permission / SecurityContext.requireUser 拒绝）。</li>
 * </ol>
 *
 * <p><b>注意</b>：本过滤器只解析身份，不做权限校验（那是 {@link PermissionAspect} 的职责），
 * 也不返回 401——无身份时让请求继续，由具体接口的 {@code @Permission} 决定是否拒绝。
 * 白名单接口（如 /actuator/**、/api/v1/auth/login）天然没有 @Permission，匿名也能访问。
 *
 * <p>排除路径：{@code /actuator/**} 不解析身份（健康检查）。
 */
public class JwtAuthServletFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthServletFilter.class);

    /** Gateway 注入的身份头（与 medconsult-gateway JwtAuthFilter 一致） */
    private static final String HDR_USER_ID = "X-User-Id";
    private static final String HDR_USER_ROLES = "X-User-Roles";
    private static final String HDR_USER_PRIMARY_ROLE = "X-User-Primary-Role";
    private static final String HDR_USER_PATIENT_ID = "X-User-Patient-Id";
    private static final String HDR_USER_DOCTOR_ID = "X-User-Doctor-Id";
    private static final String HDR_USER_NO = "X-User-No";
    /**
     * 服务身份头。AuthRelayInterceptor 发的是 X-Caller-Service（架构文档 §2.4），
     * 早期文档用 X-Service-Code——此处同时兼容两个头名，取先非空者。
     */
    private static final String HDR_SERVICE_CODE = "X-Service-Code";
    private static final String HDR_CALLER_SERVICE = "X-Caller-Service";
    private static final String HDR_AUTHORIZATION = "Authorization";

    /**
     * request attribute key —— 必须与 {@link SecurityContext#ATTR_PAYLOAD} 一致。
     * SecurityContext 用同一 key 读，filter 在 dispatcher servlet 之前执行所以直接写 request，
     * 不能依赖 RequestContextHolder（它由 dispatcher servlet 初始化）。
     */
    static final String SECURITY_PAYLOAD_ATTR = SecurityContext.PAYLOAD_ATTR_KEY;

    private final JwtCodec jwtCodec;

    public JwtAuthServletFilter(JwtCodec jwtCodec) {
        this.jwtCodec = jwtCodec;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            JwtPayload payload = resolveFromGatewayHeaders(request);
            String source = "gateway-headers";
            if (payload == null) {
                payload = resolveFromAuthorization(request);
                source = "authorization";
            }
            if (payload != null) {
                // 直接写 request attribute（不能用 SecurityContext.setPayload——它依赖
                // RequestContextHolder，而后者在 dispatcher servlet 之前的 filter 阶段尚未初始化）。
                // request attribute 天然请求作用域，dispatcher servlet 复用同一 request 对象，
                // controller 阶段 SecurityContext.getPayload() 读的就是这里写的值。
                request.setAttribute(SECURITY_PAYLOAD_ATTR, payload);
                log.trace("身份解析成功 path={} source={} userId={} roles={}",
                        request.getRequestURI(), source, payload.userId(), payload.roles());
            } else {
                log.trace("无身份信息 path={}（匿名请求）", request.getRequestURI());
            }
        } catch (Exception e) {
            // 解析失败不阻断请求（让 @Permission / SecurityContext.requireUser 决定），
            // 但记录便于排查。典型场景：直连时 token 过期/无效。
            log.debug("身份解析失败 path={} reason={}", request.getRequestURI(), e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 策略 1：信任网关注入的 X-User-* 头重建 payload（轻量，无需重新解析 JWT）。
     */
    private JwtPayload resolveFromGatewayHeaders(HttpServletRequest request) {
        String userIdStr = request.getHeader(HDR_USER_ID);
        // 服务身份头：兼容 X-Caller-Service（AuthRelayInterceptor 发）和 X-Service-Code（早期命名）
        String serviceCode = request.getHeader(HDR_CALLER_SERVICE);
        if (serviceCode == null || serviceCode.isBlank()) {
            serviceCode = request.getHeader(HDR_SERVICE_CODE);
        }

        // 服务身份（/internal/* 调用方）
        if (serviceCode != null && !serviceCode.isBlank()) {
            return new JwtPayload(
                    JwtPayload.SubjectType.SERVICE,
                    null,
                    serviceCode,
                    serviceCode,
                    Collections.emptyList(),
                    null,
                    null,
                    null,
                    null,
                    Collections.emptyList(),
                    null,
                    null
            );
        }

        // 用户身份（网关解析后的标准透传）
        if (userIdStr != null && !userIdStr.isBlank()) {
            Long userId;
            try {
                userId = Long.valueOf(userIdStr);
            } catch (NumberFormatException e) {
                return null;
            }
            String primaryRole = request.getHeader(HDR_USER_PRIMARY_ROLE);
            List<String> roles = parseCsvHeader(request.getHeader(HDR_USER_ROLES));
            String name = request.getHeader("X-User-Name");
            // 透传 patient/doctor 关联主键（网关 JwtAuthFilter 注入），供业务侧 SELF 数据范围校验
            Long patientId = parseLongHeader(request.getHeader(HDR_USER_PATIENT_ID));
            Long doctorId = parseLongHeader(request.getHeader(HDR_USER_DOCTOR_ID));
            // 透传用户业务编号（userNo），供通知等按业务编号关联的服务匹配 receiver_id 等
            String userNo = request.getHeader(HDR_USER_NO);
            return new JwtPayload(
                    JwtPayload.SubjectType.USER,
                    userId,
                    null,
                    name,
                    roles,
                    primaryRole,
                    patientId,
                    doctorId,
                    userNo,
                    Collections.emptyList(),
                    null,
                    null
            );
        }
        return null;
    }

    /**
     * 策略 2：直连场景，解析原始 Authorization: Bearer JWT。
     */
    private JwtPayload resolveFromAuthorization(HttpServletRequest request) {
        String auth = request.getHeader(HDR_AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        String token = auth.substring(7);
        if (jwtCodec == null) {
            return null;
        }
        return jwtCodec.parse(token);
    }

    private static List<String> parseCsvHeader(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** 头值 → Long，非法/缺失返回 null（身份头可能未透传，不应抛异常阻断请求） */
    private static Long parseLongHeader(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
