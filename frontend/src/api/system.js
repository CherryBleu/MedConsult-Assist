import request from '@/utils/request'
import { mockDoctorList, mockAddDoctor, mockUpdateDoctor, mockDeleteDoctor } from '@/mock/system'
import { maskPhone } from '@/utils/privacy'
import {
  mockUserList, mockAddUser, mockUpdateUser, mockDeleteUser,
  mockDeptList, mockAddDept, mockUpdateDept, mockDeleteDept,
  mockChangePassword
} from '@/mock/system'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const normalizePageList = (payload) => payload?.items ?? payload?.records ?? (Array.isArray(payload) ? payload : [])

const mapDoctorUserPhones = async () => {
  try {
    const res = await getUserListApi({ role: 'DOCTOR', pageSize: 100 })
    const users = normalizePageList(res.data)
    const phoneMap = new Map()
    users.forEach(user => {
      const masked = maskPhone(user.phone)
      if (!masked) return
      ;[user.doctorId, user.userNo, user.account].forEach(key => {
        if (key != null && key !== '') phoneMap.set(String(key), masked)
      })
    })
    return phoneMap
  } catch (e) {
    return new Map()
  }
}

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

// 更新用户（后端暂无用户更新接口；明确拒绝，避免「改了不生效」误导。后端接口待补）
export const updateUserApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateUser(id, data))
  }
  return Promise.reject(new Error('用户更新功能暂未提供（后端接口待补）'))
}

// 删除用户（后端暂无用户删除接口；明确拒绝，避免「删了又回来」误导。后端接口待补）
export const deleteUserApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteUser(id))
  }
  return Promise.reject(new Error('用户删除功能暂未提供（后端接口待补）'))
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
  const list = normalizePageList(res.data)
  const phoneMap = await mapDoctorUserPhones()
  res.data = list.map(d => ({
    id: d.doctorId ?? d.id,
    doctorPkId: d.id,
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
    phone: maskPhone(d.phone) || phoneMap.get(String(d.id)) || phoneMap.get(String(d.doctorId)) || '',
    registrationFee: d.registrationFee ?? d.fee ?? '',
    fee: d.registrationFee ?? d.fee,
    enabled: d.enabled,
    status: d.enabled ? 'ACTIVE' : 'DISABLED'
  }))
  return res
}

// 新增医生（对齐后端 POST /doctors，管理员创建医生档案接口）
// 注意：后端 CreateRequest 字段为 { name, departmentId, title, specialties, introduction, enabled }，
// 前端 form 还含 doctorNo/phone/password 等账号字段——这些字段后端 doctor 表不消费，
// 由管理员通过 UserManage（POST /auth/users）单独建账号并关联 doctorId。
export const addDoctorApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDoctor(data))
  }
  return request({
    url: '/doctors',
    method: 'post',
    data: {
      name: data.name,
      departmentId: data.departmentId,
      title: data.title,
      specialties: data.specialties,
      introduction: data.introduction,
      enabled: data.status ? data.status !== 'DISABLED' : data.enabled
    }
  })
}

// 更新医生（对齐后端 PATCH /doctors/{doctorId}）
export const updateDoctorApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateDoctor(id, data))
  }
  return request({
    url: `/doctors/${id}`,
    method: 'patch',
    data: {
      departmentId: data.departmentId,
      title: data.title,
      specialties: data.specialties,
      introduction: data.introduction,
      enabled: data.status ? data.status !== 'DISABLED' : data.enabled
    }
  })
}

// 删除医生（对齐后端 DELETE /doctors/{doctorId}，逻辑删除）
export const deleteDoctorApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteDoctor(id))
  }
  return request({
    url: `/doctors/${id}`,
    method: 'delete'
  })
}

// ===== 修改密码 =====

// 修改密码（对齐后端 POST /auth/change-password）
export const changePasswordApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockChangePassword(data))
  }
  return request({ url: '/auth/change-password', method: 'post', data })
}
