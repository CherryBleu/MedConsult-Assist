import request from '@/utils/request'
import { mockDoctorList, mockAddDoctor, mockUpdateDoctor, mockDeleteDoctor } from '@/mock/system'
import {
  mockUserList, mockAddUser, mockUpdateUser, mockDeleteUser,
  mockDeptList, mockAddDept, mockUpdateDept, mockDeleteDept
} from '@/mock/system'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// ===== 用户管理 =====
// 后端无独立 system/user 管理接口；用户创建走 /auth/register，列表/删除暂占位

// 用户列表（后端暂无用户管理列表接口，占位）
// 返回数组形式（与 el-table :data 兼容），非 PageResult 对象
export const getUserListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUserList())
  }
  // 后端暂无用户列表接口；返回空数组占位，避免阻塞页面
  return Promise.resolve({ code: 0, message: 'success', data: [] })
}

// 新增用户（对齐后端 POST /auth/register）
export const addUserApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddUser(data))
  }
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

// 更新用户（后端暂无用户更新接口，占位）
export const updateUserApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateUser(id, data))
  }
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

// 删除用户（后端暂无用户删除接口，占位）
export const deleteUserApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteUser(id))
  }
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

// ===== 科室管理 =====

// 科室列表（对齐后端 GET /departments）
// 后端返回 PageResult {items,total}；提取数组并映射字段（departmentId→id, departmentName→name）
export const getDeptListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeptList())
  }
  const res = await request({ url: '/departments', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(d => ({
    id: d.departmentId ?? d.id,
    departmentId: d.departmentId,
    name: d.departmentName ?? d.name,
    departmentName: d.departmentName,
    location: d.location,
    description: d.location ?? d.description,
    enabled: d.enabled
  }))
  return res
}

// 新增科室（后端暂无科室创建接口，占位）
export const addDeptApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDept(data))
  }
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

// 更新科室（后端暂无科室更新接口，占位）
export const updateDeptApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateDept(id, data))
  }
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

// 删除科室（后端暂无科室删除接口，占位）
export const deleteDeptApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteDept(id))
  }
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

// ===== 医生管理 =====

// 医生列表（对齐后端 GET /doctors）
// 后端返回 PageResult {items,total}；提取数组并映射字段
export const getDoctorListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDoctorList())
  }
  const res = await request({ url: '/doctors', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(d => ({
    id: d.doctorId ?? d.id,
    doctorId: d.doctorId,
    name: d.doctorName ?? d.name,
    doctorName: d.doctorName,
    departmentId: d.departmentId,
    departmentName: d.departmentName ?? d.deptName,
    title: d.title,
    specialties: Array.isArray(d.specialties) ? d.specialties.join('、') : (d.specialties ?? ''),
    registrationFee: d.registrationFee ?? d.fee,
    fee: d.registrationFee ?? d.fee,
    enabled: d.enabled
  }))
  return res
}

// 新增医生（后端暂无医生创建接口，占位）
export const addDoctorApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDoctor(data))
  }
  return Promise.resolve({ code: 0, 'message': 'success', data: null })
}

// 更新医生（后端暂无医生更新接口，占位）
export const updateDoctorApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateDoctor(id, data))
  }
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

// 删除医生（后端暂无医生删除接口，占位）
export const deleteDoctorApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteDoctor(id))
  }
  return Promise.resolve({ code: 0, message: 'success', data: null })
}
