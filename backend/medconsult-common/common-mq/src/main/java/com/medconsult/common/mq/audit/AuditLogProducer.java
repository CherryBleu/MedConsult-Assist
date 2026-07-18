package com.medconsult.common.mq.audit;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.mq.LocalMessage;
import com.medconsult.common.mq.LocalMessageMapper;
import com.medconsult.common.mq.MqConstants;

/**
 * Writes audit events to local_message so MessageDispatcher can reliably publish
 * them after the local transaction commits.
 */
public class AuditLogProducer {

    private final LocalMessageMapper localMessageMapper;
    private final ObjectMapper objectMapper;

    public AuditLogProducer(LocalMessageMapper localMessageMapper, ObjectMapper objectMapper) {
        this.localMessageMapper = localMessageMapper;
        this.objectMapper = objectMapper;
    }

    public LocalMessage enqueue(AuditLogEvent event) {
        if (event.getResult() == null || event.getResult().isBlank()) {
            event.setResult("SUCCESS");
        }
        String payloadJson = serialize(event);
        LocalMessage message = LocalMessage.of(
                MqConstants.EXCHANGE_LOG,
                messageNo(event),
                MqConstants.RK_AUDIT_LOG,
                payloadJson);
        localMessageMapper.insert(message);
        return message;
    }

    private String serialize(AuditLogEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialize audit log event failed", e);
        }
    }

    private static String messageNo(AuditLogEvent event) {
        return "audit:" + compact(event.getResourceType(), 12)
                + ":" + compact(event.getAction(), 12)
                + ":" + compact(event.getResourceId(), 12)
                + ":" + IdWorker.getIdStr();
    }

    private static String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
