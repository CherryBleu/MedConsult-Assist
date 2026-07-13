import request from '@/utils/request'

// 审计日志 API（对齐后端 notification-service AuditLogController，
// docs/接口文档.md §4.1 GET /audit-logs）
//
// 仅管理员（HOSPITAL_ADMIN/PHARMACY_ADMIN）可查（后端 @Permission(roles) 限制）。

// 分页查询审计日志（支持多条件过滤）
// params: { resourceType?, resourceId?, operatorId?, action?, dateFrom?, dateTo?, page, pageSize }
// 超时设为 15 秒（覆盖全局 90s 默认值）：审计日志为查询接口，不应让用户等 90 秒。
// notification-service 偶发僵死时，15 秒超时让前端快速失败并提示重试，而非一直 loading。
export const getAuditLogListApi = (params) => {
  return request({ url: '/audit-logs', method: 'get', params, timeout: 15000 })
}
