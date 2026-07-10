import request from '@/utils/request'
import {
  mockScheduleList, mockCreateAppointment, mockMyAppointmentList, mockCancelAppointment,
  mockAppointmentDetail, mockCheckInAppointment, mockPayAppointment, mockMarkNoShow,
  mockReceptionList, mockStartVisit, mockEndVisit, mockAdminAppointmentList,
  mockScheduleManageList, mockCreateSchedule, mockUpdateSchedule, mockDeleteSchedule, mockToggleScheduleStatus
} from '@/mock/appointment'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export const getScheduleListApi = (doctorId) => {
  if (USE_MOCK) return Promise.resolve(mockScheduleList(doctorId))
  return request({ url: `/doctors/${doctorId}/schedules`, method: 'get' })
}

export const getAppointmentListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockMyAppointmentList(params))
  return request({ url: '/appointments/my', method: 'get', params })
}

export const getAppointmentDetailApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockAppointmentDetail(id))
  return request({ url: `/appointments/${id}`, method: 'get' })
}

export const createAppointmentApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockCreateAppointment(data))
  return request({ url: '/appointments', method: 'post', data })
}

export const cancelAppointmentApi = (id, reason) => {
  if (USE_MOCK) return Promise.resolve(mockCancelAppointment(id))
  return request({ url: `/appointments/${id}/cancel`, method: 'post', data: { reason } })
}

export const payAppointmentApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockPayAppointment(id))
  return request({ url: `/appointments/${id}/pay`, method: 'post' })
}

export const checkInAppointmentApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockCheckInAppointment(id))
  return request({ url: `/appointments/${id}/checkin`, method: 'post' })
}

export const markNoShowApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockMarkNoShow(id))
  return request({ url: `/appointments/${id}/no-show`, method: 'post' })
}

export const getReceptionListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockReceptionList(params))
  return request({ url: '/appointments/reception', method: 'get', params })
}

export const startVisitApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockStartVisit(id))
  return request({ url: `/appointments/${id}/start`, method: 'post' })
}

export const endVisitApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockEndVisit(id))
  return request({ url: `/appointments/${id}/complete`, method: 'post' })
}

export const getAdminAppointmentListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockAdminAppointmentList(params))
  return request({ url: '/admin/appointments', method: 'get', params })
}

export const getScheduleManageListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockScheduleManageList(params))
  return request({ url: '/admin/schedules', method: 'get', params })
}

export const createScheduleApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockCreateSchedule(data))
  return request({ url: '/admin/schedules', method: 'post', data })
}

export const updateScheduleApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockUpdateSchedule(data))
  return request({ url: `/admin/schedules/${data.id}`, method: 'put', data })
}

export const deleteScheduleApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockDeleteSchedule(id))
  return request({ url: `/admin/schedules/${id}`, method: 'delete' })
}

export const toggleScheduleStatusApi = (id, status) => {
  if (USE_MOCK) return Promise.resolve(mockToggleScheduleStatus(id, status))
  return request({ url: `/admin/schedules/${id}/status`, method: 'put', data: { status } })
}
