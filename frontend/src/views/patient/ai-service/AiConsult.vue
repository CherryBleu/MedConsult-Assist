<template>
  <div class="page-container chat-page">
    <div class="chat-card">
      <!-- 头部 -->
      <div class="chat-header">
        <div class="header-left">
          <div class="ai-logo"><el-icon :size="22"><Cpu /></el-icon></div>
          <div>
            <span class="header-title">AI智能问诊</span>
            <p class="header-subtitle">RAG 知识检索 · 风险规则 · 科室建议</p>
          </div>
        </div>
        <el-tag size="small" type="info" effect="plain">仅供参考，不替代诊断</el-tag>
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
            <div class="ai-message-stack">
              <div class="message-bubble ai-bubble">{{ msg.content }}</div>
              <div v-if="hasEvidence(msg)" class="evidence-panel" aria-label="RAG 检索证据">
                <div class="evidence-title">
                  <span class="evidence-title-main">
                    <el-icon><Document /></el-icon>
                    检索证据
                  </span>
                  <el-tag v-if="msg.answerSource" size="small" type="success" effect="plain">
                    {{ answerSourceLabel(msg.answerSource) }}
                  </el-tag>
                </div>

                <div v-if="msg.citations?.length" class="evidence-section">
                  <div class="evidence-section-title">引用片段</div>
                  <article
                    v-for="citation in msg.citations"
                    :key="citationKey(citation)"
                    class="citation-card"
                  >
                    <div class="citation-head">
                      <strong>{{ citation.diseaseName || citation.sourceId || '疾病 JSON 条目' }}</strong>
                      <span v-if="typeof citation.score === 'number'" class="score-pill">
                        {{ formatScore(citation.score) }}
                      </span>
                    </div>
                    <p v-if="citation.snippet" class="citation-snippet">{{ citation.snippet }}</p>
                    <div v-if="citation.matchedFields?.length" class="field-row">
                      <span class="field-label">匹配字段</span>
                      <el-tag
                        v-for="field in citation.matchedFields"
                        :key="`${citationKey(citation)}-${field}`"
                        size="small"
                        effect="plain"
                      >
                        {{ field }}
                      </el-tag>
                    </div>
                  </article>
                </div>

                <details v-if="msg.vectorMatches?.length" class="vector-details">
                  <summary>
                    <span class="summary-main">
                      <el-icon><Search /></el-icon>
                      向量匹配
                    </span>
                    <span class="summary-count">{{ msg.vectorMatches.length }} 条</span>
                  </summary>
                  <div class="vector-list">
                    <div
                      v-for="match in msg.vectorMatches"
                      :key="vectorMatchKey(match)"
                      class="vector-item"
                    >
                      <div class="vector-head">
                        <strong>{{ match.diseaseName || match.sourceId || '疾病 JSON 条目' }}</strong>
                        <span v-if="typeof match.score === 'number'" class="score-pill">
                          {{ formatScore(match.score) }}
                        </span>
                      </div>
                      <div class="vector-meta">
                        {{ match.fieldName || 'text' }}
                        <span v-if="match.sourceId"> · {{ match.sourceId }}</span>
                      </div>
                      <p v-if="match.chunkText" class="vector-snippet">{{ match.chunkText }}</p>
                    </div>
                  </div>
                </details>
              </div>
            </div>
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
// keep-alive include 按组件 name 匹配，必须显式声明 name（与路由 name 一致）
defineOptions({ name: 'AiConsult' })

import { ref, nextTick, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Cpu, Document, Loading, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useAiChatStore } from '@/store/modules/aiChat'

const userStore = useUserStore()
const aiChatStore = useAiChatStore()

// 会话/消息/loading 全部上提到 Pinia store：组件卸载（路由切走）后状态不丢，
// 切回来时直接读 store 即可恢复"正在加载"或"已有消息"。发送请求的 Promise
// 也由 store action 持有，组件销毁不影响请求继续。
// storeToRefs 保证模板响应式；模板里仍用 messageList / loading 名字。
const { messages: messageList, loading } = storeToRefs(aiChatStore)

const messagesRef = ref(null)
const inputText = ref('')

const quickQuestions = ['咳嗽有痰怎么办', '胸闷是什么原因', '头痛该挂什么科']

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// 监听 store 状态变化自动滚动：覆盖三条路径——
//   1) 新消息入 store（用户发/AI 回）
//   2) loading 显隐（加载气泡出现/消失）
//   3) 路由切回时 onMounted 主动触发一次
// 这样即便 AI 回复是在组件切走期间到达、切回后才渲染，也能滚到底部。
watch(
  () => [messageList.value.length, loading.value],
  () => {
    scrollToBottom()
  }
)

