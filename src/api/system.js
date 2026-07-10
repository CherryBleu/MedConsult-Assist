import request from '@/utils/request'
import { mockDoctorList, mockAddDoctor, mockUpdateDoctor, mockDeleteDoctor } from '@/mock/system'
import {
  mockUserList, mockAddUser, mockUpdateUser, mockDeleteUser,
  mockDeptList, mockAddDept, mockUpdateDept, mockDeleteDept
} from '@/mock/system'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 用户列表
export const getUserListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUserList())
  }
  return request({
    url: '/system/user/list',
    method: 'get',
    params
  })
}

// 新增用户
export const addUserApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddUser(data))
  }
  return request({
    url: '/system/user/add',
    method: 'post',
    data
  })
}

// 更新用户
export const updateUserApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateUser(id, data))
  }
  return request({
    url: `/system/user/update/${id}`,
    method: 'put',
    data
  })
}

// 删除用户
export const deleteUserApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteUser(id))
  }
  return request({
    url: `/system/user/delete/${id}`,
    method: 'delete'
  })
}

// 科室列表
export const getDeptListApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeptList())
  }
  return request({
    url: '/system/department/list',
    method: 'get'
  })
}

// 新增科室
export const addDeptApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDept(data))
  }
  return request({
    url: '/system/department/add',
    method: 'post',
    data
  })
}

// 更新科室
export const updateDeptApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateDept(id, data))
  }
  return request({
    url: `/system/department/update/${id}`,
    method: 'put',
    data
  })
}

// 删除科室
export const deleteDeptApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteDept(id))
  }
  return request({
    url: `/system/department/delete/${id}`,
    method: 'delete'
  })
}

// 医生列表
export const getDoctorListApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockDoctorList())
  }
  return request({
    url: '/system/doctor/list',
    method: 'get'
  })
}

// 新增医生
export const addDoctorApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDoctor(data))
  }
  return request({
    url: '/system/doctor/add',
    method: 'post',
    data
  })
}

// 更新医生
export const updateDoctorApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateDoctor(id, data))
  }
  return request({
    url: `/system/doctor/update/${id}`,
    method: 'put',
    data
  })
}

// 删除医生
export const deleteDoctorApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDeleteDoctor(id))
  }
  return request({
    url: `/system/doctor/delete/${id}`,
    method: 'delete'
  })
}