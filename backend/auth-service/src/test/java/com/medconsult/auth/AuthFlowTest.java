package com.medconsult.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.auth.user.entity.SysUser;
import com.medconsult.auth.user.mapper.SysUserMapper;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.PatientRegisterRequest;
import com.medconsult.common.security.JwtCodec;
import com.medconsult.common.security.JwtPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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

    @Autowired
    JwtCodec jwtCodec;

    @Autowired
    SysUserMapper userMapper;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    DataSource dataSource;

    /** mock 掉 patient-service：测试环境不连真实 patient-service，注册即建档走 mock */
    @MockBean
    PatientFeignClient patientClient;

    private static final String JWT_SECRET = "test-secret";
    private static final long LEGACY_PATIENT_USER_ID = 19001L;
    private static final String LEGACY_PATIENT_ROLE_KEY = "medconsult:auth:role:" + LEGACY_PATIENT_USER_ID;
    private static final String LEGACY_WILDCARD_REFRESH_JTI = "testlegacywildcardrefresh";
    private final List<String> redisKeysToDelete = new ArrayList<>();

    @AfterEach
    void cleanupRedisKeys() {
        if (!redisKeysToDelete.isEmpty()) {
            redis.delete(redisKeysToDelete);
        }
        redis.delete(LEGACY_PATIENT_ROLE_KEY);
        redis.delete(refreshKey(LEGACY_WILDCARD_REFRESH_JTI));
    }

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

        mvc.perform(patch("/api/v1/auth/me/phone")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("""
                                {"phone":"13900000998"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneMasked").value("139****0998"));

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

    @Test
    void login_patientAccountFromStaffEntry_rejected() throws Exception {
        // mock 建档：返回固定 patientId（患者账号 patient_id 非空，doctor/pharmacist 空）
        when(patientClient.createForRegister(any(PatientRegisterRequest.class)))
                .thenReturn(Result.ok(EntityIdDTO.of(9002L)));

        // 注册一个纯患者账号（账号须 4-32 位字母/数字/下划线，故用 bobby）
        String regBody = """
                {"account":"bobby","password":"P@ssw0rd","phone":"13900000888","name":"鲍勃","role":"PATIENT","idCard":"110101199003081234"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(jsonPath("$.code").value(0));

        // 患者账号从工作人员入口（clientType=STAFF）登录 → 应被拒（403）
        String staffLogin = """
                {"account":"bobby","password":"P@ssw0rd","clientType":"STAFF"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(staffLogin))
                .andExpect(jsonPath("$.code").value(403001));

        // 同一患者账号从患者入口（clientType=PATIENT）登录 → 应成功
        String patientLogin = """
                {"account":"bobby","password":"P@ssw0rd","clientType":"PATIENT"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(patientLogin))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists());

        // 不传 clientType（向后兼容）→ 应成功
        String noCt = """
                {"account":"bobby","password":"P@ssw0rd"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(noCt))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void login_hospitalAdminFromStaffEntry_allowed() throws Exception {
        String staffLogin = """
                {"account":"admin","password":"123456","clientType":"STAFF"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(staffLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.role").value("HOSPITAL_ADMIN"));
    }

    @Test
    void login_clientType_caseInsensitive() throws Exception {
        when(patientClient.createForRegister(any(PatientRegisterRequest.class)))
                .thenReturn(Result.ok(EntityIdDTO.of(9003L)));
        String regBody = """
                {"account":"carol","password":"P@ssw0rd","phone":"13900000777","name":"卡罗尔","role":"PATIENT","idCard":"110101199003091234"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(jsonPath("$.code").value(0));

        // 小写 staff 也应被识别并拒绝（后端 toUpperCase 容错）
        String lowerStaff = """
                {"account":"carol","password":"P@ssw0rd","clientType":"staff"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(lowerStaff))
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void login_unknownClientType_rejected() throws Exception {
        String unknownEntry = """
                {"account":"admin","password":"123456","clientType":"MOBILE"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(unknownEntry))
                .andExpect(jsonPath("$.code").value(400001));
    }

    @Test
    void login_builtinRoles_issueRoleScopedTokens() throws Exception {
        assertTokenScope(login("patient", "123456").accessToken(), "PATIENT", "ai:symptom-chat", "ai:triage");
        assertTokenScope(login("doctor", "123456").accessToken(), "DOCTOR", "ai:summary:confirm");
        assertTokenScope(login("yaofang", "123456").accessToken(), "PHARMACY_ADMIN", "drug:write");
        assertTokenScope(login("admin", "123456").accessToken(), "HOSPITAL_ADMIN", "user:manage");
    }

    @Test
    void login_usesRbacPermissionScopeWhenUserRoleRowsExist() throws Exception {
        seedRbacRole(1L, 81001L, "DOCTOR", 1, 82001L, "rbac:login-scope");

        TokenPair tokens = login("admin", "123456");
        JwtPayload payload = jwtCodec.parse(tokens.accessToken());

        assertEquals("DOCTOR", payload.primaryRole());
        assertEquals(List.of("DOCTOR"), payload.roles());
        assertEquals(List.of("rbac:login-scope"), payload.scope());
        assertFalse(payload.scope().contains("user:manage"));
        assertFalse(payload.scope().contains("ai:summary:confirm"));
    }

    @Test
    void login_filtersDisabledRbacPermissionScopeWhenUserRoleRowsExist() throws Exception {
        seedRbacRoleWithPermissionEnabled(1L, 81002L, "DOCTOR", 1,
                82002L, "rbac:disabled-login-scope", 1, 0);

        TokenPair tokens = login("admin", "123456");
        JwtPayload payload = jwtCodec.parse(tokens.accessToken());

        assertEquals("DOCTOR", payload.primaryRole());
        assertEquals(List.of("DOCTOR"), payload.roles());
        assertEquals(List.of(), payload.scope());
        assertFalse(payload.scope().contains("rbac:disabled-login-scope"));
        assertFalse(payload.scope().contains("user:manage"));
        assertFalse(payload.scope().contains("ai:summary:confirm"));
    }

    @Test
    void login_fallsBackToRedisRoleScopeWhenRbacRowsMissing() throws Exception {
        JwtPayload payload = assertTokenScope(login("yaofang", "123456").accessToken(),
                "PHARMACY_ADMIN", "drug:write");

        assertTrue(payload.roles().contains("PHARMACY_ADMIN"));
        assertFalse(payload.scope().contains("rbac:login-scope"));
    }

    @Test
    void login_doesNotFallBackToRedisWhenRbacRowsOnlyContainDisabledRoles() throws Exception {
        seedRbacRole(1L, 87001L, "DOCTOR", 1, 88001L, "rbac:disabled-role-scope", 0);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"admin","password":"123456"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void userList_usesRbacPrimaryRoleWhenRowsExist() throws Exception {
        TokenPair adminTokens = login("admin", "123456");
        seedRbacRole(1L, 89001L, "DOCTOR", 0, 89101L, "rbac:list-doctor");
        seedRbacRole(1L, 89002L, "PHARMACY_ADMIN", 1, 89102L, "rbac:list-pharmacy");

        mvc.perform(get("/api/v1/auth/users")
                        .param("keyword", "admin")
                        .header("Authorization", "Bearer " + adminTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].account").value("admin"))
                .andExpect(jsonPath("$.data.items[0].role").value("PHARMACY_ADMIN"));
    }

    @Test
    void userList_fallsBackToRedisRoleWhenRbacRowsMissing() throws Exception {
        TokenPair adminTokens = login("admin", "123456");

        mvc.perform(get("/api/v1/auth/users")
                        .param("keyword", "yaofang")
                        .header("Authorization", "Bearer " + adminTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].account").value("yaofang"))
                .andExpect(jsonPath("$.data.items[0].role").value("PHARMACY_ADMIN"));
    }

    @Test
    void userList_roleFilterUsesDisplayedRbacPrimaryRoleWhenRowsExist() throws Exception {
        TokenPair adminTokens = login("admin", "123456");
        seedRbacRole(1L, 89003L, "DOCTOR", 0, 89103L, "rbac:list-filter-doctor");
        seedRbacRole(1L, 89004L, "PHARMACY_ADMIN", 1, 89104L, "rbac:list-filter-pharmacy");

        mvc.perform(get("/api/v1/auth/users")
                        .param("keyword", "admin")
                        .param("role", "PHARMACY_ADMIN")
                        .header("Authorization", "Bearer " + adminTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].account").value("admin"))
                .andExpect(jsonPath("$.data.items[0].role").value("PHARMACY_ADMIN"));

        mvc.perform(get("/api/v1/auth/users")
                        .param("keyword", "admin")
                        .param("role", "HOSPITAL_ADMIN")
                        .header("Authorization", "Bearer " + adminTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void userList_doesNotFallBackToRedisWhenRbacRowsOnlyContainDisabledRoles() throws Exception {
        TokenPair adminTokens = login("admin", "123456");
        seedRbacRole(1L, 89005L, "DOCTOR", 1, 89105L, "rbac:list-disabled-role", 0);

        mvc.perform(get("/api/v1/auth/users")
                        .param("keyword", "admin")
                        .header("Authorization", "Bearer " + adminTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].account").value("admin"))
                .andExpect(jsonPath("$.data.items[0].role").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get("/api/v1/auth/users")
                        .param("keyword", "admin")
                        .param("role", "HOSPITAL_ADMIN")
                        .header("Authorization", "Bearer " + adminTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void refresh_preservesNarrowScopeFromRefreshToken() throws Exception {
        TokenPair tokens = login("patient", "123456");
        JwtPayload refreshPayload = assertTokenScope(tokens.refreshToken(), "PATIENT", "ai:symptom-chat");

        String refreshBody = "{\"refreshToken\":\"" + tokens.refreshToken() + "\"}";
        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        String newAccessToken = om.readTree(refreshResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
        JwtPayload accessPayload = assertTokenScope(newAccessToken, "PATIENT", "ai:symptom-chat");
        assertEquals(refreshPayload.scope(), accessPayload.scope());
    }

    @Test
    void refresh_usesCurrentRbacPermissionScopeWhenUserRoleRowsExist() throws Exception {
        TokenPair tokens = login("admin", "123456");
        seedRbacRole(1L, 85001L, "DOCTOR", 1, 86001L, "rbac:refresh-scope");

        String refreshBody = "{\"refreshToken\":\"" + tokens.refreshToken() + "\"}";
        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        String newAccessToken = om.readTree(refreshResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
        JwtPayload payload = jwtCodec.parse(newAccessToken);
        assertEquals("DOCTOR", payload.primaryRole());
        assertEquals(List.of("DOCTOR"), payload.roles());
        assertEquals(List.of("rbac:refresh-scope"), payload.scope());
    }

    @Test
    void refresh_filtersDisabledRbacPermissionScopeWhenUserRoleRowsExist() throws Exception {
        TokenPair tokens = login("admin", "123456");
        seedRbacRoleWithPermissionEnabled(1L, 85002L, "DOCTOR", 1,
                86002L, "rbac:disabled-refresh-scope", 1, 0);

        String refreshBody = "{\"refreshToken\":\"" + tokens.refreshToken() + "\"}";
        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        String newAccessToken = om.readTree(refreshResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
        JwtPayload payload = jwtCodec.parse(newAccessToken);
        assertEquals("DOCTOR", payload.primaryRole());
        assertEquals(List.of("DOCTOR"), payload.roles());
        assertEquals(List.of(), payload.scope());
        assertFalse(payload.scope().contains("rbac:disabled-refresh-scope"));
        assertFalse(payload.scope().contains("user:manage"));
        assertFalse(payload.scope().contains("ai:summary:confirm"));
    }

    @Test
    void refresh_rewritesLegacyWildcardScope() throws Exception {
        String legacyRefreshToken = jwtCodec.signUser(3L, "赵演示", List.of("PATIENT"), "PATIENT",
                3001L, null, null, "U0000003", List.of("*"), 604800, LEGACY_WILDCARD_REFRESH_JTI);
        redis.opsForValue().set(refreshKey(LEGACY_WILDCARD_REFRESH_JTI), "1", Duration.ofMinutes(5));

        String refreshBody = "{\"refreshToken\":\"" + legacyRefreshToken + "\"}";
        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        String newAccessToken = om.readTree(refreshResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
        assertTokenScope(newAccessToken, "PATIENT", "ai:symptom-chat", "ai:triage");
    }

    @Test
    void authSecurityMutations_enqueueAuditLogOutboxMessages() throws Exception {
        when(patientClient.createForRegister(any(PatientRegisterRequest.class)))
                .thenReturn(Result.ok(EntityIdDTO.of(9020L)));

        String traceId = "trace-auth-audit";
        String regBody = """
                {"account":"audit_user","password":"P@ssw0rd","phone":"13900000620","name":"Audit User","role":"PATIENT","idCard":"110101199003201234"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Trace-Id", traceId)
                        .contentType("application/json")
                        .content("""
                                {"account":"audit_user","password":"P@ssw0rd","clientType":"PATIENT"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String accessToken = om.readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
        redisKeysToDelete.add(refreshKey(jwtCodec.parse(
                om.readTree(loginResult.getResponse().getContentAsString()).at("/data/refreshToken").asText()).jti()));

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Trace-Id", traceId)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"P@ssw0rd","newPassword":"N3wP@ssw0rd"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("""
                SELECT message_no, exchange, routing_key, payload_json, status, retry_count
                FROM local_message
                WHERE routing_key = 'audit.log'
                  AND message_no LIKE 'audit:USER:%'
                ORDER BY id
                """);
        assertEquals(2, rows.size());

        List<String> actions = new ArrayList<>();
        for (java.util.Map<String, Object> row : rows) {
            assertEquals("medconsult.log", row.get("exchange"));
            assertEquals("audit.log", row.get("routing_key"));
            assertEquals("PENDING", row.get("status"));
            assertEquals(0, ((Number) row.get("retry_count")).intValue());
            assertTrue(String.valueOf(row.get("message_no")).startsWith("audit:USER:"));

            JsonNode payload = om.readTree(String.valueOf(row.get("payload_json")));
            assertEquals(traceId, payload.get("traceId").asText());
            assertEquals("USER", payload.get("resourceType").asText());
            assertEquals("SUCCESS", payload.get("result").asText());
            assertFalse(payload.get("resourceId").asText().isBlank());
            actions.add(payload.get("action").asText());
        }
        assertEquals(List.of("LOGIN", "PASSWORD_CHANGE"), actions);
    }

    @Test
    void bindPatient_reissuesRoleScopedTokens() throws Exception {
        when(patientClient.createForRegister(any(PatientRegisterRequest.class)))
                .thenReturn(Result.ok(EntityIdDTO.of(9010L)));
        seedLegacyPatientUser();

        TokenPair tokens = login("legacy_patient_scope", "P@ssw0rd");
        String bindBody = """
                {"idCard":"110101199003101234"}""";
        MvcResult bindResult = mvc.perform(post("/api/v1/auth/me/bind-patient")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType("application/json")
                        .content(bindBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.patientId").value("9010"))
                .andReturn();

        String response = bindResult.getResponse().getContentAsString();
        String accessToken = om.readTree(response).at("/data/accessToken").asText();
        String refreshToken = om.readTree(response).at("/data/refreshToken").asText();
        JwtPayload accessPayload = assertTokenScope(accessToken, "PATIENT", "ai:symptom-chat");
        JwtPayload refreshPayload = assertTokenScope(refreshToken, "PATIENT", "ai:symptom-chat");
        assertEquals(9010L, accessPayload.patientId());
        assertEquals(accessPayload.scope(), refreshPayload.scope());
    }

    @Test
    void internalAuthVerify_acceptsUserTokenAndRejectsServiceToken() throws Exception {
        String patientToken = login("patient", "123456").accessToken();

        mvc.perform(get("/internal/auth/verify")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(3))
                .andExpect(jsonPath("$.data.primaryRole").value("PATIENT"))
                .andExpect(jsonPath("$.data.roles[0]").value("PATIENT"))
                .andExpect(jsonPath("$.data.scope",
                        org.hamcrest.Matchers.hasItem("ai:symptom-chat")))
                .andExpect(jsonPath("$.data.exp").isNumber());

        mvc.perform(get("/internal/auth/verify")
                        .header("Authorization", "Bearer " + serviceToken("patient:read")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401001));
    }

    @Test
    void internalServiceVerify_acceptsActiveServiceTokenAndRejectsUserOrExcessScope() throws Exception {
        mvc.perform(get("/internal/auth/service-verify")
                        .header("Authorization", "Bearer " + serviceToken("patient:read", "drug:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.serviceCode").value("ai-service"))
                .andExpect(jsonPath("$.data.scope",
                        org.hamcrest.Matchers.hasItems("patient:read", "drug:read")))
                .andExpect(jsonPath("$.data.exp").isNumber());

        mvc.perform(get("/internal/auth/service-verify")
                        .header("Authorization", "Bearer " + login("patient", "123456").accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401001));

        mvc.perform(get("/internal/auth/service-verify")
                        .header("Authorization", "Bearer " + serviceToken("patient:read", "patient:write")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void internalUserRoles_requiresServiceTokenAndReturnsRedisFallbackRole() throws Exception {
        seedRolelessUser();

        mvc.perform(get("/internal/auth/users/{userId}/roles", 1L)
                        .header("Authorization", "Bearer " + serviceToken("patient:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.primaryRole").value("HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.roles[0]").value("HOSPITAL_ADMIN"));

        mvc.perform(get("/internal/auth/users/{userId}/roles", 19002L)
                        .header("Authorization", "Bearer " + serviceToken("patient:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.primaryRole").value("PATIENT"))
                .andExpect(jsonPath("$.data.roles[0]").value("PATIENT"));

        mvc.perform(get("/internal/auth/users/{userId}/roles", 1L)
                        .header("Authorization", "Bearer " + login("patient", "123456").accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401001));
    }

    @Test
    void internalUserRoles_readsRbacRolesBeforeRedisFallback() throws Exception {
        seedRbacRole(1L, 83001L, "DOCTOR", 0, 84001L, "rbac:doctor-read");
        seedRbacRole(1L, 83002L, "PHARMACY_ADMIN", 1, 84002L, "rbac:pharmacy-read");

        mvc.perform(get("/internal/auth/users/{userId}/roles", 1L)
                        .header("Authorization", "Bearer " + serviceToken("patient:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.primaryRole").value("PHARMACY_ADMIN"))
                .andExpect(jsonPath("$.data.roles",
                        org.hamcrest.Matchers.contains("PHARMACY_ADMIN", "DOCTOR")));
    }

    private TokenPair login(String account, String password) throws Exception {
        String loginBody = """
                {"account":"%s","password":"%s"}""".formatted(account, password);
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();
        String response = loginResult.getResponse().getContentAsString();
        TokenPair tokens = new TokenPair(
                om.readTree(response).at("/data/accessToken").asText(),
                om.readTree(response).at("/data/refreshToken").asText());
        redisKeysToDelete.add(refreshKey(jwtCodec.parse(tokens.refreshToken()).jti()));
        return tokens;
    }

    private String serviceToken(String... scopes) {
        return jwtCodec.signService("ai-service", "AI 辅助问诊服务", List.of(scopes), 3600, null);
    }

    private JwtPayload assertTokenScope(String token, String expectedRole, String... expectedScopes) {
        JwtPayload payload = jwtCodec.parse(token);
        assertEquals(expectedRole, payload.primaryRole());
        assertNotNull(payload.scope());
        assertFalse(payload.scope().isEmpty());
        assertFalse(payload.scope().contains("*"));
        for (String expectedScope : expectedScopes) {
            assertTrue(payload.scope().contains(expectedScope));
        }
        return payload;
    }

    private void seedLegacyPatientUser() {
        SysUser user = new SysUser();
        user.setId(LEGACY_PATIENT_USER_ID);
        user.setUserNo("UTESTSCOPE001");
        user.setAccount("legacy_patient_scope");
        user.setPhone("13900000666");
        user.setPasswordHash(new BCryptPasswordEncoder(10).encode("P@ssw0rd"));
        user.setName("历史患者");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        redis.opsForValue().set(LEGACY_PATIENT_ROLE_KEY, "PATIENT", Duration.ofDays(1));
    }

    private void seedRolelessUser() {
        SysUser user = new SysUser();
        user.setId(19002L);
        user.setUserNo("UTESTNOROLE001");
        user.setAccount("roleless_patient");
        user.setPhone("13900000667");
        user.setPasswordHash(new BCryptPasswordEncoder(10).encode("P@ssw0rd"));
        user.setName("无角色患者");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
    }

    private void seedRbacRole(Long userId, Long roleId, String roleCode, Integer isPrimary,
                              Long permissionId, String permissionCode) {
        seedRbacRole(userId, roleId, roleCode, isPrimary, permissionId, permissionCode, 1);
    }

    private void seedRbacRole(Long userId, Long roleId, String roleCode, Integer isPrimary,
                              Long permissionId, String permissionCode, Integer roleEnabled) {
        seedRbacRoleWithPermissionEnabled(userId, roleId, roleCode, isPrimary,
                permissionId, permissionCode, roleEnabled, null);
    }

    private void seedRbacRoleWithPermissionEnabled(Long userId, Long roleId, String roleCode, Integer isPrimary,
                                                   Long permissionId, String permissionCode, Integer roleEnabled,
                                                   Integer permissionEnabled) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO sys_role (id, role_code, role_name, enabled)
                VALUES (?, ?, ?, ?)
                """, roleId, roleCode, roleCode, roleEnabled);
        if (permissionEnabled == null) {
            jdbc.update("""
                    INSERT INTO sys_permission (id, permission_code, permission_name)
                    VALUES (?, ?, ?)
                    """, permissionId, permissionCode, permissionCode);
        } else {
            jdbc.update("""
                    INSERT INTO sys_permission (id, permission_code, permission_name, enabled)
                    VALUES (?, ?, ?, ?)
                    """, permissionId, permissionCode, permissionCode, permissionEnabled);
        }
        jdbc.update("""
                INSERT INTO sys_role_permission (id, role_id, permission_id, data_scope)
                VALUES (?, ?, ?, 'ALL')
                """, permissionId + 1000, roleId, permissionId);
        jdbc.update("""
                INSERT INTO sys_user_role (id, user_id, role_id, is_primary)
                VALUES (?, ?, ?, ?)
                """, roleId + 1000, userId, roleId, isPrimary);
    }

    private record TokenPair(String accessToken, String refreshToken) {
    }

    private static String refreshKey(String jti) {
        return "medconsult:auth:refresh:" + jti;
    }
}
