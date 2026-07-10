package com.medconsult.notification.audit.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.notification.audit.dto.AuditLogDTO;
import com.medconsult.notification.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "审计日志接口", description = "业务审计日志查询（§4.1）")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** §4.1 分页查询审计日志（支持多条件过滤） */
    @GetMapping
    @Operation(summary = "查询业务审计日志")
    public Result<PageResult<AuditLogDTO.ListItem>> list(
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType,
            @Parameter(description = "资源编号") @RequestParam(required = false) String resourceId,
            @Parameter(description = "操作人编号") @RequestParam(required = false) String operatorId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "起始时间") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(auditLogService.list(page, pageSize, resourceType, resourceId,
                operatorId, action, dateFrom, dateTo));
    }
}
