package com.medconsult.patient;

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
 * 患者档案全流程集成测试：H2 内存库（无 Nacos，排除 discovery/config 避免连接开销）。
 *
 * <p>覆盖《接口文档》§2.2 全部 5 接口 + 关键分支：
 * <ul>
 *   <li>创建患者 + 重复证件号拒绝（409）+ 证件号/手机号都空拒绝（400）</li>
 *   <li>查询详情 + 脱敏（idNoMasked/phoneMasked）</li>
 *   <li>分页查询</li>
 *   <li>更新档案</li>
 *   <li>状态流转（ACTIVE → DISABLED）</li>
 *   <li>对内接口 /internal/patients/{id}/context + /allergies</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        // id_no 加密 key（32 字节全 0 的 Base64，测试用），让 CryptoHolder 装配、TypeHandler 加解密生效
        "medconsult.crypto.id-no.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        // 测试用 H2 内存库（MySQL dialect 模式），不连真实 MySQL 避免污染数据
        "spring.datasource.url=jdbc:h2:mem:medconsult_patient_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // 禁用 Nacos discovery/config，避免测试连接 Nacos
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@org.springframework.transaction.annotation.Transactional
class PatientFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void fullPatientFlow_createDetailListUpdateStatus() throws Exception {
        // 1. 创建患者档案
        String createBody = """
                {"name":"张三","gender":"MALE","birthDate":"1988-05-12",
                 "idType":"ID_CARD","idNo":"110101198805120011","phone":"13800000001",
                 "address":"北京市朝阳区示例路 1 号",
                 "allergies":["青霉素"],"pastMedicalHistory":["高血压"],
                 "familyHistory":["糖尿病家族史"],
                 "emergencyContact":{"name":"李四","relation":"配偶","phone":"13800000002"}}""";
        MvcResult createResult = mvc.perform(post("/api/v1/patients")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.patientId").exists())
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String patientNo = om.readTree(createResult.getResponse().getContentAsString())
                .at("/data/patientId").asText();

        // 2. 重复证件号 → 冲突 409
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(createBody))
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已存在")));

