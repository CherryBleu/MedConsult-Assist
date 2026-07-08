package com.medconsult.medicalrecord;

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
 * medical-record-service 全流程集成测试：H2 内存库 + Redis 16379（无 Nacos，排除 discovery/config）。
 *
 * <p>覆盖《接口文档》§2.6 病历 + 《修改建议》§2.1 处方（第 1 批 5 接口）关键路径：
 * <ul>
 *   <li>病历：创建(DRAFT) / 详情 / 列表(按 patientId 过滤) / 更新草稿 / 归档(DRAFT→ARCHIVED)</li>
 *   <li>病历归档后不可改 → CONFLICT（医疗文书不可变，§6.1）</li>
 *   <li>内部接口 /internal/medical-records/{id}/full 返回完整病历</li>
 *   <li>处方：开方(DRAFT) / 列表(按 status 过滤) / 详情(含明细) / 提交审方(DRAFT→PENDING_REVIEW)</li>
 *   <li>处方审方：PENDING_REVIEW → APPROVED；PENDING_REVIEW → REJECTED</li>
 *   <li>非法状态转移：DRAFT 直接 review → CONFLICT；REJECT 无驳回原因 → PARAM_ERROR</li>
 * </ul>
 *
 * <p>注意：medical-record-service 无只读依赖表（不像 outpatient 依赖 department/doctor 预置数据），
 * 病历/处方的 patientId/doctorId 都是业务编号串，service 层正哈希落库，故无需 @BeforeEach 预置。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        // 测试用 H2 内存库（MySQL dialect 模式），不连真实 MySQL 避免污染数据
        "spring.datasource.url=jdbc:h2:mem:medconsult_record_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // Redis 仍连真实容器（infra/docker-compose.yml，测试需先启动），处方审方锁依赖
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        // 禁用 Nacos discovery/config，避免测试连接 Nacos
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@org.springframework.transaction.annotation.Transactional
class MedicalRecordFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private static final String PATIENT_NO = "P202607060001";
    private static final String DOCTOR_NO = "D10001";
    private static final String PHARMACIST_NO = "PH2001";

    // ===== 病历域 =====

    @Test
    void createRecord_draftSuccess() throws Exception {
        String body = """
                {"patientId":"%s","doctorId":"%s","chiefComplaint":"胸闷、心悸 3 天",
                 "presentIllness":"活动后胸闷加重，休息后缓解。",
                 "pastHistory":"高血压 5 年。",
                 "physicalExam":"血压 150/95mmHg，心率 92 次/分。",
                 "initialDiagnosis":["心律失常待查","高血压"],
                 "doctorAdvice":"完善心电图检查，监测血压。"}""".formatted(PATIENT_NO, DOCTOR_NO);
        mvc.perform(post("/api/v1/medical-records").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void recordDetail_returnsFields() throws Exception {
        String recordNo = createRecord();
        mvc.perform(get("/api/v1/medical-records/" + recordNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordNo))
                .andExpect(jsonPath("$.data.chiefComplaint").value("胸闷、心悸 3 天"))
                .andExpect(jsonPath("$.data.initialDiagnosis[0]").value("心律失常待查"))
                .andExpect(jsonPath("$.data.initialDiagnosis[1]").value("高血压"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void recordList_filterByPatient() throws Exception {
        createRecord();
        mvc.perform(get("/api/v1/medical-records").param("patientId", PATIENT_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].chiefComplaint").value("胸闷、心悸 3 天"))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
    }

    @Test
    void updateDraft_onlyDraftAllowed() throws Exception {
        String recordNo = createRecord();
        // DRAFT 可改
        mvc.perform(put("/api/v1/medical-records/" + recordNo)
                        .contentType("application/json")
                        .content("{\"doctorAdvice\":\"继续监测血压，1 周后复诊。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordNo));
    }

    @Test
    void updateDraft_archivedRejected() throws Exception {
        String recordNo = createRecord();
        archiveRecord(recordNo);
        // 归档后不可改 → CONFLICT
        mvc.perform(put("/api/v1/medical-records/" + recordNo)
                        .contentType("application/json")
                        .content("{\"doctorAdvice\":\"尝试修改已归档病历\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void archive_draftToArchived() throws Exception {
        String recordNo = createRecord();
        mvc.perform(post("/api/v1/medical-records/" + recordNo + "/archive")
                        .contentType("application/json")
                        .content("{\"confirmBy\":\"D10001\",\"confirmNote\":\"病历内容已确认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordNo))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void archive_notFound() throws Exception {
        mvc.perform(post("/api/v1/medical-records/MR_NOT_EXIST/archive")
                        .contentType("application/json")
                        .content("{\"confirmBy\":\"D10001\"}"))
                .andExpect(jsonPath("$.code").value(404001));
    }

    @Test
    void internalFull_returnsComplete() throws Exception {
        String recordNo = createRecord();
        // 内部接口用 BIGINT 主键，需先查出 id（H2 直接查）
        Long id = resolveRecordId(recordNo);
        mvc.perform(get("/internal/medical-records/" + id + "/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordNo").value(recordNo))
                .andExpect(jsonPath("$.data.chiefComplaint").value("胸闷、心悸 3 天"))
                .andExpect(jsonPath("$.data.initialDiagnosis[0]").value("心律失常待查"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    // ===== 处方域 =====

    @Test
    void createPrescription_draftWithItems() throws Exception {
        String rxNo = createPrescription();
        // 验证详情含明细
        mvc.perform(get("/api/v1/prescriptions/" + rxNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].drugName").value("硝苯地平控释片"))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(210.00))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.totalFee").value(210.00));
    }

    @Test
    void prescriptionList_filterByStatus() throws Exception {
        createPrescription(); // DRAFT
        mvc.perform(get("/api/v1/prescriptions").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
        // PENDING_REVIEW 应为 0
        mvc.perform(get("/api/v1/prescriptions").param("status", "PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void submit_draftToPendingReview() throws Exception {
        String rxNo = createPrescription();
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
    }

    @Test
    void submit_nonDraftRejected() throws Exception {
        String rxNo = createPrescription();
        submitPrescription(rxNo); // DRAFT → PENDING_REVIEW
        // 已 PENDING_REVIEW 再 submit → CONFLICT
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/submit"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void review_pendingToApproved() throws Exception {
        String rxNo = createPrescription();
        submitPrescription(rxNo);
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/review")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVE\",\"pharmacistId\":\"PH2001\",\"reviewComment\":\"用药合理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").exists());
    }

    @Test
    void review_pendingToRejected() throws Exception {
        String rxNo = createPrescription();
        submitPrescription(rxNo);
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/review")
                        .contentType("application/json")
                        .content("{\"action\":\"REJECT\",\"pharmacistId\":\"PH2001\",\"rejectReason\":\"剂量超限\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void review_illegalAction_onDraft() throws Exception {
        String rxNo = createPrescription();
        // DRAFT 直接 review → CONFLICT（必须先 submit）
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/review")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVE\",\"pharmacistId\":\"PH2001\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void review_rejectWithoutReason_paramError() throws Exception {
        String rxNo = createPrescription();
        submitPrescription(rxNo);
        // REJECT 无 rejectReason → PARAM_ERROR
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/review")
                        .contentType("application/json")
                        .content("{\"action\":\"REJECT\",\"pharmacistId\":\"PH2001\"}"))
                .andExpect(jsonPath("$.code").value(400001));
    }

    // ===== 测试助手 =====

    /** 创建病历，返回 record_no */
    private String createRecord() throws Exception {
        String body = """
                {"patientId":"%s","doctorId":"%s","chiefComplaint":"胸闷、心悸 3 天",
                 "initialDiagnosis":["心律失常待查","高血压"]}""".formatted(PATIENT_NO, DOCTOR_NO);
        MvcResult r = mvc.perform(post("/api/v1/medical-records").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/recordId").asText();
    }

    /** 归档病历 */
    private void archiveRecord(String recordNo) throws Exception {
        mvc.perform(post("/api/v1/medical-records/" + recordNo + "/archive")
                        .contentType("application/json")
                        .content("{\"confirmBy\":\"D10001\",\"confirmNote\":\"确认\"}"))
                .andExpect(status().isOk());
    }

    /** 创建处方（DRAFT），返回 prescription_no */
    private String createPrescription() throws Exception {
        String recordNo = createRecord();
        String body = """
                {"recordId":"%s","patientId":"%s","doctorId":"%s","source":"OUTPATIENT",
                 "items":[{"drugName":"硝苯地平控释片","specification":"30mg*7片","dosage":"30mg",
                           "frequency":"每日一次","days":7,"quantity":7,"unit":"片","unitPrice":30.00}]}"""
                .formatted(recordNo, PATIENT_NO, DOCTOR_NO);
        MvcResult r = mvc.perform(post("/api/v1/prescriptions").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/prescriptionId").asText();
    }

    /** 提交审方（DRAFT → PENDING_REVIEW） */
    private void submitPrescription(String rxNo) throws Exception {
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/submit"))
                .andExpect(status().isOk());
    }

    /** 按 record_no 查 BIGINT 主键 id（内部接口用主键） */
    private Long resolveRecordId(String recordNo) {
        // 直接走内部 service 不便（测试用 JdbcTemplate 反而绕过 @Transactional 隔离），
        // 这里复用 mvc 拿不到 id；改用 jdbc 查。
        // 因测试类注入 DataSource 较重，此处用一个轻量手段：record_no 是 MR + base36 雪花，
        // 但我们存的是 IdWorker.getId() 的无符号 base36，无法逆推。改为通过 jdbc 查。
        return resolveIdViaJdbc("medical_record", "record_no", recordNo);
    }

    @Autowired
    javax.sql.DataSource dataSource;

    private Long resolveIdViaJdbc(String table, String noCol, String noVal) {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .queryForObject("SELECT id FROM " + table + " WHERE " + noCol + " = ?",
                        Long.class, noVal);
    }
}
