const TOKEN_KEY = 'hospital_token'
const REFRESH_TOKEN_KEY = 'hospital_refresh_token'

// 存储token
export const setToken = (token) => {
  localStorage.setItem(TOKEN_KEY, token)
}

// 获取token
export const getToken = () => {
  return localStorage.getItem(TOKEN_KEY) || ''
}

// 删除token
export const removeToken = () => {
  localStorage.removeItem(TOKEN_KEY)
}

// 存储refreshToken
export const setRefreshToken = (refreshToken) => {
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

// 获取refreshToken
export const getRefreshToken = () => {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

// 删除refreshToken
export const removeRefreshToken = () => {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}