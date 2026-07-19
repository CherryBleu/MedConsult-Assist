package com.medconsult.notification.notification.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.notification.notification.dto.NotificationDTO;
import com.medconsult.notification.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知对内接口（架构文档 §2.3，/internal/notifications）。
 *
 * <p>路径前缀 /internal/notifications（不配 Gateway 路由，仅服务间 Feign 调用）。
 * <p>同步写入兜底：架构文档明确「同步少用，优先 MQ」。主路径是其他服务发 MQ 到
 * notification.send 队列，由 {@link com.medconsult.notification.consumer.NotificationConsumer} 消费。
 * 本端点供未来服务不想走 MQ 时同步调用。
 *
 * <p><b>鉴权</b>：强制服务身份（{@link SecurityContext#requireService()}），防伪造通知。
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationInternalController {

    private final NotificationService notificationService;

    /** 同步发单条通知（架构文档 §2.3，兜底用，优先 MQ） */
    @PostMapping
    public Result<NotificationDTO.CreateResponse> create(@Valid @RequestBody NotificationDTO.CreateRequest req) {
        SecurityContext.requireService("notification:write");
        return Result.ok(notificationService.create(req));
    }
}
