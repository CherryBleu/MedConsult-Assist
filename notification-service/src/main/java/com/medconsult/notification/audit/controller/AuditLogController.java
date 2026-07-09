package com.medconsult.notification.audit.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.notification.audit.dto.AuditLogDTO;
import com.medconsult.notification.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 审计日志对外接口（对齐《接口文档》§4.1 + 《修改建议》§2.2 接口增强）。
 *
 * <p>路径前缀 /api/v1/audit-logs（对外，走 Gateway 鉴权）。
 * <p>查询参数按《修改建议》§2.2 建议补全：resourceType/resourceId/operatorId/action/dateFrom/dateTo。
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** §4.1 分页查询审计日志（支持多条件过滤） */
    @GetMapping
    public Result<PageResult<AuditLogDTO.ListItem>> list(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(auditLogService.list(page, pageSize, resourceType, resourceId,
                operatorId, action, dateFrom, dateTo));
    }
}
