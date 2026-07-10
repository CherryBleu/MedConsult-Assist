package com.medconsult.notification.notification.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.Permission;
import com.medconsult.notification.notification.dto.NotificationDTO;
import com.medconsult.notification.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知对外接口（对齐《接口文档》§2.8）。
 *
 * <p>路径前缀 /api/v1/notifications（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code notificationId} 实为 {@code notification_no}（业务可读编号）。
 *
 * <p><b>创建通知权限</b>：POST 仅管理员可调（架构文档 §2.3——通知由业务事件经 MQ 或
 * /internal/notifications 触发，对外手动创建是管理动作，普通用户不能伪造发给他人的通知）。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "通知接口", description = "站内通知管理（§2.8）")
public class NotificationController {

    private final NotificationService notificationService;

    /** §2.8.1 创建通知（仅管理员；业务通知走 MQ 或 /internal/notifications） */
    @PostMapping
    @Operation(summary = "创建通知")
    @Permission(roles = {"HOSPITAL_ADMIN", "PHARMACY_ADMIN"})
    public Result<NotificationDTO.CreateResponse> create(@Valid @RequestBody NotificationDTO.CreateRequest req) {
        return Result.ok(notificationService.create(req));
    }

    /** §2.8.2 查询通知列表（可按 receiverId / read 过滤） */
    @GetMapping
    @Operation(summary = "查询通知列表")
    public Result<PageResult<NotificationDTO.ListItem>> list(
            @Parameter(description = "接收人编号") @RequestParam(required = false) String receiverId,
            @Parameter(description = "是否已读") @RequestParam(required = false) Boolean read,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(notificationService.list(page, pageSize, receiverId, read));
    }

    /** §2.8.3 标记通知已读 */
    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "标记通知已读")
    public Result<NotificationDTO.ReadResponse> markRead(@Parameter(description = "通知编号", required = true) @PathVariable String notificationId) {
        return Result.ok(notificationService.markRead(notificationId));
    }
}
