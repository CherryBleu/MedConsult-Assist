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
export const getUserListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUserList())
  }
  // 后端暂无用户列表接口；返回空占位，避免阻塞页面
  return Promise.resolve({ code: 0, message: 'success', data: { items: [], total: 0 } })
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
export const getDeptListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeptList())
  }
  return request({
    url: '/departments',
    method: 'get',
    params
  })
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
export const getDoctorListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDoctorList())
  }
  return request({
    url: '/doctors',
    method: 'get',
    params
  })
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
