package com.medconsult.notification.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.PageQuery;
import com.medconsult.notification.audit.dto.AuditLogDTO;
import com.medconsult.notification.audit.entity.AuditLog;
import com.medconsult.notification.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志服务实现（对齐《修改建议》§2.2 + 《接口文档》§4.1）。
 *
 * <p>write：补默认值（result 默认 SUCCESS、createdAt 自动）、生成 auditNo。
 * <p>list：支持 resourceType/resourceId/operatorId/action/dateFrom/dateTo 六条件过滤，
 *    按 created_at DESC 分页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional
    public AuditLogDTO.WriteResponse write(AuditLogDTO.WriteRequest req) {
        AuditLog a = new AuditLog();
        a.setAuditNo(generateAuditNo());
        a.setTraceId(req.getTraceId());
        a.setResourceType(req.getResourceType());
        a.setResourceId(req.getResourceId());
        a.setResourceName(req.getResourceName());
        a.setAction(req.getAction());
        a.setOperatorId(req.getOperatorId());
        a.setOperatorRole(req.getOperatorRole());
        a.setOperatorName(req.getOperatorName());
        a.setTargetOwnerId(req.getTargetOwnerId());
        a.setDetail(req.getDetail());
        a.setIp(req.getIp());
        a.setUserAgent(req.getUserAgent());
        a.setResult(req.getResult() == null || req.getResult().isBlank() ? "SUCCESS" : req.getResult());
        a.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(a);
        log.debug("审计写入: auditNo={} resourceType={} action={} operatorId={}",
                a.getAuditNo(), req.getResourceType(), req.getAction(), req.getOperatorId());
        return new AuditLogDTO.WriteResponse(a.getAuditNo(), a.getCreatedAt());
    }

    @Override
    public PageResult<AuditLogDTO.ListItem> list(int page, int pageSize,
                                                  String resourceType, String resourceId,
                                                  String operatorId, String action,
                                                  LocalDateTime dateFrom, LocalDateTime dateTo) {
        Page<AuditLog> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<AuditLog> qw = new QueryWrapper<>();
        if (resourceType != null && !resourceType.isBlank()) {
            qw.eq("resource_type", resourceType);
        }
        if (resourceId != null && !resourceId.isBlank()) {
            qw.eq("resource_id", resourceId);
        }
        if (operatorId != null && !operatorId.isBlank()) {
            qw.eq("operator_id", operatorId);
        }
        if (action != null && !action.isBlank()) {
            qw.eq("action", action);
        }
        if (dateFrom != null) {
            qw.ge("created_at", dateFrom);
        }
        if (dateTo != null) {
            qw.le("created_at", dateTo);
        }
        qw.orderByDesc("created_at");
        IPage<AuditLog> result = auditLogMapper.selectPage(p, qw);
        List<AuditLogDTO.ListItem> items = new ArrayList<>();
        for (AuditLog a : result.getRecords()) {
            items.add(new AuditLogDTO.ListItem(
                    a.getAuditNo(),
                    a.getResourceType(),
                    a.getResourceId(),
                    a.getResourceName(),
                    a.getAction(),
                    a.getOperatorId(),
                    a.getOperatorRole(),
                    a.getOperatorName(),
                    a.getResult(),
                    a.getCreatedAt()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    /** 生成审计编号：AL + 雪花序列 base36 */
    private static String generateAuditNo() {
        long id = IdWorker.getId();
        return "AL" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
