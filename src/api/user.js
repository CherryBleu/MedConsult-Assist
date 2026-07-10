import request from '@/utils/request'
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
    url: '/auth/refresh-token',
    method: 'post',
    data: { refreshToken }
  })
}

// 登出接口
export const logoutApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockLogout())
  }
  return request({
    url: '/auth/logout',
    method: 'post'
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