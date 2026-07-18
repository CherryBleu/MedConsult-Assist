import request from '@/utils/request'
import {
  mockScheduleList, mockCreateAppointment, mockMyAppointmentList, mockCancelAppointment,
  mockAppointmentDetail, mockCheckInAppointment, mockPayAppointment, mockMarkNoShow,
  mockReceptionList, mockStartVisit, mockEndVisit, mockAdminAppointmentList,
  mockScheduleManageList, mockCreateSchedule, mockUpdateSchedule, mockDeleteSchedule, mockToggleScheduleStatus
} from '@/mock/appointment'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 后端 Appointment 字段 → 前端期望字段映射
// 后端 ListItem: appointmentId/patientNo/departmentName/doctorName/appointmentDate/period
//                /queueNo/fee/paymentStatus/appointmentStatus/visitReason
// 后端 DetailResponse 额外含: patientName/cancelReason
const mapAppointment = (a) => ({
  id: a.appointmentId ?? a.id,
  appointmentId: a.appointmentId ?? a.id,
  appointmentNo: a.appointmentNo ?? a.appointmentId ?? a.id,
  patientNo: a.patientNo,
  patientName: a.patientName,
  deptName: a.departmentName ?? a.deptName,
  departmentName: a.departmentName,
  doctorName: a.doctorName,
  scheduleDate: a.appointmentDate ?? a.scheduleDate,
  appointmentDate: a.appointmentDate,
  appointmentStatus: a.appointmentStatus ?? a.status,
  status: a.appointmentStatus ?? a.status,
  paymentStatus: a.paymentStatus,
  fee: a.fee ?? a.registrationFee,
  period: a.period,
  queueNo: a.queueNo,
  visitReason: a.visitReason,
  cancelReason: a.cancelReason,
  createdAt: a.createdAt
})

// 排班列表（后端排班在 /schedules/available，按 doctorId/departmentId/date 查询）
export const getScheduleListApi = async (doctorId, params) => {
  if (USE_MOCK) return Promise.resolve(mockScheduleList(doctorId))
  // doctorId 必传：按选中医生过滤排班，避免显示其他医生的号源导致挂错号
  const res = await request({ url: '/schedules/available', method: 'get', params: { doctorId, ...(params || {}) } })
  // 后端 /schedules/available 直接返回数组（非分页）
  const list = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  // 后端排班无 scheduleDate 字段（通用可用排班），适配为前端期望结构并展开到近 7 天：
  // 每个排班在展示的每一天都可用，避免"暂无排班"导致预约流程断链。
  const periodTime = { MORNING: { start: '08:00', end: '12:00' }, AFTERNOON: { start: '14:00', end: '17:00' }, EVENING: { start: '18:00', end: '21:00' } }
  const today = new Date()
  const expanded = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(today)
    d.setDate(d.getDate() + i)
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    for (const s of list) {
      const remaining = s.remainingQuota ?? 0
      expanded.push({
        id: s.scheduleId ?? s.id,
        scheduleId: s.scheduleId,
        scheduleDate: dateStr,
        doctorId: s.doctorId,
        doctorName: s.doctorName,
        period: s.period,
        status: remaining > 0 ? 'AVAILABLE' : 'FULL',
        totalQuota: remaining,
        bookedQuota: 0,
        remainingQuota: remaining,
        registrationFee: s.registrationFee ?? s.fee,
        fee: s.registrationFee ?? s.fee,
        startTime: periodTime[s.period]?.start ?? '08:00',
        endTime: periodTime[s.period]?.end ?? '12:00'
      })
    }
  }
  res.data = expanded
  return res
}

// 我的预约列表（对齐后端 GET /appointments，按 patientId/status 过滤）
export const getAppointmentListApi = async (params) => {
  if (USE_MOCK) return Promise.resolve(mockMyAppointmentList(params))
  const res = await request({ url: '/appointments', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = { ...(res.data || {}), records: list.map(mapAppointment), items: list.map(mapAppointment), total: res.data?.total ?? list.length }
  return res
}

// 预约详情（对齐后端 GET /appointments/{appointmentId}）
export const getAppointmentDetailApi = async (id) => {
  if (USE_MOCK) return Promise.resolve(mockAppointmentDetail(id))
  const res = await request({ url: `/appointments/${id}`, method: 'get' })
  if (res.data) res.data = mapAppointment(res.data)
  return res
}

// 创建预约（对齐后端 POST /appointments）
export const createAppointmentApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockCreateAppointment(data))
  return request({ url: '/appointments', method: 'post', data })
}

// 取消预约（对齐后端 POST /appointments/{appointmentId}/cancel）
// 后端 CancelRequest 字段：cancelReason + operatorType（PATIENT/DOCTOR/ADMIN）
export const cancelAppointmentApi = (id, reason, operatorType = 'PATIENT') => {
  if (USE_MOCK) return Promise.resolve(mockCancelAppointment(id))
  return request({
    url: `/appointments/${id}/cancel`,
    method: 'post',
    data: { cancelReason: reason, operatorType }
  })
}

