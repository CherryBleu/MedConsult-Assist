import request from '@/utils/request'
import { getRefreshToken } from '@/utils/auth'
import { mockLogin, mockGetUserInfo, mockRegister, mockRefreshToken, mockLogout } from '@/mock/user'

// 是否启用模拟数据
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 注册接口
export const registerApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRegister(data))
  }
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

// 登录接口
export const loginApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockLogin(data.account))
  }
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

// 刷新token接口
export const refreshTokenApi = (refreshToken) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRefreshToken(refreshToken))
  }
  return request({
    url: '/auth/refresh',
    method: 'post',
    data: { refreshToken }
  })
}

// 登出接口（后端 @RequestBody LogoutRequest 需要 refreshToken 字段）
export const logoutApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockLogout())
  }
  return request({
    url: '/auth/logout',
    method: 'post',
    data: { refreshToken: getRefreshToken() }
  })
}

// 获取当前用户信息
export const getUserInfoApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockGetUserInfo())
  }
  return request({
    url: '/auth/me',
    method: 'get'
  })
}

// 绑定患者档案（补建档场景：历史脏账号 patient_id 为 NULL 时补全关联）
// 后端自动用 sys_user 已有的 name/phone 建档，前端只需补充 idCard/gender/birthDate。
// 返回新的 accessToken/refreshToken（已带 patientId），前端存储后无需重新登录。
export const bindPatientApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockGetUserInfo())
  }
  return request({
    url: '/auth/me/bind-patient',
    method: 'post',
    data
  })
}