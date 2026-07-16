import request from '@/utils/request'
import { mockDoctorList, mockAddDoctor, mockUpdateDoctor, mockDeleteDoctor } from '@/mock/system'
import {
  mockUserList, mockAddUser, mockUpdateUser, mockDeleteUser,
  mockDeptList, mockAddDept, mockUpdateDept, mockDeleteDept
} from '@/mock/system'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// ===== 用户管理 =====
// 用户列表走后端 GET /auth/users（仅 HOSPITAL_ADMIN 可访问），用户创建走 /auth/register，
// 更新/删除暂占位。

// 用户列表（对齐后端 GET /auth/users，仅 HOSPITAL_ADMIN 可访问）
// 后端返回 PageResult {page,pageSize,total,items}；UserManage.vue 侧从 items 取数组
export const getUserListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUserList())
  }
  return request({ url: '/auth/users', method: 'get', params })
}

// 新增用户（对齐后端 POST /auth/users，管理员创建用户接口，支持管理类角色 #20/#21）
export const addUserApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddUser(data))
  }
  return request({
    url: '/auth/users',
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
// 后端返回 PageResult {items,total}；提取数组并映射字段（departmentId→id/departmentNo, departmentName→name）
export const getDeptListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeptList())
  }
  const res = await request({ url: '/departments', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(d => ({
    id: d.departmentId ?? d.id,
    departmentId: d.departmentId,
    departmentNo: d.departmentId ?? d.departmentNo,
    name: d.departmentName ?? d.name,
    departmentName: d.departmentName,
    location: d.location,
    description: d.location ?? d.description,
    enabled: d.enabled
  }))
  return res
}

// 新增科室（对齐后端 POST /departments，#15。departmentNo 后端自动生成）
export const addDeptApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDept(data))
  }
  return request({ url: '/departments', method: 'post', data })
}

// 更新科室（对齐后端 PATCH /departments/{departmentId}，#15。部分字段）
export const updateDeptApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateDept(id, data))
  }
  return request({ url: `/departments/${id}`, method: 'patch', data })
}

// 删除科室（对齐后端 DELETE /departments/{departmentId}，#15。被引用时后端拒绝）
export const deleteDeptApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteDept(id))
  }
  return request({ url: `/departments/${id}`, method: 'delete' })
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
    // 后端 ListItem.doctorId 即 doctor_no（业务编号），前端表格"工号"列读 doctorNo
    doctorNo: d.doctorId ?? d.doctorNo,
    name: d.doctorName ?? d.name,
    doctorName: d.doctorName,
    departmentId: d.departmentId,
    departmentName: d.departmentName ?? d.deptName,
    title: d.title,
    specialties: Array.isArray(d.specialties) ? d.specialties.join('、') : (d.specialties ?? ''),
    // 后端 Doctor 实体无 gender/phone/registrationFee 字段，暂回退为空
    gender: d.gender ?? '',
    phone: d.phone ?? '',
    registrationFee: d.registrationFee ?? d.fee ?? '',
    fee: d.registrationFee ?? d.fee,
    enabled: d.enabled,
    status: d.enabled ? 'ACTIVE' : 'DISABLED'
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

// ===== 修改密码 =====

// 修改密码（对齐后端 POST /auth/change-password）
export const changePasswordApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockChangePassword(data))
  }
  return request({ url: '/auth/change-password', method: 'post', data })
}