// 发送消息：仅做输入读取 + 调 store action，真正的"入消息→等回复→写回复"
// 全在 store.sendMessage 内完成，组件不持有请求 Promise。
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return
  inputText.value = ''
  await aiChatStore.sendMessage(text)
}

const quickSend = (text) => {
  inputText.value = text
  sendMessage()
}

const hasEvidence = (msg) => {
  return Boolean(msg?.citations?.length || msg?.vectorMatches?.length)
}

const formatScore = (score) => {
  return typeof score === 'number' ? `${Math.round(score * 100)}%` : ''
}

const answerSourceLabel = (source) => {
  if (source === 'VECTOR_SEARCH_AND_RULE') {
    return '向量检索 + 规则'
  }
  if (source === 'VECTOR_SEARCH_AND_RULE_DEGRADED') {
    return '降级检索 + 规则'
  }
  return source
}

const citationKey = (citation) => {
  return [
    citation.sourceId,
    citation.diseaseName,
    citation.snippet
  ].filter(Boolean).join('|')
}

const vectorMatchKey = (match) => {
  return [
    match.vectorId,
    match.sourceId,
    match.fieldName,
    match.chunkText
  ].filter(Boolean).join('|')
}

onMounted(async () => {
  // 路由切回时：store 已初始化且 sessionId 还在 → 直接复用现有会话/loading，
  // 不重置 loading（若 store.loading=true 说明有请求在飞，继续显示加载）。
  if (aiChatStore.initialized && aiChatStore.sessionId) {
    await scrollToBottom()
    return
  }
  // 首次进入或会话已失效：创建新会话
  await aiChatStore.initSession()
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
  background: rgba(255, 255, 255, .86);
  border: 1px solid rgba(255,255,255,.72);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(14px);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 22px;
  border-bottom: 1px solid var(--border-lighter);
  background: linear-gradient(135deg, rgba(255,255,255,.94), rgba(240,247,255,.88));
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ai-logo {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--gradient-primary);
  box-shadow: 0 10px 24px rgba(22, 119, 255, .24);
}
.header-title {
  display: block;
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
}
.header-subtitle {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-secondary);
}

/* 消息区 */
.chat-messages {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: radial-gradient(circle at 8% 12%, rgba(22,119,255,.08), transparent 24%), #f7fbff;
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
.ai-message-stack {
  width: min(780px, 78%);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-message-stack .message-bubble {
  max-width: 100%;
}
.ai-bubble {
  background: #fff;
  border: 1px solid var(--border-lighter);
  border-top-left-radius: 4px;
  color: var(--text-primary);
  box-shadow: 0 8px 24px rgba(15, 35, 95, .06);
}

.evidence-panel {
  padding: 14px;
  border: 1px solid rgba(22, 119, 255, .14);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255,255,255,.96), rgba(244,250,255,.92));
  box-shadow: 0 10px 24px rgba(15, 35, 95, .06);
}
.evidence-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.evidence-title-main,
.summary-main {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: var(--text-primary);
}
.evidence-section-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}
.citation-card,
.vector-item {
  padding: 12px;
  border: 1px solid var(--border-lighter);
  border-radius: 12px;
  background: #fff;
}
.citation-card + .citation-card,
.vector-item + .vector-item {
  margin-top: 8px;
}
.citation-head,
.vector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  color: var(--text-primary);
}
.citation-snippet,
.vector-snippet {
  margin: 8px 0 0;
  color: var(--text-regular);
  font-size: 13px;
  line-height: 1.6;
}
.field-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.field-label,
.vector-meta,
.summary-count {
  font-size: 12px;
  color: var(--text-secondary);
}
.score-pill {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  color: #0958d9;
  background: rgba(22, 119, 255, .1);
  font-size: 12px;
  font-weight: 700;
}
.vector-details {
  margin-top: 10px;
}
.vector-details summary {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
  list-style: none;
  border-radius: 12px;
  padding: 0 10px;
  color: var(--text-primary);
}
.vector-details summary::-webkit-details-marker {
  display: none;
}
.vector-details summary:focus-visible {
  outline: 2px solid rgba(22, 119, 255, .45);
  outline-offset: 2px;
}
.vector-list {
  padding-top: 8px;
}
.user-bubble {
  background: var(--gradient-primary);
  color: #fff;
  border-top-right-radius: 4px;
  box-shadow: 0 8px 24px rgba(22,119,255,.18);
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

@media (max-width: 768px) {
  .chat-page {
    height: calc(100vh - 76px);
  }
  .chat-header {
    align-items: flex-start;
    gap: 12px;
  }
  .chat-messages {
    padding: 18px 14px;
  }
  .message-bubble,
  .ai-message-stack {
    max-width: none;
    width: calc(100% - 48px);
  }
  .evidence-title,
  .citation-head,
  .vector-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
