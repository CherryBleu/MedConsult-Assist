package com.medconsult.notification.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "创建通知请求")
    public static class CreateRequest {
        @NotBlank(message = "接收人编号不能为空")
        @Size(max = 32, message = "接收人编号不能超过 32 字")
        @Schema(description = "接收人编号")
        private String receiverId;
        @NotBlank(message = "接收人角色不能为空")
        @Pattern(regexp = "^(PATIENT|DOCTOR|PHARMACY_ADMIN|HOSPITAL_ADMIN)$",
                message = "接收人角色须为 PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN")
        @Schema(description = "接收人角色：PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN")
        private String receiverRole;
        @NotBlank(message = "通知类型不能为空")
        @Pattern(regexp = "^(APPOINTMENT|SCHEDULE|MEDICATION|AI_RISK|SYSTEM)$",
                message = "通知类型须为 APPOINTMENT / SCHEDULE / MEDICATION / AI_RISK / SYSTEM")
        @Schema(description = "通知类型：APPOINTMENT / SCHEDULE / MEDICATION / AI_RISK / SYSTEM")
        private String type;
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字")
        @Schema(description = "标题")
        private String title;
        @Size(max = 1000, message = "内容不能超过 1000 字")
        @Schema(description = "内容")
        private String content;
        @Size(max = 50, message = "关联业务类型不能超过 50 字")
        @Schema(description = "关联业务类型")
        private String relatedType;
        @Size(max = 64, message = "关联业务编号不能超过 64 字")
        @Schema(description = "关联业务编号")
        private String relatedId;
    }

    /** §2.8.1 创建响应 */
    @Schema(description = "创建通知响应")
    public record CreateResponse(
            @Schema(description = "通知编号") String notificationId,   // notification_no
            @Schema(description = "是否已读") boolean read             // false（初始未读）
    ) {}

    // ===== §2.8.2 列表 =====

    /** §2.8.2 列表项 */
    @Schema(description = "通知列表项")
    public record ListItem(
            @Schema(description = "通知编号") String notificationId,   // notification_no
            @Schema(description = "通知类型") String type,
            @Schema(description = "标题") String title,
            @Schema(description = "通知正文") String content,
            @Schema(description = "是否已读") boolean read,
            @Schema(description = "创建时间") LocalDateTime createdAt
    ) {}

    // ===== §2.8.3 标记已读 =====

    /** §2.8.3 标记已读响应 */
    @Schema(description = "标记已读响应")
    public record ReadResponse(
            @Schema(description = "通知编号") String notificationId,
            @Schema(description = "是否已读") boolean read,
            @Schema(description = "阅读时间") LocalDateTime readAt
    ) {}
}
