package com.medconsult.outpatient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 退款流程集成测试：覆盖 payment patch 防伪造、退款状态机与幂等。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        "spring.datasource.url=jdbc:h2:mem:medconsult_outpatient_refund_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
class RefundFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Autowired
    DataSource dataSource;

    private static final String DEPT_NO = "DEP_REFUND_TEST";
    private static final String DOCTOR_NO = "11001";
    private Long departmentId;
    private Long doctorId;

    @BeforeEach
    void seedBaseData() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        departmentId = 93001L;
        doctorId = 94001L;
        ignoreMissingTable(() -> jdbc.update("DELETE FROM refund_order"));
        jdbc.update("DELETE FROM appointment WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM doctor_schedule WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM doctor WHERE id = ?", doctorId);
        jdbc.update("DELETE FROM department WHERE id = ?", departmentId);
        jdbc.update("INSERT INTO department(id, department_no, name, description, location, enabled, deleted) " +
                "VALUES (?,?,?,?,?,1,0)", departmentId, DEPT_NO, "退款测试科", "退款测试科室", "测试楼 2 层");
        jdbc.update("INSERT INTO doctor(id, doctor_no, name, department_id, title, specialties, introduction, enabled, deleted) " +
                "VALUES (?,?,?,?,?,?,?,?,0)", doctorId, DOCTOR_NO, "退款测试医生", departmentId,
                "主治医师", "[\"退款流程\"]", "测试简介", 1);
    }

    @Test
    void paymentPatchRejectsRefundStatuses() throws Exception {
        String appointmentNo = createPaidAppointment("9101");

        mvc.perform(withPatient(patch("/api/v1/appointments/" + appointmentNo + "/payment"), "9101")
                        .contentType("application/json")
                        .content("{\"paymentStatus\":\"REFUNDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400001))
                .andExpect(jsonPath("$.message").value(containsString("退款状态")));

        mvc.perform(withPatient(patch("/api/v1/appointments/" + appointmentNo + "/payment"), "9101")
                        .contentType("application/json")
                        .content("{\"paymentStatus\":\"REFUNDED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400001))
                .andExpect(jsonPath("$.message").value(containsString("退款状态")));
    }

    @Test
    void paidAppointmentCanApplyRefundAndDuplicateIsIdempotent() throws Exception {
        String appointmentNo = createPaidAppointment("9102");
        String refundBody = "{\"reason\":\"行程冲突\",\"idempotencyKey\":\"idem-paid-9102\"}";

        MvcResult first = mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/refund"), "9102")
                        .contentType("application/json")
                        .content(refundBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundNo").exists())
                .andExpect(jsonPath("$.data.appointmentId").value(appointmentNo))
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.provider").value("MOCK"))
                .andExpect(jsonPath("$.data.refundAmount").value(50.0))
                .andReturn();
        String firstRefundNo = om.readTree(first.getResponse().getContentAsString()).at("/data/refundNo").asText();

        mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/refund"), "9102")
                        .contentType("application/json")
                        .content(refundBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundNo").value(firstRefundNo))
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

        Long count = jdbc().queryForObject("SELECT COUNT(*) FROM refund_order WHERE appointment_no = ?", Long.class, appointmentNo);
        org.junit.jupiter.api.Assertions.assertEquals(1L, count);

        Long notificationCount = jdbc().queryForObject("""
                SELECT COUNT(*) FROM local_message
                WHERE routing_key = 'notification.send'
                  AND payload_json LIKE '%退款成功%'
                """, Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(1L, notificationCount);
    }

    @Test
    void unpaidAppointmentRefundIsRejected() throws Exception {
        String appointmentNo = createAppointment(createSchedule(), "9103");

        mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/refund"), "9103")
                        .contentType("application/json")
                        .content("{\"reason\":\"未支付退款\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(containsString("仅已支付预约可申请退款")));
    }

    @Test
    void completedAppointmentRefundIsRejected() throws Exception {
        String appointmentNo = createPaidAppointment("9106");

        mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/check-in"), "9106")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(withDoctor(patch("/api/v1/appointments/" + appointmentNo + "/status"))
                        .contentType("application/json")
                        .content("{\"appointmentStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(withDoctor(patch("/api/v1/appointments/" + appointmentNo + "/status"))
                        .contentType("application/json")
                        .content("{\"appointmentStatus\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/refund"), "9106")
                        .contentType("application/json")
                        .content("{\"reason\":\"已完成退款\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(containsString("仅未就诊或已取消预约可申请退款")));
    }

    @Test
    void refundedDuplicateReturnsOriginalRefundOrder() throws Exception {
        String appointmentNo = createPaidAppointment("9104");

        MvcResult first = mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/refund"), "9104")
                        .contentType("application/json")
                        .content("{\"reason\":\"第一次退款\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String firstRefundNo = om.readTree(first.getResponse().getContentAsString()).at("/data/refundNo").asText();

        mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/refund"), "9104")
                        .contentType("application/json")
                        .content("{\"reason\":\"重复退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundNo").value(firstRefundNo))
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));
    }

    @Test
    void concurrentRefundRequestsCreateOnlyOneRefundOrder() throws Exception {
        String appointmentNo = createPaidAppointment("9105");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                MvcResult result = mvc.perform(withPatient(post("/api/v1/appointments/" + appointmentNo + "/refund"), "9105")
                                .contentType("application/json")
                                .content("{\"reason\":\"并发退款\",\"idempotencyKey\":\"idem-concurrent-" + index + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
                return om.readTree(result.getResponse().getContentAsString()).at("/data/refundNo").asText();
            }));
        }
        start.countDown();

        String refundNo = futures.get(0).get();
        for (Future<String> future : futures) {
            org.junit.jupiter.api.Assertions.assertEquals(refundNo, future.get());
        }
        pool.shutdownNow();

        Long count = jdbc().queryForObject("SELECT COUNT(*) FROM refund_order WHERE appointment_no = ?", Long.class, appointmentNo);
        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
    }

    private String createPaidAppointment(String patientId) throws Exception {
        String appointmentNo = createAppointment(createSchedule(), patientId);
        mvc.perform(withPatient(patch("/api/v1/appointments/" + appointmentNo + "/payment"), patientId)
                        .contentType("application/json")
                        .content("{\"paymentStatus\":\"PAID\",\"paymentNo\":\"PAY" + patientId + "\",\"paidAmount\":50.00}"))
                .andExpect(status().isOk());
        return appointmentNo;
    }

    private String createSchedule() throws Exception {
        String createBody = """
                {"doctorId":"%s","departmentId":"%s","scheduleDate":"2026-07-15",
                 "period":"MORNING","startTime":"08:00","endTime":"12:00",
                 "totalQuota":30,"registrationFee":50.00}""".formatted(DOCTOR_NO, DEPT_NO);
        MvcResult result = mvc.perform(withAdmin(post("/api/v1/schedules"))
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).at("/data/scheduleId").asText();
    }

    private String createAppointment(String scheduleNo, String patientId) throws Exception {
        String createBody = """
                {"patientId":"%s","scheduleId":"%s","visitReason":"退款测试","source":"MOBILE_APP"}""".formatted(patientId, scheduleNo);
        MvcResult result = mvc.perform(withPatient(post("/api/v1/appointments"), patientId)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        return body.at("/data/appointmentId").asText();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static void ignoreMissingTable(Runnable action) {
        try {
            action.run();
        } catch (DataAccessException ignored) {
        }
    }

    private MockHttpServletRequestBuilder withAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", "1")
                .header("X-User-Primary-Role", "HOSPITAL_ADMIN")
                .header("X-User-Roles", "HOSPITAL_ADMIN");
    }

    private MockHttpServletRequestBuilder withPatient(MockHttpServletRequestBuilder builder, String patientId) {
        return builder.header("X-User-Id", "6" + patientId)
                .header("X-User-Primary-Role", "PATIENT")
                .header("X-User-Roles", "PATIENT")
                .header("X-User-Patient-Id", patientId);
    }

    private MockHttpServletRequestBuilder withDoctor(MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", "5001")
                .header("X-User-Primary-Role", "DOCTOR")
                .header("X-User-Roles", "DOCTOR")
                .header("X-User-Doctor-Id", String.valueOf(doctorId));
    }
}
