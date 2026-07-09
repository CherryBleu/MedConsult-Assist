package com.medconsult.notification.consumer;

import lombok.Data;

/**
 * 通知事件消息体（MQ payload 反序列化目标）。
 *
 * <p>生产端（未来 medical-record/outpatient 等服务）把通知事件序列化为 JSON 发到
 * notification.send 队列；本类是反序列化目标，字段与 {@link com.medconsult.notification.notification.dto.NotificationDTO.CreateRequest} 对齐。
 *
 * <p>消息体格式约定：LocalMessage.payloadJson 的内容即本类的 JSON 序列化。
 */
@Data
public class NotificationEvent {
    private String receiverId;
    private String receiverRole;
    private String type;
    private String title;
    private String content;
    private String relatedType;
    private String relatedId;
}
