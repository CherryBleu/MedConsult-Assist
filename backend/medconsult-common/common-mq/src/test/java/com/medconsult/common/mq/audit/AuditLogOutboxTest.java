package com.medconsult.common.mq.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.mq.LocalMessage;
import com.medconsult.common.mq.LocalMessageMapper;
import com.medconsult.common.mq.MqConstants;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogOutboxTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void producerEnqueuesAuditEventAsPendingLocalMessage() throws Exception {
        LocalMessageMapper mapper = mock(LocalMessageMapper.class);
        AuditLogProducer producer = new AuditLogProducer(mapper, objectMapper);

        AuditLogEvent event = new AuditLogEvent();
        event.setTraceId("trace-001");
        event.setResourceType("PATIENT");
        event.setResourceId("P30001");
        event.setResourceName("patient archive");
        event.setAction("CREATE");
        event.setOperatorId("7");
        event.setOperatorRole("HOSPITAL_ADMIN");
        event.setOperatorName("admin");
        event.setTargetOwnerId(3001L);
        event.setDetail("{\"source\":\"patient-service\"}");
        event.setResult("SUCCESS");

        producer.enqueue(event);

        ArgumentCaptor<LocalMessage> captor = ArgumentCaptor.forClass(LocalMessage.class);
        verify(mapper).insert(captor.capture());
        LocalMessage message = captor.getValue();
        assertEquals(MqConstants.EXCHANGE_LOG, message.getExchange());
        assertEquals(MqConstants.RK_AUDIT_LOG, message.getRoutingKey());
        assertEquals(LocalMessage.STATUS_PENDING, message.getStatus());
        assertTrue(message.getMessageNo().startsWith("audit:PATIENT:CREATE:P30001:"));
        assertEquals(0, message.getRetryCount());

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertEquals("trace-001", payload.get("traceId").asText());
        assertEquals("PATIENT", payload.get("resourceType").asText());
        assertEquals("P30001", payload.get("resourceId").asText());
        assertEquals("7", payload.get("operatorId").asText());
        assertEquals(3001L, payload.get("targetOwnerId").asLong());
    }

    @Test
    void aspectEnqueuesAuditEventAfterSuccessfulAnnotatedMethod() {
        AuditLogProducer producer = mock(AuditLogProducer.class);
        AuditLogAspect aspect = new AuditLogAspect(producer);
        PatientAuditTarget target = proxied(new PatientAuditTarget(), aspect);
        bindRequest();

        PatientResult result = target.update(3001L, "P30001");

        assertEquals("P30001", result.patientId());
        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(producer).enqueue(captor.capture());
        AuditLogEvent event = captor.getValue();
        assertEquals("trace-from-request", event.getTraceId());
        assertEquals("PATIENT", event.getResourceType());
        assertEquals("P30001", event.getResourceId());
        assertEquals("archive P30001", event.getResourceName());
        assertEquals("UPDATE", event.getAction());
        assertEquals("42", event.getOperatorId());
        assertEquals("HOSPITAL_ADMIN", event.getOperatorRole());
        assertEquals("Admin User", event.getOperatorName());
        assertEquals(3001L, event.getTargetOwnerId());
        assertEquals("{\"changed\":\"patient\"}", event.getDetail());
        assertEquals("127.0.0.1", event.getIp());
        assertEquals("JUnit", event.getUserAgent());
        assertEquals("SUCCESS", event.getResult());
    }

    @Test
    void aspectDoesNotEnqueueAuditEventWhenBusinessMethodFails() {
        AuditLogProducer producer = mock(AuditLogProducer.class);
        AuditLogAspect aspect = new AuditLogAspect(producer);
        PatientAuditTarget target = proxied(new PatientAuditTarget(), aspect);
        bindRequest();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> target.fail("P30001"));

        assertEquals("boom", ex.getMessage());
        verifyNoInteractions(producer);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxied(T target, AuditLogAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    private static void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        request.setAttribute(TraceIdFilter.REQUEST_ATTR_KEY, "trace-from-request");
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, new JwtPayload(
                JwtPayload.SubjectType.USER,
                42L,
                null,
                "Admin User",
                List.of("HOSPITAL_ADMIN"),
                "HOSPITAL_ADMIN",
                null,
                null,
                null,
                "U42",
                List.of("patient:update"),
                "jti",
                1893456000L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    static class PatientAuditTarget {
        @AuditLog(
                resourceType = "PATIENT",
                action = "UPDATE",
                resourceId = "#result.patientId()",
                resourceName = "'archive ' + #result.patientId()",
                targetOwnerId = "#p0",
                detail = "'{\"changed\":\"patient\"}'")
        PatientResult update(Long ownerId, String patientNo) {
            return new PatientResult(patientNo);
        }

        @AuditLog(resourceType = "PATIENT", action = "UPDATE", resourceId = "#p0")
        PatientResult fail(String patientNo) {
            throw new IllegalStateException("boom");
        }
    }

    record PatientResult(String patientId) {
    }
}
