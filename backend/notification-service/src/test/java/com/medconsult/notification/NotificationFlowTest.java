package com.medconsult.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.security.JwtCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * notification-service REST 层集成测试：H2 内存库（无 Nacos / 无 RabbitMQ）。
 *
 * <p>覆盖《接口文档》§2.8 通知 + §4.1 审计查询的关键路径：
 * <ul>
 *   <li>通知：创建 / 列表(receiverId+read 过滤) / 标记已读 / 重复标记已读幂等</li>
 *   <li>审计：内部写入 / 多条件查询(resourceType/operatorId/action/dateRange)</li>
 *   <li>内部接口 /internal/notifications + /internal/audit-logs 同步写入兜底</li>
 * </ul>
 *
 * <p><b>MQ 自动装配排除</b>：本测试只测 REST 层，排除 MedConsultMqAutoConfiguration
 * （避免因无 RabbitMQ 导致上下文加载失败）。消费者用 @MockBean 隔离。
 * MQ 消费者测试见 {@link MqConsumerTest}（需真实 RabbitMQ）。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        // H2 内存库 MySQL 模式
        "spring.datasource.url=jdbc:h2:mem:medconsult_notify_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // Redis（IdempotentConsumer 依赖）
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        // RabbitMQ 连接（AutoConfiguration 声明 Queue/Exchange Bean，需连上才能声明）；
        // 但阻止 listener 容器自动启动（REST 测试不需要消费者运行，避免队列声明时序问题）
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=medconsult",
        "spring.rabbitmq.password=medconsult123",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        // 禁用 Nacos
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@org.springframework.transaction.annotation.Transactional
class NotificationFlowTest {

    private static final String JWT_SECRET = "test-secret-0123456789abcdef0123456789abcdef-min32bytes";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private static final String RECEIVER = "P202607060001";

    // ===== 通知域 =====

    @Test
    void createNotification_success() throws Exception {
        String body = """
                {"receiverId":"%s","receiverRole":"PATIENT","type":"APPOINTMENT",
                 "title":"预约成功","content":"你已成功预约 2026-07-08 上午心内科王医生门诊。",
                 "relatedType":"APPOINTMENT","relatedId":"A202607060001"}""".formatted(RECEIVER);
        // POST 创建仅管理员（@Permission(roles=管理员)），带管理员身份头
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(body)
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.notificationId").exists())
                .andExpect(jsonPath("$.data.read").value(false));
    }

    @Test
    void createNotification_invalidType_rejected() throws Exception {
        String body = """
                {"receiverId":"%s","receiverRole":"PATIENT","type":"INVALID",
                 "title":"测试"}""".formatted(RECEIVER);
        // 带管理员身份头过角色校验，让 type 校验（400001）成为失败原因
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(body)
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.code").value(400001));
    }

