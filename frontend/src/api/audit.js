import request from '@/utils/request'

// 审计日志 API（对齐后端 notification-service AuditLogController，
// docs/接口文档.md §4.1 GET /audit-logs）
//
// 仅管理员（HOSPITAL_ADMIN/PHARMACY_ADMIN）可查（后端 @Permission(roles) 限制）。

// 分页查询审计日志（支持多条件过滤）
// params: { resourceType?, resourceId?, operatorId?, action?, dateFrom?, dateTo?, page, pageSize }
export const getAuditLogListApi = (params) => {
  return request({ url: '/audit-logs', method: 'get', params })
}
