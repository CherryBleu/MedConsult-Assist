package com.medconsult.common.feign;

import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuthRelayInterceptor} 双模鉴权测试（架构文档 §2.4）。
 *
 * <p>纯单元测试，不启 Spring 容器；通过 SecurityContext.setRequestAttributes 注入身份，
 * 验证拦截器在 RequestTemplate 上写入正确的头。
 */
class AuthRelayInterceptorTest {

    private AuthRelayInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // 模拟请求上下文（SecurityContext 依赖 RequestContextHolder）
        MockHttpServletRequest req = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        // 服务 token 固定返回（模拟 auth-service 换发）
        interceptor = new AuthRelayInterceptor(() -> "svc-jwt-token");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContext.setPayload(null);
        RequestContext.clearAll();
    }

    @Test
    void userMode_relaysUserTokenAndIdentityHeaders() {
        // 模拟用户身份：医生，处方权限
        SecurityContext.setPayload(new JwtPayload(
                JwtPayload.SubjectType.USER, 1001L, null, "张医生",
                List.of("DOCTOR"), "DOCTOR", null, 3001L, null,
                List.of("prescription:write"), "jti-1", 0L));
        RequestContext.setUserToken("user-jwt-token");
        RequestContext.setCallerService("medical-record-service");
        RequestContext.setTraceId("trace-abc");

        RequestTemplate tpl = new RequestTemplate();
        interceptor.apply(tpl);

        assertEquals("Bearer user-jwt-token", header(tpl, "Authorization"));
        assertEquals("1001", header(tpl, "X-User-Id"));
        assertEquals("DOCTOR", header(tpl, "X-User-Roles"));
        assertEquals("medical-record-service", header(tpl, "X-Caller-Service"));
        assertEquals("trace-abc", header(tpl, "X-Trace-Id"));
    }

    @Test
    void serviceMode_injectsServiceTokenWhenNoUser() {
        // 无用户身份（定时任务/MQ 消费场景）
        RequestContext.setCallerService("notification-service");
        RequestContext.setTraceId("trace-xyz");

        RequestTemplate tpl = new RequestTemplate();
        interceptor.apply(tpl);

        // 应注入服务 token，而非用户 token
        assertEquals("Bearer svc-jwt-token", header(tpl, "Authorization"));
        assertNull(header(tpl, "X-User-Id"), "无用户时不写 X-User-Id");
        assertNull(header(tpl, "X-User-Roles"), "无用户时不写 X-User-Roles");
        assertEquals("notification-service", header(tpl, "X-Caller-Service"));
        assertEquals("trace-xyz", header(tpl, "X-Trace-Id"));
    }

    @Test
    void traceIdAlwaysRelayed_evenWithoutIdentity() {
        RequestContext.setTraceId("trace-only");
        RequestTemplate tpl = new RequestTemplate();
        interceptor.apply(tpl);
        assertEquals("trace-only", header(tpl, "X-Trace-Id"));
        // 服务 token 仍注入（无用户即服务模式）
        assertEquals("Bearer svc-jwt-token", header(tpl, "Authorization"));
    }

    @Test
    void serviceIdentity_doesNotTriggerUserMode() {
        // 即便有 payload 但 subjectType=SERVICE，仍走服务模式
        SecurityContext.setPayload(new JwtPayload(
                JwtPayload.SubjectType.SERVICE, null, "ai-service", "AI",
                null, null, null, null, null, List.of("patient:read"), "jti-s", 0L));

        RequestTemplate tpl = new RequestTemplate();
        interceptor.apply(tpl);

        assertNull(header(tpl, "X-User-Id"), "服务身份不写用户头");
        assertEquals("Bearer svc-jwt-token", header(tpl, "Authorization"));
    }

    private static String header(RequestTemplate tpl, String name) {
        Collection<String> values = tpl.headers().get(name);
        if (values == null || values.isEmpty()) return null;
        return values.iterator().next();
    }
}
