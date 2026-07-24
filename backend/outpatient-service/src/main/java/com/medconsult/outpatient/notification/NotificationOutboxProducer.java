package com.medconsult.outpatient.notification;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.mq.LocalMessage;
import com.medconsult.common.mq.LocalMessageMapper;
import com.medconsult.common.mq.MqConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Writes station notifications to local_message for reliable MQ dispatch.
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxProducer {

    private final LocalMessageMapper localMessageMapper;
    private final ObjectMapper objectMapper;

    public void enqueuePatient(Long patientId, String type, String title, String content,
                               String relatedType, String relatedId) {
        if (patientId == null) {
            return;
        }
        enqueue(String.valueOf(patientId), "PATIENT", type, title, content, relatedType, relatedId);
    }

    public void enqueueDoctor(Long doctorId, String type, String title, String content,
                              String relatedType, String relatedId) {
        if (doctorId == null) {
            return;
        }
        enqueue(String.valueOf(doctorId), "DOCTOR", type, title, content, relatedType, relatedId);
    }

    private void enqueue(String receiverId, String receiverRole, String type, String title, String content,
                         String relatedType, String relatedId) {
        NotificationPayload payload = new NotificationPayload(
                receiverId, receiverRole, type, title, content, relatedType, relatedId);
        LocalMessage message = LocalMessage.of(
                MqConstants.EXCHANGE_NOTIFICATION,
                messageNo(type, relatedType, relatedId),
                MqConstants.RK_NOTIFICATION_SEND,
                serialize(payload));
        localMessageMapper.insert(message);
    }

    private String serialize(NotificationPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialize notification event failed", e);
        }
    }

    private static String messageNo(String type, String relatedType, String relatedId) {
        return "notif:" + compact(type, 10)
                + ":" + compact(relatedType, 12)
                + ":" + compact(relatedId, 12)
                + ":" + IdWorker.getIdStr();
    }

    private static String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private record NotificationPayload(
            String receiverId,
            String receiverRole,
            String type,
            String title,
            String content,
            String relatedType,
            String relatedId
    ) {}
}
