package com.medconsult.notification.notification.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.notification.notification.dto.NotificationDTO;
import com.medconsult.notification.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知对外接口（对齐《接口文档》§2.8）。
 *
 * <p>路径前缀 /api/v1/notifications（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code notificationId} 实为 {@code notification_no}（业务可读编号）。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** §2.8.1 创建通知 */
    @PostMapping
    public Result<NotificationDTO.CreateResponse> create(@Valid @RequestBody NotificationDTO.CreateRequest req) {
        return Result.ok(notificationService.create(req));
    }

    /** §2.8.2 查询通知列表（可按 receiverId / read 过滤） */
    @GetMapping
    public Result<PageResult<NotificationDTO.ListItem>> list(
            @RequestParam(required = false) String receiverId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(notificationService.list(page, pageSize, receiverId, read));
    }

    /** §2.8.3 标记通知已读 */
    @PatchMapping("/{notificationId}/read")
    public Result<NotificationDTO.ReadResponse> markRead(@PathVariable String notificationId) {
        return Result.ok(notificationService.markRead(notificationId));
    }
}