// 支付（对齐后端 PATCH /appointments/{appointmentId}/payment）
// 后端 PaymentUpdateRequest.paymentStatus @NotBlank，必须带 body
export const payAppointmentApi = (id, payload = {}) => {
  if (USE_MOCK) return Promise.resolve(mockPayAppointment(id))
  return request({
    url: `/appointments/${id}/payment`,
    method: 'patch',
    data: {
      paymentStatus: 'PAID',
      paymentNo: payload.paymentNo,
      paidAmount: payload.paidAmount
    }
  })
}

// 签到（状态机 PATCH /appointments/{appointmentId}/status，appointmentStatus=CHECKED_IN）
export const checkInAppointmentApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockCheckInAppointment(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { appointmentStatus: 'CHECKED_IN' } })
}

// 标记爽约（状态机 PATCH，appointmentStatus=NO_SHOW）
export const markNoShowApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockMarkNoShow(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { appointmentStatus: 'NO_SHOW' } })
}

// 接诊列表（医生视角，对齐后端 GET /appointments，按 status=BOOKED/CHECKED_IN 过滤待接诊）
export const getReceptionListApi = async (params) => {
  if (USE_MOCK) return Promise.resolve(mockReceptionList(params))
  const res = await request({ url: '/appointments', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = { ...(res.data || {}), records: list.map(mapAppointment), items: list.map(mapAppointment), total: res.data?.total ?? list.length }
  return res
}

// 开始就诊（状态机 PATCH，appointmentStatus=IN_PROGRESS）
export const startVisitApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockStartVisit(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { appointmentStatus: 'IN_PROGRESS' } })
}

// 完成就诊（状态机 PATCH，appointmentStatus=COMPLETED）
export const endVisitApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockEndVisit(id))
  return request({ url: `/appointments/${id}/status`, method: 'patch', data: { appointmentStatus: 'COMPLETED' } })
}

// 管理员：预约列表（复用 GET /appointments，管理员权限在 JWT scope 校验）
export const getAdminAppointmentListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockAdminAppointmentList(params))
  return request({ url: '/appointments', method: 'get', params })
}

// 管理员：排班列表（对齐后端 GET /schedules）
// 后端 ListItem: scheduleId/doctorName/departmentName/scheduleDate/period/startTime/endTime
//                /totalQuota/bookedQuota/remainingQuota/registrationFee/status
// 前端 ScheduleManage 期望: scheduleNo/doctorName/deptName/scheduleDate/period/startTime/endTime
//                          /totalQuota/bookedQuota/registrationFee/status
const mapAdminSchedule = (s) => ({
  id: s.scheduleId ?? s.id,
  scheduleNo: s.scheduleId ?? s.scheduleNo,
  scheduleId: s.scheduleId,
  doctorId: s.doctorId,
  doctorName: s.doctorName,
  departmentId: s.departmentId,
  deptName: s.departmentName ?? s.deptName,
  departmentName: s.departmentName,
  scheduleDate: s.scheduleDate,
  period: s.period,
  startTime: s.startTime,
  endTime: s.endTime,
  totalQuota: s.totalQuota,
  bookedQuota: s.bookedQuota,
  remainingQuota: s.remainingQuota,
  registrationFee: s.registrationFee,
  fee: s.registrationFee,
  status: s.status
})

export const getScheduleManageListApi = async (params) => {
  if (USE_MOCK) return Promise.resolve(mockScheduleManageList(params))
  const res = await request({ url: '/schedules', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  const mapped = list.map(mapAdminSchedule)
  res.data = { ...(res.data || {}), records: mapped, items: mapped, total: res.data?.total ?? mapped.length }
  return res
}

// 管理员：创建排班（对齐后端 POST /schedules）
export const createScheduleApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockCreateSchedule(data))
  return request({ url: '/schedules', method: 'post', data })
}

// 管理员：更新排班（对齐 PUT /schedules/{id} 全量更新，仅 HOSPITAL_ADMIN，不可改医生）
export const updateScheduleApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockUpdateSchedule(data))
  return request({ url: `/schedules/${data.id}`, method: 'put', data })
}

// 管理员：删除排班（对齐 DELETE /schedules/{scheduleId}，软删，有未完成预约时拒绝）
export const deleteScheduleApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockDeleteSchedule(id))
  return request({ url: `/schedules/${id}`, method: 'delete' })
}

// 管理员：切换排班状态（对齐后端 PATCH /schedules/{scheduleId}/status）
export const toggleScheduleStatusApi = (id, status) => {
  if (USE_MOCK) return Promise.resolve(mockToggleScheduleStatus(id, status))
  return request({ url: `/schedules/${id}/status`, method: 'patch', data: { status } })
}

// 申请退款（对齐后端 POST /appointments/{id}/refund，仅 PAID 单可退，Redis 锁防重复退款）
// payload: { reason?, operatorType? }；返回 { refundNo, refundAmount, paymentStatus: 'REFUNDED' }
export const refundAppointmentApi = (id, data) => {
  if (USE_MOCK) return Promise.resolve({ code: 0, message: 'success', data: { refundNo: 'R_MOCK', appointmentId: id, refundAmount: 50, paymentStatus: 'REFUNDED' } })
  return request({ url: `/appointments/${id}/refund`, method: 'post', data })
}
