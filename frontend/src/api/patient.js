import request from '@/utils/request'
import { mockPatientInfo, mockHealthArchive, mockUpdatePatientInfo, mockPatientList, mockPatientDetail, mockAddPatient, mockUpdatePatientStatus } from '@/mock/patient'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 获取患者个人信息（当前登录患者的档案，对齐后端 GET /patients/{patientId}）
export const getPatientInfoApi = (patientId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientInfo())
  }
  return request({
    url: `/patients/${patientId}`,
    method: 'get'
  })
}

// 获取健康档案（后端无独立健康档案端点，复用患者详情）
export const getHealthArchiveApi = (patientId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockHealthArchive())
  }
  return request({
    url: `/patients/${patientId}`,
    method: 'get'
  })
}

// 更新患者信息（对齐后端 PUT /patients/{patientId}）
export const updatePatientInfoApi = (patientId, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdatePatientInfo(data))
  }
  return request({
    url: `/patients/${patientId}`,
    method: 'put',
    data
  })
}

// 管理员：患者列表（对齐后端 GET /patients）
export const getAdminPatientListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientList(params))
  }
  return request({
    url: '/patients',
    method: 'get',
    params
  })
}

// 管理员：患者详情（对齐后端 GET /patients/{patientId}）
export const getPatientDetailApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientDetail(id))
  }
  return request({
    url: `/patients/${id}`,
    method: 'get'
  })
}

// 管理员：更新患者状态（对齐后端 PATCH /patients/{patientId}/status）
export const updatePatientStatusApi = (id, status) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdatePatientStatus(id, status))
  }
  return request({
    url: `/patients/${id}/status`,
    method: 'patch',
    data: { status }
  })
}

// 管理员：新增患者（对齐后端 POST /patients）
export const addPatientApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddPatient(data))
  }
  return request({
    url: '/patients',
    method: 'post',
    data
  })
}
