package com.medconsult.notification.audit.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.notification.audit.dto.AuditLogDTO;

import java.time.LocalDateTime;

/**
 * 审计日志服务接口（对齐《修改建议》§2.2 + 架构文档 §2.3 内部接口）。
 *
 * <p>write：内部写入（POST /internal/audit-logs 同步兜底 + MQ 消费者调）。
 * <p>list：对外查询（GET /audit-logs，支持多条件）。
 */
public interface AuditLogService {

    /** 写入审计日志（内部接口 + MQ 消费者共用） */
    AuditLogDTO.WriteResponse write(AuditLogDTO.WriteRequest req);

    /** 分页查询审计日志（支持 resourceType/resourceId/operatorId/action/dateFrom/dateTo） */
    PageResult<AuditLogDTO.ListItem> list(int page, int pageSize,
                                          String resourceType, String resourceId,
                                          String operatorId, String action,
                                          LocalDateTime dateFrom, LocalDateTime dateTo);
}
