import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, setToken, removeToken, getRefreshToken, removeRefreshToken } from './auth'
import router from '@/router'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL + '/v1',
  timeout: 15000
})

let isRefreshing = false
let pendingRequests = []

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 0) {
      if (res.code === 401) {
        return handleTokenRefresh(response.config)
      }
      // 未登录状态下的业务错误不弹全局提示（调用方自行处理）
      if (getToken()) {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    // 分页契约适配：后端 PageResult 返回 {items,total}，前端多处按 {records,total} 取值。
    // 在此统一补 records 别名，避免逐个组件改写且兼容 mock 旧返回。
    if (res.data && Array.isArray(res.data.items) && !res.data.records) {
      res.data.records = res.data.items
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      return handleTokenRefresh(error.config)
    }
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    // 未登录状态下的网络错误不弹全局提示（调用方自行处理，如注册页科室加载失败静默）
    if (getToken()) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

const doRefreshToken = async () => {
  const refreshToken = getRefreshToken()
  const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'
  if (USE_MOCK) {
    return {
      accessToken: 'mock-access-token-' + Date.now(),
      refreshToken: refreshToken,
      expiresIn: 7200
    }
  }
  const res = await axios.post(
    import.meta.env.VITE_API_BASE_URL + '/v1/auth/refresh',
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } }
  )
  if (res.data?.code === 0) return res.data.data
  throw new Error('refresh failed')
}

const handleTokenRefresh = async (config) => {
  // 未登录状态下（无 access token）的 401 不应触发 forceLogout，
  // 否则注册页等公开页面的 API 401 会把用户闪退到登录页。
  if (!getToken()) {
    return Promise.reject(new Error('未登录'))
  }
  if (!getRefreshToken()) {
    forceLogout()
    return Promise.reject(new Error('登录已过期，请重新登录'))
  }
  if (!isRefreshing) {
    isRefreshing = true
    try {
      const data = await doRefreshToken()
      setToken(data.accessToken)
      config.headers['Authorization'] = `Bearer ${data.accessToken}`
      pendingRequests.forEach((cb) => cb(data.accessToken))
      pendingRequests = []
      return service(config)
    } catch (e) {
      pendingRequests = []
      forceLogout()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    } finally {
      isRefreshing = false
    }
  }
  return new Promise((resolve) => {
    pendingRequests.push((newToken) => {
      config.headers['Authorization'] = `Bearer ${newToken}`
      resolve(service(config))
    })
  })
}

const forceLogout = () => {
  removeToken()
  removeRefreshToken()
  if (router.currentRoute.value.path !== '/login') {
    router.replace('/login')
  }
}

export default service
