import { defineStore } from 'pinia'
import { createSessionApi, sendChatMessageApi } from '@/api/ai'

/**
 * AI 问诊会话状态。
 *
 * <p>目的：把 AiConsult.vue 的会话状态（含 loading、进行中的请求）从组件内 ref
 * 上提到 Pinia store。这样无论组件是否卸载（路由切走），发送请求的 Promise
 * 都由 store action 持有，请求完成后结果直接写入 store；切回来时组件从
 * store 读取 loading / messages 即可恢复"正在加载"或"已有回复"的展示。
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
    initialized: false,
    // 是否正在等待 AI 回复（上提到 store，保证组件卸载后切回仍能看到加载态）
    loading: false
  }),

  actions: {
    // 设置当前会话 id（标记为已初始化）
    setSession(id) {
      this.sessionId = id
      this.initialized = true
    },

    // 设置加载态
    setLoading(val) {
      this.loading = val
    },

    // 追加一条消息
    addMessage(msg) {
      this.messages.push(msg)
    },

    // 批量设置消息列表（用于从历史接口恢复）
    setMessages(list) {
      this.messages = Array.isArray(list) ? list : []
    },

    // 创建新会话（调后端拿 sessionId 并写入 store）
    // 组件切回时若 sessionId 为空才会调用，正常切回不会重复建会话。
    async initSession() {
      try {
        const res = await createSessionApi()
        this.setSession(res.data.sessionId)
      } catch (err) {
        console.error('创建会话失败', err)
      }
    },

    // 发送消息：整个"入消息→等AI回复→写回复"流程都在 store action 内执行，
    // 返回的 Promise 由 store 持有。即使组件卸载，请求仍会继续，完成后
    // 结果写入 store；组件重新挂载时通过 storeToRefs 读取 loading/messages
    // 即可恢复展示。注意：store action 里不能调用组件的 scrollToBottom，
    // 滚动由组件 watch messages/loading 自行处理。
    async sendMessage(text) {
      const content = String(text || '').trim()
      if (!content || this.loading) return

      // 确保有会话（防御：极端情况下 store 被清空又点发送）
      if (!this.sessionId) {
        await this.initSession()
        if (!this.sessionId) return
      }

      // 用户消息入 store
      this.addMessage({
        id: Date.now(),
        role: 'user',
        content
      })

      this.loading = true
      try {
        const res = await sendChatMessageApi(this.sessionId, content)
        this.addMessage({
          // AI 消息 id 不能用 sessionId（同一会话多次回复会重复，Vue v-for key 冲突）
          id: Date.now(),
          role: 'ai',
          content: res.data.answer || res.data.aiAnswer || '暂无回复'
        })
      } catch (e) {
        // 发送失败时保留用户消息并追加错误提示，避免用户等待后消息消失无反馈
        this.addMessage({
          id: Date.now(),
          role: 'ai',
          content: '抱歉，AI 问诊服务暂时不可用，请稍后重试。'
        })
      } finally {
        this.loading = false
      }
    },

    // 清空整个会话（登出/手动重置时调用）
    clear() {
      this.sessionId = ''
      this.messages = []
      this.initialized = false
      this.loading = false
    }
  }
})
