import request from '@/utils/request'

// 排班模板 API（对齐后端 /api/v1/schedule-templates，后端修改.md #16 默认排班）
// 后端 templateId 实为 template_no（业务可读编号）。仅 HOSPITAL_ADMIN 可访问。

// 模板列表字段映射（后端 → 前端）
const mapTemplate = (t) => ({
  id: t.templateId ?? t.id,
  templateNo: t.templateId ?? t.templateNo,
  templateId: t.templateId,
  doctorId: t.doctorId,
  doctorName: t.doctorName,
  departmentId: t.departmentId,
  deptName: t.departmentName ?? t.deptName,
  departmentName: t.departmentName,
  dayOfWeek: t.dayOfWeek,
  period: t.period,
  startTime: t.startTime,
  endTime: t.endTime,
  totalQuota: t.totalQuota,
  registrationFee: t.registrationFee,
  fee: t.registrationFee,
  enabled: t.enabled,
  ...t
})

// 查询模板列表（GET /schedule-templates，可按 doctorId/departmentId/enabled 过滤）
export const getTemplateListApi = async (params) => {
  const res = await request({ url: '/schedule-templates', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  const mapped = list.map(mapTemplate)
  res.data = { ...(res.data || {}), records: mapped, items: mapped, total: res.data?.total ?? mapped.length }
  return res
}

// 创建模板（POST /schedule-templates）
export const createTemplateApi = (data) => {
  return request({ url: '/schedule-templates', method: 'post', data })
}

// 更新模板（PUT /schedule-templates/{id}，不改归属医生）
export const updateTemplateApi = (id, data) => {
  return request({ url: `/schedule-templates/${id}`, method: 'put', data })
}

// 删除模板（DELETE /schedule-templates/{id}，软删）
export const deleteTemplateApi = (id) => {
  return request({ url: `/schedule-templates/${id}`, method: 'delete' })
}

// 一键生成排班（POST /schedule-templates/apply）
// data: { startDate: 'YYYY-MM-DD', weeks: 1-8, doctorId? }
// 返回 { generated, skipped }
export const applyScheduleTemplateApi = (data) => {
  return request({ url: '/schedule-templates/apply', method: 'post', data })
}
