package com.medconsult.notification.audit.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.notification.audit.dto.AuditLogDTO;
import com.medconsult.notification.audit.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志对内接口（架构文档 §2.3，/internal/audit-logs）。
 *
 * <p>路径前缀 /internal/audit-logs（不配 Gateway 路由，仅服务间 Feign 调用）。
 * <p>同步写入兜底：架构文档明确「同步少用，优先 MQ」。主路径是其他服务发 MQ 到
 * audit.log 队列，由 {@link com.medconsult.notification.consumer.AuditLogConsumer} 消费。
 *
 * <p><b>鉴权</b>：强制服务身份（{@link SecurityContext#requireService()}），防伪造审计。
 */
@RestController
@RequestMapping("/internal/audit-logs")
@RequiredArgsConstructor
public class AuditLogInternalController {

    private final AuditLogService auditLogService;

    /** 同步写审计（架构文档 §2.3，兜底用，优先 MQ） */
    @PostMapping
    public Result<AuditLogDTO.WriteResponse> write(@Valid @RequestBody AuditLogDTO.WriteRequest req) {
        SecurityContext.requireService();
        return Result.ok(auditLogService.write(req));
    }
}
