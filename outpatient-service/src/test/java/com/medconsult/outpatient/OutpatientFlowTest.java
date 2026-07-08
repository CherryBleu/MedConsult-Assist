package com.medconsult.outpatient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 门诊服务全流程集成测试：H2 内存库 + Redis 16379（无 Nacos，排除 discovery/config）。
 *
 * <p>覆盖《接口文档》§2.3-§2.5 关键路径 + 抢号锁（架构文档 §7.1）：
 * <ul>
 *   <li>科室/医生列表查询（预置基础数据 via JdbcTemplate）</li>
 *   <li>创建排班 + 同医生重复排班 409</li>
 *   <li>创建预约（抢号）+ 验证 schedule.booked 增加 + queue_no 生成</li>
 *   <li>重复预约 409</li>
 *   <li>取消预约 + 验证号源释放 + 状态 CANCELLED</li>
 *   <li>状态流转 BOOKED→CHECKED_IN→COMPLETED</li>
 *   <li>已 COMPLETED 不可取消 → 业务异常</li>
 * </ul>
 *
 * <p>注意：DepartmentService/DoctorService 在本服务为只读（创建接口未定义），
 * 故测试用 JdbcTemplate 预置 department/doctor 基础数据，与生产 schema 一致。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        // 测试用 H2 内存库（MySQL dialect 模式），不连真实 MySQL 避免污染数据
        "spring.datasource.url=jdbc:h2:mem:medconsult_outpatient_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // Redis 仍连真实容器（infra/docker-compose.yml，测试需先启动），抢号锁依赖
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        // 禁用 Nacos discovery/config，避免测试连接 Nacos
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@org.springframework.transaction.annotation.Transactional
class OutpatientFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Autowired
    DataSource dataSource;

    /** 测试用科室/医生编号（预置数据）。doctor_no 用纯数字便于 appointment.patientId 解析为 Long */
    private static final String DEPT_NO = "DEP_TEST";
    private static final String DOCTOR_NO = "10001";
    private Long departmentId;
    private Long doctorId;

    @BeforeEach
    void seedBaseData() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 预置科室
        departmentId = 1001L;
        jdbc.update("INSERT INTO department(id, department_no, name, description, location, enabled, deleted) " +
                "VALUES (?,?,?,?,?,1,0)", departmentId, DEPT_NO, "测试科", "测试科室", "测试楼 1 层");
        // 预置医生（department_id 关联上面科室主键）
        doctorId = 2001L;
        jdbc.update("INSERT INTO doctor(id, doctor_no, name, department_id, title, specialties, introduction, enabled, deleted) " +
                "VALUES (?,?,?,?,?,?,?,?,0)", doctorId, DOCTOR_NO, "测试医生", departmentId,
                "主任医师", "[\"高血压\",\"心律失常\"]", "测试简介", 1);
    }

    @Test
    void departmentAndDoctorList_returnSeededData() throws Exception {
        // 科室列表
        mvc.perform(get("/api/v1/departments").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].departmentId").value(DEPT_NO))
                .andExpect(jsonPath("$.data.items[0].departmentName").value("测试科"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true));

        // 医生列表（按科室过滤）
        mvc.perform(get("/api/v1/doctors").param("departmentId", DEPT_NO).param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].doctorId").value(DOCTOR_NO))
                .andExpect(jsonPath("$.data.items[0].doctorName").value("测试医生"))
                .andExpect(jsonPath("$.data.items[0].departmentId").value(DEPT_NO))
                .andExpect(jsonPath("$.data.items[0].departmentName").value("测试科"))
                .andExpect(jsonPath("$.data.items[0].title").value("主任医师"))
                .andExpect(jsonPath("$.data.items[0].specialties[0]").value("高血压"))
                .andExpect(jsonPath("$.data.items[0].specialties[1]").value("心律失常"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true));
    }

    @Test
    void scheduleCreate_andDuplicateConflict() throws Exception {
        // 创建排班
        String createBody = """
                {"doctorId":"%s","departmentId":"%s","scheduleDate":"2026-07-15",
                 "period":"MORNING","startTime":"08:00","endTime":"12:00",
                 "totalQuota":30,"registrationFee":50.00}""".formatted(DOCTOR_NO, DEPT_NO);
        MvcResult createResult = mvc.perform(post("/api/v1/schedules")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.scheduleId").exists())
                .andExpect(jsonPath("$.data.remainingQuota").value(30))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        String scheduleNo = om.readTree(createResult.getResponse().getContentAsString())
                .at("/data/scheduleId").asText();

        // 同医生同日期同时段重复排班 → 409
        mvc.perform(post("/api/v1/schedules").contentType("application/json").content(createBody))
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已存在排班")));

        // 查询排班列表
        mvc.perform(get("/api/v1/schedules").param("departmentId", DEPT_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].scheduleId").value(scheduleNo))
                .andExpect(jsonPath("$.data.items[0].doctorName").value("测试医生"))
                .andExpect(jsonPath("$.data.items[0].departmentName").value("测试科"))
                .andExpect(jsonPath("$.data.items[0].totalQuota").value(30))
                .andExpect(jsonPath("$.data.items[0].bookedQuota").value(0))
                .andExpect(jsonPath("$.data.items[0].remainingQuota").value(30))
                .andExpect(jsonPath("$.data.items[0].status").value("AVAILABLE"));

        // 可预约号源
        mvc.perform(get("/api/v1/schedules/available").param("departmentId", DEPT_NO).param("date", "2026-07-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].scheduleId").value(scheduleNo))
                .andExpect(jsonPath("$.data[0].doctorId").value(DOCTOR_NO))
                .andExpect(jsonPath("$.data[0].doctorName").value("测试医生"))
                .andExpect(jsonPath("$.data[0].remainingQuota").value(30))
                .andExpect(jsonPath("$.data[0].registrationFee").value(50.0));
    }

    @Test
    void appointmentCreate_quotaDecrementAndQueueNo() throws Exception {
        String scheduleNo = createSchedule(5); // 总号源 5

        // 创建预约（patientId 用纯数字，service 层解析为 Long 主键）
        String createBody = """
                {"patientId":"9001","scheduleId":"%s","visitReason":"胸闷心悸","source":"MOBILE_APP"}""".formatted(scheduleNo);
        MvcResult createResult = mvc.perform(post("/api/v1/appointments")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appointmentId").exists())
                .andExpect(jsonPath("$.data.queueNo").value(1))
                .andExpect(jsonPath("$.data.fee").value(50.0))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.appointmentStatus").value("BOOKED"))
                .andReturn();
        String appointmentNo = om.readTree(createResult.getResponse().getContentAsString())
                .at("/data/appointmentId").asText();

        // 验证 schedule.booked 增加 + remaining 减少
        mvc.perform(get("/api/v1/schedules").param("departmentId", DEPT_NO))
                .andExpect(jsonPath("$.data.items[0].bookedQuota").value(1))
                .andExpect(jsonPath("$.data.items[0].remainingQuota").value(4));

        // 重复预约（同 patient + 同 schedule 未取消）→ 409
        mvc.perform(post("/api/v1/appointments").contentType("application/json").content(createBody))
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已有未取消预约")));

        // 查询预约详情
        mvc.perform(get("/api/v1/appointments/" + appointmentNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId").value(appointmentNo))
                .andExpect(jsonPath("$.data.doctorName").value("测试医生"))
                .andExpect(jsonPath("$.data.departmentName").value("测试科"))
                .andExpect(jsonPath("$.data.appointmentDate").value("2026-07-15"))
                .andExpect(jsonPath("$.data.period").value("MORNING"))
                .andExpect(jsonPath("$.data.queueNo").value(1))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.appointmentStatus").value("BOOKED"));

        // 分页查询预约
        mvc.perform(get("/api/v1/appointments").param("patientId", "9001").param("status", "BOOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].appointmentId").value(appointmentNo))
                .andExpect(jsonPath("$.data.items[0].appointmentStatus").value("BOOKED"));
    }

    @Test
    void appointmentCancel_releasesQuota() throws Exception {
        String scheduleNo = createSchedule(5);
        String appointmentNo = createAppointment(scheduleNo, "9002");

        // 验证 booked=1
        mvc.perform(get("/api/v1/schedules").param("departmentId", DEPT_NO))
                .andExpect(jsonPath("$.data.items[0].bookedQuota").value(1))
                .andExpect(jsonPath("$.data.items[0].remainingQuota").value(4));

        // 取消预约
        String cancelBody = """
                {"cancelReason":"时间冲突","operatorType":"PATIENT"}""";
        mvc.perform(post("/api/v1/appointments/" + appointmentNo + "/cancel")
                        .contentType("application/json").content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appointmentId").value(appointmentNo))
                .andExpect(jsonPath("$.data.appointmentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.releasedQuota").value(1));

        // 验证号源释放：booked=0, remaining=5
        mvc.perform(get("/api/v1/schedules").param("departmentId", DEPT_NO))
                .andExpect(jsonPath("$.data.items[0].bookedQuota").value(0))
                .andExpect(jsonPath("$.data.items[0].remainingQuota").value(5));
    }

    @Test
    void appointmentStatusFlow_bookedToCompleted() throws Exception {
        String scheduleNo = createSchedule(5);
        String appointmentNo = createAppointment(scheduleNo, "9003");

        // BOOKED → CHECKED_IN
        mvc.perform(patch("/api/v1/appointments/" + appointmentNo + "/status")
                        .contentType("application/json").content("{\"appointmentStatus\":\"CHECKED_IN\",\"remark\":\"签到\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentStatus").value("CHECKED_IN"));

        // CHECKED_IN → IN_PROGRESS
        mvc.perform(patch("/api/v1/appointments/" + appointmentNo + "/status")
                        .contentType("application/json").content("{\"appointmentStatus\":\"IN_PROGRESS\"}"))
                .andExpect(jsonPath("$.data.appointmentStatus").value("IN_PROGRESS"));

        // IN_PROGRESS → COMPLETED
        mvc.perform(patch("/api/v1/appointments/" + appointmentNo + "/status")
                        .contentType("application/json").content("{\"appointmentStatus\":\"COMPLETED\"}"))
                .andExpect(jsonPath("$.data.appointmentStatus").value("COMPLETED"));

        // 已 COMPLETED 不可取消 → 业务冲突 409
        mvc.perform(post("/api/v1/appointments/" + appointmentNo + "/cancel")
                        .contentType("application/json").content("{\"cancelReason\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不可取消")));

        // 非法状态流转 COMPLETED → CHECKED_IN → 409
        mvc.perform(patch("/api/v1/appointments/" + appointmentNo + "/status")
                        .contentType("application/json").content("{\"appointmentStatus\":\"CHECKED_IN\"}"))
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("非法状态流转")));
    }

    @Test
    void paymentUpdate_andScheduleSuspendNotified() throws Exception {
        String scheduleNo = createSchedule(5);
        String appointmentNo = createAppointment(scheduleNo, "9004");

        // 更新支付状态 UNPAID → PAID
        String payBody = """
                {"paymentStatus":"PAID","paymentNo":"PAY001","paidAmount":50.00}""";
        mvc.perform(patch("/api/v1/appointments/" + appointmentNo + "/payment")
                        .contentType("application/json").content(payBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId").value(appointmentNo))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        // 排班停诊 → notifiedAppointments=1（有 1 个未完成预约）
        mvc.perform(patch("/api/v1/schedules/" + scheduleNo + "/status")
                        .contentType("application/json").content("{\"status\":\"SUSPENDED\",\"reason\":\"医生停诊\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduleId").value(scheduleNo))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.notifiedAppointments").value(1));
    }

    // ===== 测试助手 =====

    /** 创建排班（总号源 quota），返回 schedule_no */
    private String createSchedule(int quota) throws Exception {
        String createBody = """
                {"doctorId":"%s","departmentId":"%s","scheduleDate":"2026-07-15",
                 "period":"MORNING","startTime":"08:00","endTime":"12:00",
                 "totalQuota":%d,"registrationFee":50.00}""".formatted(DOCTOR_NO, DEPT_NO, quota);
        MvcResult r = mvc.perform(post("/api/v1/schedules").contentType("application/json").content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/scheduleId").asText();
    }

    /** 创建预约，返回 appointment_no */
    private String createAppointment(String scheduleNo, String patientId) throws Exception {
        String createBody = """
                {"patientId":"%s","scheduleId":"%s","visitReason":"就诊","source":"MOBILE_APP"}""".formatted(patientId, scheduleNo);
        MvcResult r = mvc.perform(post("/api/v1/appointments").contentType("application/json").content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/appointmentId").asText();
    }
}
