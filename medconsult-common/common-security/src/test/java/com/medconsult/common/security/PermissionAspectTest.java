package com.medconsult.common.security;

import com.medconsult.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link Permission} + {@link PermissionAspect} + {@link SecurityContext} 集成测试。
 *
 * <p>测试桩（{@link SecurityTestApplication} / {@link SecurityTestController} /
 * {@link SecurityTestFilterConfig}）均为顶层类，避免嵌套 @Configuration 致
 * component scan 起点异常（common-web 同款踩坑）。
 *
 * <p>验证点：
 * <ul>
 *   <li>无身份 → 401</li>
 *   <li>身份有匹配 scope → 放行</li>
 *   <li>身份 scope 不足 → 403</li>
 *   <li>角色校验（roles 声明）</li>
 *   <li>仅登录校验（code 留空）</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "medconsult.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
class PermissionAspectTest {

    @Autowired
    MockMvc mvc;

    @Test
    void noAuth_returns401() throws Exception {
        mvc.perform(get("/perm/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401001));
    }

    @Test
    void withMatchingScope_passes() throws Exception {
        mvc.perform(get("/perm/protected")
                        .header("X-Test-User", "DOCTOR")
                        .header("X-Test-Scope", "prescription:write"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void scopeInsufficient_returns403() throws Exception {
        mvc.perform(get("/perm/protected")
                        .header("X-Test-User", "PATIENT")
                        .header("X-Test-Scope", "patient:read"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void roleCheck_rejectsWrongRole() throws Exception {
        mvc.perform(get("/perm/doctor-only")
                        .header("X-Test-User", "PATIENT")
                        .header("X-Test-Scope", "prescription:write"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void roleCheck_acceptsRightRole() throws Exception {
        mvc.perform(get("/perm/doctor-only")
                        .header("X-Test-User", "DOCTOR")
                        .header("X-Test-Scope", "prescription:write"))
                .andExpect(status().isOk());
    }

    @Test
    void loginOnly_noCodeCheck_justNeedsIdentity() throws Exception {
        mvc.perform(get("/perm/login-only")
                        .header("X-Test-User", "PATIENT")
                        .header("X-Test-Scope", ""))
                .andExpect(status().isOk());
    }
}
