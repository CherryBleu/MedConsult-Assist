package com.medconsult.medicalrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.DrugFeignClient;
import com.medconsult.common.feign.client.DoctorFeignClient;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.DispenseDTO;
import com.medconsult.common.feign.dto.EntityIdDTO;
import org.junit.jupiter.api.BeforeEach;
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

    /**
     * Mock DrugFeignClient：dispense 测试不真实跨服务调 drug-service（测试环境无 Nacos discovery）。
     * 默认 stub：outbound 成功返回 flowNo + currentStock；个别测试用 reset 重写为抛库存不足。
     */
    @MockBean
    DrugFeignClient drugFeignClient;

    /**
     * Mock Patient/Doctor FeignClient：create/list 现在通过 Feign 反查真实主键（替代正哈希），
     * 测试环境无 patient/outpatient 实例，需 stub resolve 方法返回确定性 id。
     */
    @MockBean
    PatientFeignClient patientFeignClient;

    @MockBean
    DoctorFeignClient doctorFeignClient;

    private static final String PATIENT_NO = "P202607060001";
    private static final String DOCTOR_NO = "D10001";
    private static final String PHARMACIST_NO = "PH2001";

    // ===== 测试身份头（模拟网关 JwtAuthFilter 注入的 X-User-* 头） =====
    // create 时 patientFeignClient stub 把任意 patient_no 反查为 1001L，
    // 故"患者本人"身份头带 X-User-Patient-Id=1001（与记录归属一致才能通过 SELF 校验）。
    /** 医生身份（ALL 数据范围，病历域可读全部） */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder doctorAuth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", "5001")
                .header("X-User-Primary-Role", "DOCTOR")
                .header("X-User-Roles", "DOCTOR")
                .header("X-User-Doctor-Id", "2001");
    }
    /** 患者本人身份（patientId=1001，与 createRecord stub 的归属主键一致） */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder selfPatientAuth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", "6001")
                .header("X-User-Primary-Role", "PATIENT")
                .header("X-User-Roles", "PATIENT")
                .header("X-User-Patient-Id", "1001");
    }
    /** 其他患者身份（patientId=9999，与记录归属不一致 → 越权） */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder otherPatientAuth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", "6002")
                .header("X-User-Primary-Role", "PATIENT")
                .header("X-User-Roles", "PATIENT")
                .header("X-User-Patient-Id", "9999");
    }

    /**
     * 统一 stub Feign 反查：任意业务编号 → 固定确定性主键。
     * <p>用编号的稳定派生值（避免与 H2 预置数据冲突），让 create/list 行为可预期。
     * 个别测试可在方法内 reset 重写（如测 NOT_FOUND 路径）。
     */
    @BeforeEach
    void stubFeignIdResolution() {
        when(patientFeignClient.resolveId(any()))
                .thenReturn(Result.ok(EntityIdDTO.of(1001L)));
        when(doctorFeignClient.resolveDoctorId(any()))
                .thenReturn(Result.ok(EntityIdDTO.of(2001L)));
        when(doctorFeignClient.resolveDepartmentId(any()))
                .thenReturn(Result.ok(EntityIdDTO.of(3001L)));
    }

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
        mvc.perform(doctorAuth(post("/api/v1/medical-records").contentType("application/json").content(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void recordDetail_returnsFields() throws Exception {
        String recordNo = createRecord();
        mvc.perform(doctorAuth(get("/api/v1/medical-records/" + recordNo)))
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
        mvc.perform(doctorAuth(get("/api/v1/medical-records").param("patientId", PATIENT_NO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].chiefComplaint").value("胸闷、心悸 3 天"))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
    }

    @Test
    void updateDraft_onlyDraftAllowed() throws Exception {
        String recordNo = createRecord();
        // DRAFT 可改（医生身份）
        mvc.perform(doctorAuth(put("/api/v1/medical-records/" + recordNo))
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
        mvc.perform(doctorAuth(put("/api/v1/medical-records/" + recordNo))
                        .contentType("application/json")
                        .content("{\"doctorAdvice\":\"尝试修改已归档病历\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void archive_draftToArchived() throws Exception {
        String recordNo = createRecord();
        mvc.perform(doctorAuth(post("/api/v1/medical-records/" + recordNo + "/archive"))
                        .contentType("application/json")
                        .content("{\"confirmBy\":\"D10001\",\"confirmNote\":\"病历内容已确认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordNo))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void archive_notFound() throws Exception {
        mvc.perform(doctorAuth(post("/api/v1/medical-records/MR_NOT_EXIST/archive"))
                        .contentType("application/json")
                        .content("{\"confirmBy\":\"D10001\"}"))
                .andExpect(jsonPath("$.code").value(404001));
    }

    @Test
    void recordDetail_notFound() throws Exception {
        mvc.perform(doctorAuth(get("/api/v1/medical-records/MR_NOT_EXIST")))
                .andExpect(jsonPath("$.code").value(404001));
    }

    @Test
    void updateDraft_notFound() throws Exception {
        mvc.perform(doctorAuth(put("/api/v1/medical-records/MR_NOT_EXIST"))
                        .contentType("application/json")
                        .content("{\"doctorAdvice\":\"尝试改不存在病历\"}"))
                .andExpect(jsonPath("$.code").value(404001));
    }

    // ===== 越权防护（IDOR）=====

    @Test
    void recordDetail_otherPatientForbidden() throws Exception {
        String recordNo = createRecord(); // 归属 patientId=1001
        // 其他患者（patientId=9999）查他人病历 → FORBIDDEN（IDOR 防护）
        mvc.perform(otherPatientAuth(get("/api/v1/medical-records/" + recordNo)))
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void recordDetail_selfPatientAllowed() throws Exception {
        String recordNo = createRecord(); // 归属 patientId=1001
        // 患者本人（patientId=1001）查自己病历 → 放行
        mvc.perform(selfPatientAuth(get("/api/v1/medical-records/" + recordNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void recordList_patientScopedToSelf() throws Exception {
        createRecord(); // 归属 patientId=1001
        // 患者本人（patientId=1001）列表：忽略入参 patientId，只返回自己的（total=1）
        mvc.perform(selfPatientAuth(get("/api/v1/medical-records").param("patientId", "P_OTHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void updateDraft_otherPatientForbidden() throws Exception {
        String recordNo = createRecord(); // 归属 patientId=1001
        mvc.perform(otherPatientAuth(put("/api/v1/medical-records/" + recordNo))
                        .contentType("application/json")
                        .content("{\"doctorAdvice\":\"尝试改他人病历\"}"))
                .andExpect(jsonPath("$.code").value(403001));
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
    void createPrescription_nullQuantityRejected() throws Exception {
        // 验证 #1 修复：quantity/days null 不再漏过到 service（@DecimalMin/@Min 对 null 返回 valid）
        String recordNo = createRecord();
        String body = """
                {"recordId":"%s","patientId":"%s","doctorId":"%s",
                 "items":[{"drugName":"测试药","days":3}]}""".formatted(recordNo, PATIENT_NO, DOCTOR_NO);
        // quantity 缺失 → @NotNull 校验失败 → PARAM_ERROR
        mvc.perform(post("/api/v1/prescriptions").contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(400001));
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
    void submit_notFound() throws Exception {
        mvc.perform(post("/api/v1/prescriptions/RX_NOT_EXIST/submit"))
                .andExpect(jsonPath("$.code").value(404001));
    }

    @Test
    void prescriptionDetail_notFound() throws Exception {
        mvc.perform(get("/api/v1/prescriptions/RX_NOT_EXIST"))
                .andExpect(jsonPath("$.code").value(404001));
    }

    @Test
    void review_notFound() throws Exception {
        mvc.perform(post("/api/v1/prescriptions/RX_NOT_EXIST/review")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVE\",\"pharmacistId\":\"PH2001\"}"))
                .andExpect(jsonPath("$.code").value(404001));
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

    // ===== batch 2：缴费 / 调剂发药 / 完成 / 退方 =====

    @Test
    void pay_approvedToPaid() throws Exception {
        String rxNo = createApprovedPrescription();
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/pay")
                        .contentType("application/json")
                        .content("{\"paidAmount\":210.00,\"paymentNo\":\"PAY001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paidAmount").value(210.00));
    }

    @Test
    void pay_nonApprovedRejected() throws Exception {
        String rxNo = createPrescription(); // DRAFT，未审
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/pay")
                        .contentType("application/json")
                        .content("{\"paidAmount\":210.00,\"paymentNo\":\"PAY001\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void dispense_approvedToDispensed() throws Exception {
        String rxNo = createApprovedPrescription();
        // stub Feign outbound 成功
        when(drugFeignClient.outbound(any(), any())).thenReturn(
                Result.ok(new DispenseDTO.OutboundResponse("SF_MOCK_1", "D_MOCK", 50)));

        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/dispense")
                        .contentType("application/json")
                        .content("{\"pharmacistId\":\"PH2001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DISPENSED"))
                .andExpect(jsonPath("$.data.items[0].drugName").value("硝苯地平控释片"))
                .andExpect(jsonPath("$.data.items[0].dispensedQuantity").value(7))
                .andExpect(jsonPath("$.data.items[0].stockFlowId").value("SF_MOCK_1"));
    }

    @Test
    void dispense_insufficientStock_conflict() throws Exception {
        String rxNo = createApprovedPrescription();
        // stub Feign outbound 抛库存不足（CONFLICT）
        when(drugFeignClient.outbound(any(), any())).thenThrow(
                new BusinessException(ErrorCode.CONFLICT, "库存不足"));

        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/dispense")
                        .contentType("application/json")
                        .content("{\"pharmacistId\":\"PH2001\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void dispense_draftRejected() throws Exception {
        String rxNo = createPrescription(); // DRAFT，未审未缴费
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/dispense")
                        .contentType("application/json")
                        .content("{\"pharmacistId\":\"PH2001\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void dispense_fractionalQuantity_rejected() throws Exception {
        // 小数数量（1.5 片）应被拒绝：药品库存按整数件
        String recordNo = createRecord();
        String body = """
                {"recordId":"%s","patientId":"%s","doctorId":"%s",
                 "items":[{"drugNo":"D001","drugName":"测试药","days":3,"quantity":1.5,"unit":"片"}]}"""
                .formatted(recordNo, PATIENT_NO, DOCTOR_NO);
        String rxNo = om.readTree(mvc.perform(post("/api/v1/prescriptions")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .at("/data/prescriptionId").asText();
        submitPrescription(rxNo);
        approvePrescription(rxNo);
        when(drugFeignClient.outbound(any(), any())).thenReturn(
                Result.ok(new DispenseDTO.OutboundResponse("SF_MOCK", "D001", 50)));
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/dispense")
                        .contentType("application/json")
                        .content("{\"pharmacistId\":\"PH2001\"}"))
                .andExpect(jsonPath("$.code").value(400001));
    }

    @Test
    void complete_dispensedToCompleted() throws Exception {
        String rxNo = createDispensedPrescription();
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void complete_nonDispensedRejected() throws Exception {
        String rxNo = createApprovedPrescription(); // APPROVED，未调剂
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/complete"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void cancel_approvedToCancelled() throws Exception {
        String rxNo = createApprovedPrescription();
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/cancel")
                        .contentType("application/json")
                        .content("{\"cancelReason\":\"患者取消\",\"operatorId\":\"D10001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("患者取消"));
    }

    @Test
    void cancel_alreadyDispensedRejected() throws Exception {
        String rxNo = createDispensedPrescription(); // DISPENSED
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/cancel")
                        .contentType("application/json")
                        .content("{\"cancelReason\":\"尝试退已发药\",\"operatorId\":\"D10001\"}"))
                .andExpect(jsonPath("$.code").value(409001));
    }

    // ===== 测试助手 =====

    /** 创建病历（医生身份），返回 record_no */
    private String createRecord() throws Exception {
        String body = """
                {"patientId":"%s","doctorId":"%s","chiefComplaint":"胸闷、心悸 3 天",
                 "initialDiagnosis":["心律失常待查","高血压"]}""".formatted(PATIENT_NO, DOCTOR_NO);
        MvcResult r = mvc.perform(doctorAuth(post("/api/v1/medical-records").contentType("application/json").content(body)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/recordId").asText();
    }

    /** 归档病历（医生身份） */
    private void archiveRecord(String recordNo) throws Exception {
        mvc.perform(doctorAuth(post("/api/v1/medical-records/" + recordNo + "/archive"))
                        .contentType("application/json")
                        .content("{\"confirmBy\":\"D10001\",\"confirmNote\":\"确认\"}"))
                .andExpect(status().isOk());
    }

    /** 创建处方（DRAFT），返回 prescription_no。明细带 drugNo 供 dispense 测试用 */
    private String createPrescription() throws Exception {
        String recordNo = createRecord();
        String body = """
                {"recordId":"%s","patientId":"%s","doctorId":"%s","source":"OUTPATIENT",
                 "items":[{"drugNo":"D001","drugName":"硝苯地平控释片","specification":"30mg*7片","dosage":"30mg",
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

    /** 审方通过（PENDING_REVIEW → APPROVED） */
    private void approvePrescription(String rxNo) throws Exception {
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/review")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVE\",\"pharmacistId\":\"PH2001\",\"reviewComment\":\"用药合理\"}"))
                .andExpect(status().isOk());
    }

    /** 创建已审通过的处方（DRAFT→submit→review APPROVE），返回 prescription_no */
    private String createApprovedPrescription() throws Exception {
        String rxNo = createPrescription();
        submitPrescription(rxNo);
        approvePrescription(rxNo);
        return rxNo;
    }

    /** 创建已调剂的处方（开方→提交→审方→dispense），返回 prescription_no。需先 stub Feign */
    private String createDispensedPrescription() throws Exception {
        String rxNo = createApprovedPrescription();
        when(drugFeignClient.outbound(any(), any())).thenReturn(
                Result.ok(new DispenseDTO.OutboundResponse("SF_MOCK_1", "D001", 50)));
        mvc.perform(post("/api/v1/prescriptions/" + rxNo + "/dispense")
                        .contentType("application/json")
                        .content("{\"pharmacistId\":\"PH2001\"}"))
                .andExpect(status().isOk());
        return rxNo;
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