        // 3. 证件号和手机号都空 → 参数错误 400
        String badBody = """
                {"name":"无名氏","gender":"UNKNOWN"}""";
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(badBody))
                .andExpect(jsonPath("$.code").value(400001));

        // 4. 查询详情 + 脱敏
        mvc.perform(get("/api/v1/patients/" + patientNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(patientNo))
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.gender").value("MALE"))
                .andExpect(jsonPath("$.data.idNoMasked").value("110101********0011"))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****0001"))
                .andExpect(jsonPath("$.data.allergies[0]").value("青霉素"))
                .andExpect(jsonPath("$.data.pastMedicalHistory[0]").value("高血压"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 5. 分页查询
        mvc.perform(get("/api/v1/patients").param("page", "1").param("pageSize", "10").param("keyword", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].patientId").value(patientNo))
                .andExpect(jsonPath("$.data.items[0].name").value("张三"))
                .andExpect(jsonPath("$.data.items[0].phoneMasked").value("138****0001"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));

        // 6. 更新档案
        String updateBody = """
                {"phone":"13800000009","address":"北京市海淀区示例路 9 号",
                 "allergies":["青霉素","头孢类"],"pastMedicalHistory":["高血压","慢性胃炎"]}""";
        mvc.perform(put("/api/v1/patients/" + patientNo).contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.patientId").value(patientNo))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        // 验证更新生效（详情手机号脱敏后为 138****0009）
        mvc.perform(get("/api/v1/patients/" + patientNo))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****0009"))
                .andExpect(jsonPath("$.data.allergies[1]").value("头孢类"));

        // 7. 状态流转 ACTIVE → DISABLED
        String statusBody = """
                {"status":"DISABLED","reason":"重复档案，已完成合并"}""";
        mvc.perform(patch("/api/v1/patients/" + patientNo + "/status").contentType("application/json").content(statusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(patientNo))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        // 非法状态 → 400
        String badStatus = """
                {"status":"BANNED","reason":"x"}""";
        mvc.perform(patch("/api/v1/patients/" + patientNo + "/status").contentType("application/json").content(badStatus))
                .andExpect(jsonPath("$.code").value(400001));
    }

    @Test
    void internalContextAndAllergies_returnPatientData() throws Exception {
        // 先建一个患者，拿到 patient_no，再查主键 id 用于内部接口
        String createBody = """
                {"name":"李四","gender":"FEMALE","birthDate":"1990-01-01",
                 "idType":"ID_CARD","idNo":"110101199001010028","phone":"13900000003",
                 "allergies":["青霉素","阿司匹林"],"pastMedicalHistory":["哮喘"]}}""";
        MvcResult createResult = mvc.perform(post("/api/v1/patients")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        String patientNo = om.readTree(createResult.getResponse().getContentAsString())
                .at("/data/patientId").asText();

        // 对内接口需要 BIGINT 主键 id（非 patient_no）。对外接口不暴露 id，
        // 这里通过 JdbcTemplate 直接查 patient_no → id（测试专用，不污染业务代码）。
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                ctx.getBean(javax.sql.DataSource.class));
        Long patientId = jdbc.queryForObject(
                "SELECT id FROM patient WHERE patient_no = ?", Long.class, patientNo);

        // /internal/patients/{id}/context（需带 X-Caller-Service 头通过 requireService 鉴权）
        mvc.perform(get("/internal/patients/" + patientId + "/context")
                        .header("X-Caller-Service", "test-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.patientId").value(patientId))
                .andExpect(jsonPath("$.data.name").value("李*"))   // 姓名脱敏
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.allergies[0]").value("青霉素"))
                .andExpect(jsonPath("$.data.allergies[1]").value("阿司匹林"))
                .andExpect(jsonPath("$.data.pastMedicalHistory[0]").value("哮喘"));

        // /internal/patients/{id}/allergies
        mvc.perform(get("/internal/patients/" + patientId + "/allergies")
                        .header("X-Caller-Service", "test-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0]").value("青霉素"))
                .andExpect(jsonPath("$.data[1]").value("阿司匹林"));

        // 不存在的主键 → context 降级返回 empty（不抛异常）
        mvc.perform(get("/internal/patients/9999999999/context")
                        .header("X-Caller-Service", "test-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(9999999999L))
                .andExpect(jsonPath("$.data.allergies").isArray());
        mvc.perform(get("/internal/patients/9999999999/allergies")
                        .header("X-Caller-Service", "test-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext ctx;

    /**
     * DTO 格式校验回归测试：对应 review 发现的 P0 问题——name=纯数字、非法手机号、
     * 非法枚举值等都应被 Bean Validation 在 Controller 入口拦截，返回 PARAM_ERROR(400001)。
     */
    @Test
    void createPatient_rejectsInvalidFormats() throws Exception {
        // 1. name=纯数字"123" → 应被 @Pattern 拒绝（review 报告的核心问题）
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(
                        "{\"name\":\"123\",\"phone\":\"13800000001\"}"))
                .andExpect(jsonPath("$.code").value(400001));

        // 2. 非法手机号（非 1[3-9] 开头）
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(
                        "{\"name\":\"合法名\",\"phone\":\"12800000001\"}"))
                .andExpect(jsonPath("$.code").value(400001));

        // 3. 非法 gender（白名单外）
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(
                        "{\"name\":\"合法名\",\"gender\":\"OTHER\",\"phone\":\"13800000001\"}"))
                .andExpect(jsonPath("$.code").value(400001));

        // 4. 非法 idType
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(
                        "{\"name\":\"合法名\",\"idType\":\"DRIVER_LICENSE\",\"idNo\":\"12345\",\"phone\":\"13800000001\"}"))
                .andExpect(jsonPath("$.code").value(400001));

        // 5. 未来出生日期 → 应被 @Past 拒绝
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(
                        "{\"name\":\"合法名\",\"birthDate\":\"2099-01-01\",\"phone\":\"13800000001\"}"))
                .andExpect(jsonPath("$.code").value(400001));

        // 6. 重复手机号 → service 层唯一性校验，应返回 CONFLICT(409001)
        //    先建一个，再用同样手机号建第二个
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(
                        "{\"name\":\"张三\",\"phone\":\"13700000077\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/v1/patients").contentType("application/json").content(
                        "{\"name\":\"李四\",\"phone\":\"13700000077\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }
}
