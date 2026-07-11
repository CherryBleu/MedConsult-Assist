import request from '@/utils/request'
import {
  mockScheduleList, mockCreateAppointment, mockMyAppointmentList, mockCancelAppointment,
  mockAppointmentDetail, mockCheckInAppointment, mockPayAppointment, mockMarkNoShow,
  mockReceptionList, mockStartVisit, mockEndVisit, mockAdminAppointmentList,
  mockScheduleManageList, mockCreateSchedule, mockUpdateSchedule, mockDeleteSchedule, mockToggleScheduleStatus
} from '@/mock/appointment'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 排班列表（后端排班在 /schedules，按 departmentId/date 查询）
export const getScheduleListApi = (doctorId, params) => {
  if (USE_MOCK) return Promise.resolve(mockScheduleList(doctorId))
  return request({ url: '/schedules/available', method: 'get', params })
}

// 我的预约列表（对齐后端 GET /appointments，按 patientId/status 过滤）
export const getAppointmentListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockMyAppointmentList(params))
  return request({ url: '/appointments', method: 'get', params })
}

// 预约详情（对齐后端 GET /appointments/{appointmentId}）
export const getAppointmentDetailApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockAppointmentDetail(id))
  return request({ url: `/appointments/${id}`, method: 'get' })
}

// 创建预约（对齐后端 POST /appointments）
export const createAppointmentApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockCreateAppointment(data))
  return request({ url: '/appointments', method: 'post', data })
}

// 取消预约（对齐后端 POST /appointments/{appointmentId}/cancel）
export const cancelAppointmentApi = (id, reason) => {
  if (USE_MOCK) return Promise.resolve(mockCancelAppointment(id))
  return request({ url: `/appointments/${id}/cancel`, method: 'post', data: { reason } })
}

// 支付（对齐后端 PATCH /appointments/{appointmentId}/payment）
export const payAppointmentApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockPayAppointment(id))
  return request({ url: `/appointments/${id}/payment`, method: 'patch' })
}

// 签到（状态机 PATCH /appointments/{appointmentId}/status，status=CHECKED_IN）
export const checkInAppointmentApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockCheckInAppointment(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { status: 'CHECKED_IN' } })
}

// 标记爽约（状态机 PATCH，status=NO_SHOW）
export const markNoShowApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockMarkNoShow(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { status: 'NO_SHOW' } })
}

// 接诊列表（医生视角，对齐后端 GET /appointments，按 status=PAID 过滤待接诊）
export const getReceptionListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockReceptionList(params))
  return request({ url: '/appointments', method: 'get', params })
}

// 开始就诊（状态机 PATCH，status=IN_PROGRESS）
export const startVisitApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockStartVisit(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { status: 'IN_PROGRESS' } })
}

// 完成就诊（状态机 PATCH，status=COMPLETED）
export const endVisitApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockEndVisit(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { status: 'COMPLETED' } })
}

// 管理员：预约列表（复用 GET /appointments，管理员权限在 JWT scope 校验）
export const getAdminAppointmentListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockAdminAppointmentList(params))
  return request({ url: '/appointments', method: 'get', params })
}

// 管理员：排班列表（对齐后端 GET /schedules）
export const getScheduleManageListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockScheduleManageList(params))
  return request({ url: '/schedules', method: 'get', params })
}

// 管理员：创建排班（对齐后端 POST /schedules）
export const createScheduleApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockCreateSchedule(data))
  return request({ url: '/schedules', method: 'post', data })
}

// 管理员：更新排班（后端无 PUT /schedules/{id}，用 PATCH status 兜底）
export const updateScheduleApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockUpdateSchedule(data))
  return request({ url: `/schedules/${data.id}/status`, method: 'patch', data })
}

// 管理员：删除排班（后端无删除端点，占位）
export const deleteScheduleApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockDeleteSchedule(id))
  // 后端暂无排班删除接口；返回成功占位
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

// 管理员：切换排班状态（对齐后端 PATCH /schedules/{scheduleId}/status）
export const toggleScheduleStatusApi = (id, status) => {
  if (USE_MOCK) return Promise.resolve(mockToggleScheduleStatus(id, status))
  return request({ url: `/schedules/${id}/status`, method: 'patch', data: { status } })
}
