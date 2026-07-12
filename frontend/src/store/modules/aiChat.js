import { defineStore } from 'pinia'

/**
 * AI 问诊会话状态。
 *
 * <p>目的：把 AiConsult.vue 的会话状态从组件内 ref 上提到 Pinia store，
 * 组件卸载（切走路由）后再回来时复用已有会话，避免消息清空。
 *
 * <p>注意：store 仅做内存态持久（路由切换不触发页面刷新）；浏览器整体刷新
 * 后 Pinia 状态会重建，届时 AiConsult.onMounted 会检测到 sessionId 为空
 * 而重新 initSession，行为符合预期。
 *
 * <p>风格对齐 user.js：Options API（state + actions）。
 */
export const useAiChatStore = defineStore('aiChat', {
  state: () => ({
    // 当前会话 id（来自后端 createSessionApi）
    sessionId: '',
    // 消息列表：{ id, role: 'user' | 'ai', content }
    messages: [],
    // 是否已初始化过会话（用于区分"新进页面"与"路由切回"）
    initialized: false
  }),

  actions: {
    // 设置当前会话 id（标记为已初始化）
    setSession(id) {
      this.sessionId = id
      this.initialized = true
    },

    // 追加一条消息
    addMessage(msg) {
      this.messages.push(msg)
    },

    // 批量设置消息列表（用于从历史接口恢复）
    setMessages(list) {
      this.messages = Array.isArray(list) ? list : []
    },

    // 清空整个会话（登出/手动重置时调用）
    clear() {
      this.sessionId = ''
      this.messages = []
      this.initialized = false
    }
  }
})