    @Test
    void notificationList_filterByReceiverAndRead() throws Exception {
        String no = createNotification();
        // 未读过滤（以管理员身份查询，带 X-User-Id + X-User-Primary-Role 头通过 IDOR 鉴权）
        mvc.perform(get("/api/v1/notifications").param("receiverId", RECEIVER).param("read", "false")
                        .header("X-User-Id", "1").header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("预约成功"))
                .andExpect(jsonPath("$.data.items[0].read").value(false));
        // 已读过滤应为 0
        mvc.perform(get("/api/v1/notifications").param("receiverId", RECEIVER).param("read", "true")
                        .header("X-User-Id", "1").header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void markRead_success() throws Exception {
        String no = createNotification();
        mvc.perform(patch("/api/v1/notifications/" + no + "/read")
                        .header("X-User-Id", "1").header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").exists());
        // 标记后已读列表有 1 条
        mvc.perform(get("/api/v1/notifications").param("receiverId", RECEIVER).param("read", "true")
                        .header("X-User-Id", "1").header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void markRead_alreadyRead_idempotent() throws Exception {
        String no = createNotification();
        mvc.perform(patch("/api/v1/notifications/" + no + "/read")
                        .header("X-User-Id", "1").header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(status().isOk());
        // 重复标记不报错
        mvc.perform(patch("/api/v1/notifications/" + no + "/read")
                        .header("X-User-Id", "1").header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void markRead_notFound() throws Exception {
        mvc.perform(patch("/api/v1/notifications/N_NOT_EXIST/read")
                        .header("X-User-Id", "1").header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.code").value(404001));
    }

    @Test
    void createNotification_nonAdminForbidden() throws Exception {
        // POST /api/v1/notifications 仅管理员（@Permission(roles=管理员)）；
        // PATIENT 身份应被 PermissionAspect 拒绝 403。
        String body = """
                {"receiverId":"%s","receiverRole":"PATIENT","type":"APPOINTMENT","title":"测试"}""".formatted(RECEIVER);
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(body)
                        .header("X-User-Id", "100")
                        .header("X-User-Roles", "PATIENT")
                        .header("X-User-Primary-Role", "PATIENT"))
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void notificationList_nonAdminForbidden() throws Exception {
        // 非管理员查询通知列表：无 X-User-No 头（userNo 为空）无法与 receiver_id 匹配 → FORBIDDEN。
        // 正常前端会带 userNo（网关注入），此处模拟异常/直连场景拒绝。
        mvc.perform(get("/api/v1/notifications").param("receiverId", RECEIVER)
                        .header("X-User-Id", "100")
                        .header("X-User-Roles", "PATIENT")
                        .header("X-User-Primary-Role", "PATIENT"))
                .andExpect(jsonPath("$.code").value(403001));
    }

    @Test
    void internalCreateNotification_success() throws Exception {
        // 内部接口同步写入兜底
        String body = """
                {"receiverId":"D10001","receiverRole":"DOCTOR","type":"SYSTEM",
                 "title":"系统通知","content":"测试内部接口"}""";
        mvc.perform(withService(post("/internal/notifications"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.notificationId").exists());
    }

    // ===== 审计域 =====

    @Test
    void writeAuditLog_success() throws Exception {
        String body = """
                {"resourceType":"PATIENT","resourceId":"P202607060001","resourceName":"张三",
                 "action":"CREATE","operatorId":"U001","operatorRole":"HOSPITAL_ADMIN",
                 "operatorName":"管理员","ip":"127.0.0.1","result":"SUCCESS"}""";
        mvc.perform(withService(post("/internal/audit-logs"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.auditNo").exists())
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void auditLogList_multiConditionFilter() throws Exception {
        // 写两条不同 resourceType/action 的审计
        writeAudit("PATIENT", "CREATE", "U001");
        writeAudit("PRESCRIPTION", "UPDATE", "U002");

        // 审计查询仅管理员可访问（@Permission(roles={HOSPITAL_ADMIN,PHARMACY_ADMIN})），
        // 故所有 GET /api/v1/audit-logs 带管理员身份头（X-User-Roles 供 PermissionAspect 角色校验）。
        // resourceType 过滤
        mvc.perform(get("/api/v1/audit-logs").param("resourceType", "PATIENT")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].resourceType").value("PATIENT"));

        // operatorId 过滤
        mvc.perform(get("/api/v1/audit-logs").param("operatorId", "U002")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("UPDATE"));

        // action 过滤
        mvc.perform(get("/api/v1/audit-logs").param("action", "CREATE")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.total").value(1));

        // resourceType 过滤另一类业务审计
        mvc.perform(get("/api/v1/audit-logs").param("resourceType", "PRESCRIPTION")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("UPDATE"));

        // 查询审计日志本身也应落一条 VIEW 自审计，供管理员端筛选查看。
        mvc.perform(get("/api/v1/audit-logs").param("resourceType", "AUDIT_LOG")
                        .param("action", "VIEW")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items[0].resourceType").value("AUDIT_LOG"))
                .andExpect(jsonPath("$.data.items[0].action").value("VIEW"));
    }

    @Test
    void writeAuditLog_invalidAction_rejected() throws Exception {
        String body = """
                {"resourceType":"PATIENT","action":"INVALID","operatorId":"U001"}""";
        mvc.perform(withService(post("/internal/audit-logs"))
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(400001));
    }

    // ===== 测试助手 =====

    private String createNotification() throws Exception {
        String body = """
                {"receiverId":"%s","receiverRole":"PATIENT","type":"APPOINTMENT",
                 "title":"预约成功","content":"你已成功预约 2026-07-08 上午心内科王医生门诊。"}""".formatted(RECEIVER);
        // POST 创建仅管理员（@Permission(roles=管理员)），带管理员身份头
        MvcResult r = mvc.perform(post("/api/v1/notifications").contentType("application/json").content(body)
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "HOSPITAL_ADMIN")
                        .header("X-User-Primary-Role", "HOSPITAL_ADMIN"))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/notificationId").asText();
    }

    private void writeAudit(String resourceType, String action, String operatorId) throws Exception {
        String body = """
                {"resourceType":"%s","action":"%s","operatorId":"%s","result":"SUCCESS"}"""
                .formatted(resourceType, action, operatorId);
        mvc.perform(withService(post("/internal/audit-logs"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder withService(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + serviceToken("test-service"))
                .header("X-Caller-Service", "test-service");
    }

    private String serviceToken(String serviceCode) {
        return new JwtCodec(JWT_SECRET)
                .signService(serviceCode, serviceCode, List.of("*"), 3600L, "svc-jti-" + serviceCode);
    }
}
