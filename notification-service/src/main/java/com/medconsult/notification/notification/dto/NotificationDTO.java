package com.medconsult.notification.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知相关请求/响应 DTO（对齐《接口文档》§2.8）。
 *
 * <p>{@code notificationId} 实为 {@code notification_no}（业务可读编号）。
 */
public class NotificationDTO {

    // ===== §2.8.1 创建通知 =====

    @Data
    public static class CreateRequest {
        @NotBlank(message = "接收人编号不能为空")
        private String receiverId;
        @NotBlank(message = "接收人角色不能为空")
        @Pattern(regexp = "^(PATIENT|DOCTOR|PHARMACY_ADMIN|HOSPITAL_ADMIN)$",
                message = "接收人角色须为 PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN")
        private String receiverRole;
        @NotBlank(message = "通知类型不能为空")
        @Pattern(regexp = "^(APPOINTMENT|SCHEDULE|MEDICATION|AI_RISK|SYSTEM)$",
                message = "通知类型须为 APPOINTMENT / SCHEDULE / MEDICATION / AI_RISK / SYSTEM")
        private String type;
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字")
        private String title;
        @Size(max = 1000, message = "内容不能超过 1000 字")
        private String content;
        private String relatedType;
        private String relatedId;
    }

    /** §2.8.1 创建响应 */
    public record CreateResponse(
            String notificationId,   // notification_no
            boolean read             // false（初始未读）
    ) {}

    // ===== §2.8.2 列表 =====

    /** §2.8.2 列表项 */
    public record ListItem(
            String notificationId,   // notification_no
            String type,
            String title,
            boolean read
    ) {}

    // ===== §2.8.3 标记已读 =====

    /** §2.8.3 标记已读响应 */
    public record ReadResponse(
            String notificationId,
            boolean read,
            LocalDateTime readAt
    ) {}
}
