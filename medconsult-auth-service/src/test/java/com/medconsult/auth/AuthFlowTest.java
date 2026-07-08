package com.medconsult.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证全流程集成测试：H2 + Redis（无 Nacos，排除 discovery/config 避免连接开销）。
 *
 * <p>覆盖《接口文档》§2.1 全部 5 接口 + 关键分支：
 * <ul>
 *   <li>注册成功 + 重复账号拒绝</li>
 *   <li>登录成功 + 密码错误 + 不存在账号</li>
 *   <li>当前用户（me）+ 手机脱敏</li>
 *   <li>刷新 token</li>
 *   <li>登出后再刷新应失败</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@org.springframework.transaction.annotation.Transactional
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private static final String JWT_SECRET = "test-secret";

    @Test
    void fullAuthFlow_registerLoginMeRefreshLogout() throws Exception {
        // 1. 注册
        String regBody = """
                {"account":"alice","password":"P@ssw0rd","phone":"13800000001","name":"爱丽丝","role":"PATIENT"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("爱丽丝"))
                .andExpect(jsonPath("$.data.userId").exists());

        // 2. 重复账号注册 → 冲突
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已存在")));

        // 3. 登录
        String loginBody = """
                {"account":"alice","password":"P@ssw0rd"}""";
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = om.readTree(responseBody).at("/data/accessToken").asText();
        String refreshToken = om.readTree(responseBody).at("/data/refreshToken").asText();

        // 4. 密码错误
        String badPwd = """
                {"account":"alice","password":"wrong"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(badPwd))
                .andExpect(jsonPath("$.code").value(401001));

        // 5. 不存在的账号
        String noUser = """
                {"account":"nobody","password":"x"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(noUser))
                .andExpect(jsonPath("$.code").value(401001));

        // 6. 当前用户信息 + 手机脱敏
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("爱丽丝"))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****0001"))
                .andExpect(jsonPath("$.data.userId").exists());

        // 7. 刷新 token
        String refreshBody = "{\"refreshToken\":\"" + refreshToken + "\"}";
        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();
        String newAccessToken = om.readTree(refreshResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
        // 新 token 也能用
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());

        // 8. 登出
        mvc.perform(post("/api/v1/auth/logout").contentType("application/json").content(refreshBody))
                .andExpect(jsonPath("$.code").value(0));

        // 9. 登出后再用原 refresh 刷新 → 应失败（黑名单生效）
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(refreshBody))
                .andExpect(jsonPath("$.code").value(401001));
    }

    @Test
    void register_missingAccountAndPassword_rejected() throws Exception {
        String bad = """
                {"name":"无名氏"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(bad))
                .andExpect(jsonPath("$.code").value(400001));
    }
}
