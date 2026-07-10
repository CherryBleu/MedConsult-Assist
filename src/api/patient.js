import request from '@/utils/request'
import { mockPatientInfo, mockHealthArchive, mockUpdatePatientInfo, mockPatientList, mockPatientDetail, mockAddPatient, mockUpdatePatientStatus } from '@/mock/patient'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 获取患者个人信息
export const getPatientInfoApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientInfo())
  }
  return request({
    url: '/patient/info',
    method: 'get'
  })
}

// 获取健康档案
export const getHealthArchiveApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockHealthArchive())
  }
  return request({
    url: '/patient/health-archive',
    method: 'get'
  })
}

// 更新患者信息
export const updatePatientInfoApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdatePatientInfo(data))
  }
  return request({
    url: '/patient/update',
    method: 'put',
    data
  })
}

export const getAdminPatientListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientList(params))
  }
  return request({
    url: '/admin/patient/list',
    method: 'get',
    params
  })
}

export const getPatientDetailApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientDetail(id))
  }
  return request({
    url: `/admin/patient/detail/${id}`,
    method: 'get'
  })
}

export const updatePatientStatusApi = (id, status) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdatePatientStatus(id, status))
  }
  return request({
    url: `/admin/patient/status/${id}`,
    method: 'put',
    data: { status }
  })
}

export const addPatientApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddPatient(data))
  }
  return request({
    url: '/admin/patient/add',
    method: 'post',
    data
  })
}