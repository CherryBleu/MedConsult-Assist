package com.medconsult.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.PatientRegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证全流程集成测试：H2 + Redis（无 Nacos，排除 discovery/config 避免连接开销）。
 *
 * <p>覆盖《接口文档》§2.1 全部 5 接口 + 关键分支：
 * <ul>
 *   <li>注册成功 + 重复账号拒绝</li>
 *   <li>登录成功 + 密码错误 + 不存在账号</li>
 *   <li>当前用户（me）+ 手机脱敏（me 经 JwtAuthServletFilter 解析 Authorization 头写入 SecurityContext）</li>
 *   <li>刷新 token</li>
 *   <li>登出后再刷新应失败</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        // 测试用 H2 内存库（MySQL dialect 模式），不连真实 MySQL 避免污染数据
        "spring.datasource.url=jdbc:h2:mem:medconsult_auth_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // Redis 仍连真实容器（infra/docker-compose.yml，测试需先启动）
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

    /** mock 掉 patient-service：测试环境不连真实 patient-service，注册即建档走 mock */
    @MockBean
    PatientFeignClient patientClient;

    private static final String JWT_SECRET = "test-secret";

    @Test
    void fullAuthFlow_registerLoginMeRefreshLogout() throws Exception {
        // mock 建档：返回固定 patientId（注册即建档链路）
        when(patientClient.createForRegister(any(PatientRegisterRequest.class)))
                .thenReturn(Result.ok(EntityIdDTO.of(9001L)));

        // 1. 注册（PATIENT 需带 idCard + phone，建档由 mock 返回 patientId=9001）
        // 手机号用 13900000999 避开 DataSeeder 种子（patient 用户用 13800000001）
        String regBody = """
                {"account":"alice","password":"P@ssw0rd","phone":"13900000999","name":"爱丽丝","role":"PATIENT","idCard":"110101199003071234"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("爱丽丝"))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.patientId").value("9001"));

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
                .andExpect(jsonPath("$.data.phoneMasked").value("139****0999"))
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
