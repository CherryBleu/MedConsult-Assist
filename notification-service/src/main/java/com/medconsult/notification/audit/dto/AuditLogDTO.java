package com.medconsult.notification.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志相关 DTO（对齐《接口文档》§4.1 GET /audit-logs + 《修改建议》§2.2）。
 */
public class AuditLogDTO {

    // ===== 内部写入 POST /internal/audit-logs =====

    @Data
    public static class WriteRequest {
        private String traceId;
        @NotBlank(message = "资源类型不能为空")
        @Size(max = 50, message = "资源类型不能超过 50 字")
        private String resourceType;
        @Size(max = 64, message = "资源编号不能超过 64 字")
        private String resourceId;
        @Size(max = 200, message = "资源名称不能超过 200 字")
        private String resourceName;
        @NotBlank(message = "操作类型不能为空")
        @Pattern(regexp = "^(VIEW|CREATE|UPDATE|DELETE|EXPORT|LOGIN|LOGOUT)$",
                message = "操作类型须为 VIEW / CREATE / UPDATE / DELETE / EXPORT / LOGIN / LOGOUT")
        private String action;
        @Size(max = 64, message = "操作人 ID 不能超过 64 字")
        private String operatorId;
        @Size(max = 32, message = "操作人角色不能超过 32 字")
        private String operatorRole;
        @Size(max = 50, message = "操作人姓名不能超过 50 字")
        private String operatorName;
        private Long targetOwnerId;
        /** 变更前后快照（JSON 串，可空） */
        private String detail;
        @Size(max = 50, message = "IP 不能超过 50 字")
        private String ip;
        @Size(max = 255, message = "User-Agent 不能超过 255 字")
        private String userAgent;
        @Pattern(regexp = "^$|^(SUCCESS|FAILED)$", message = "结果须为 SUCCESS / FAILED")
        private String result;
    }

    /** 写入响应 */
    public record WriteResponse(
            String auditNo,
            LocalDateTime createdAt
    ) {}

    // ===== 对外查询 GET /audit-logs =====

    /** 查询列表项（对外返回，含关键字段，比《接口文档》示例更完整） */
    public record ListItem(
            String auditNo,
            String resourceType,
            String resourceId,
            String resourceName,
            String action,
            String operatorId,
            String operatorRole,
            String operatorName,
            String result,
            LocalDateTime createdAt
    ) {}
}
