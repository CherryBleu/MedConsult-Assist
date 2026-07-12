import { defineStore } from 'pinia'
import { setToken, getToken, removeToken, setRefreshToken, getRefreshToken, removeRefreshToken } from '@/utils/auth'
import { loginApi, getUserInfoApi, logoutApi, refreshTokenApi } from '@/api/user'
import { useAiChatStore } from '@/store/modules/aiChat'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    refreshToken: getRefreshToken(),
    userInfo: {},
    role: ''
  }),

  actions: {
    // 登录
    async login(loginForm) {
      const res = await loginApi(loginForm)
      this.token = res.data.accessToken
      this.refreshToken = res.data.refreshToken
      setToken(res.data.accessToken)
      setRefreshToken(res.data.refreshToken)
      return res
    },

    // 获取用户信息
    async getUserInfo() {
      const res = await getUserInfoApi()
      this.userInfo = res.data
      this.role = res.data.role
      return res.data
    },

    // 刷新token
    async refreshAccessToken() {
      const res = await refreshTokenApi(this.refreshToken)
      this.token = res.data.accessToken
      setToken(res.data.accessToken)
      return res.data.accessToken
    },

    // 退出登录
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 忽略登出接口错误，强制清除本地状态
      }
      this.token = ''
      this.refreshToken = ''
      this.userInfo = {}
      this.role = ''
      removeToken()
      removeRefreshToken()
      // 清空 AI 问诊会话缓存，避免换账号登录后看到上一个用户的聊天记录（越权信息泄露）
      useAiChatStore().clear()
    }
  }
})