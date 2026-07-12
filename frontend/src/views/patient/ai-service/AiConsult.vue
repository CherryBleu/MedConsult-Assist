<template>
  <div class="page-container chat-page">
    <div class="chat-card">
      <!-- 头部 -->
      <div class="chat-header">
        <div class="header-left">
          <el-icon :size="24" color="#1677ff"><Cpu /></el-icon>
          <span class="header-title">AI智能问诊</span>
        </div>
        <el-tag size="small" type="info">仅供参考</el-tag>
      </div>

      <!-- 消息区域 -->
      <div class="chat-messages" ref="messagesRef">
        <div class="welcome-message">
          <el-avatar :size="36" class="ai-avatar">
            <el-icon><Cpu /></el-icon>
          </el-avatar>
          <div class="message-bubble ai-bubble">
            您好，我是智能问诊助手。请描述您的症状，我会为您提供初步建议。
          </div>
        </div>

        <div 
          v-for="msg in messageList" 
          :key="msg.id"
          class="message-item"
          :class="{ 'user-message': msg.role === 'user' }"
        >
          <template v-if="msg.role === 'ai'">
            <el-avatar :size="36" class="ai-avatar">
              <el-icon><Cpu /></el-icon>
            </el-avatar>
            <div class="message-bubble ai-bubble">{{ msg.content }}</div>
          </template>
          <template v-else>
            <div class="message-bubble user-bubble">{{ msg.content }}</div>
            <el-avatar :size="36" class="user-avatar">
              {{ (userStore.userInfo.name || '?').charAt(0) }}
            </el-avatar>
          </template>
        </div>

        <div v-if="loading" class="message-item">
          <el-avatar :size="36" class="ai-avatar">
            <el-icon><Cpu /></el-icon>
          </el-avatar>
          <div class="message-bubble ai-bubble loading-bubble">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在分析中...
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-bar">
        <el-input
          v-model="inputText"
          placeholder="请输入您的症状描述..."
          @keyup.enter="sendMessage"
        >
          <template #append>
            <el-button :disabled="!inputText.trim() || loading" @click="sendMessage">
              发送
            </el-button>
          </template>
        </el-input>
        <div class="quick-questions">
          <span class="quick-label">快捷提问：</span>
          <el-button 
            v-for="q in quickQuestions" 
            :key="q" 
            size="small" 
            type="primary" 
            plain
            @click="quickSend(q)"
          >
            {{ q }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { Cpu, Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useAiChatStore } from '@/store/modules/aiChat'
import { createSessionApi, sendChatMessageApi } from '@/api/ai'

const userStore = useUserStore()
const aiChatStore = useAiChatStore()

// 会话/消息状态上提到 Pinia store：组件卸载（路由切走）后状态不丢，
// 切回来时 onMounted 直接复用已有会话，避免消息清空。
// storeToRefs 保证模板响应式；模板里仍用 messageList / sessionId 名字。
const { sessionId, messages: messageList } = storeToRefs(aiChatStore)

const messagesRef = ref(null)
const inputText = ref('')
const loading = ref(false)

const quickQuestions = ['咳嗽有痰怎么办', '胸闷是什么原因', '头痛该挂什么科']

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// 初始化新会话（调后端拿 sessionId 并写入 store）
const initSession = async () => {
  try {
    const res = await createSessionApi()
    aiChatStore.setSession(res.data.sessionId)
  } catch (err) {
    console.error('创建会话失败', err)
  }
}

// 发送消息
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  // 确保有会话（防御：极端情况下 store 被清空又点发送）
  if (!sessionId.value) {
    await initSession()
    if (!sessionId.value) return
  }

  // 添加用户消息（写 store，模板响应式自动更新）
  aiChatStore.addMessage({
    id: Date.now(),
    role: 'user',
    content: text
  })
  inputText.value = ''
  scrollToBottom()

  loading.value = true
  try {
    const res = await sendChatMessageApi(sessionId.value, text)
    aiChatStore.addMessage({
      id: res.data.sessionId || Date.now(),
      role: 'ai',
      content: res.data.answer || res.data.aiAnswer || '暂无回复'
    })
  } catch (e) {
    // 发送失败时保留用户消息并追加错误提示，避免用户等待后消息消失无反馈
    aiChatStore.addMessage({
      id: Date.now(),
      role: 'ai',
      content: '抱歉，AI 问诊服务暂时不可用，请稍后重试。'
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const quickSend = (text) => {
  inputText.value = text
  sendMessage()
}

onMounted(async () => {
  // 路由切回时：store 已有会话且有消息 → 直接复用，仅恢复滚动到底部
  if (aiChatStore.initialized && sessionId.value && messageList.value.length > 0) {
    await scrollToBottom()
    return
  }
  // 首次进入或会话已失效：创建新会话
  await initSession()
})
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 100px);
  display: flex;
}
.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: var(--radius-base);
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 消息区 */
.chat-messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: var(--bg-page);
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: flex-start;
}
.message-item.user-message {
  flex-direction: row-reverse;
}

.message-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 14px;
  word-break: break-word;
}
.ai-bubble {
  background: #fff;
  border-top-left-radius: 4px;
  color: var(--text-primary);
}
.user-bubble {
  background: var(--primary-color);
  color: #fff;
  border-top-right-radius: 4px;
}

.ai-avatar {
  background: linear-gradient(135deg, #1677ff, #4096ff);
  flex-shrink: 0;
}
.user-avatar {
  background: #52c41a;
  flex-shrink: 0;
}

.loading-bubble {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.welcome-message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

/* 输入区 */
.chat-input-bar {
  padding: 16px 20px;
  border-top: 1px solid var(--border-light);
  background: #fff;
}
.quick-questions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.quick-label {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>