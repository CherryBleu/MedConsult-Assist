<template>
  <section class="page-container consult-shell" aria-labelledby="ai-consult-title">
    <header class="consult-header">
      <div class="consult-heading">
        <div class="ai-mark" aria-hidden="true">
          <el-icon :size="22"><Cpu /></el-icon>
        </div>
        <div>
          <h1 id="ai-consult-title">AI智能问诊</h1>
          <p>RAG 知识检索、风险规则和科室建议会随回答一起展示。</p>
        </div>
      </div>
      <el-tag size="large" type="info" effect="plain">仅供参考，不替代医生诊断</el-tag>
    </header>

    <div class="consult-layout">
      <main class="consult-main" aria-label="AI 问诊消息">
        <section class="message-timeline" ref="messagesRef" aria-live="polite">
          <article class="welcome-message">
            <el-avatar :size="36" class="ai-avatar">
              <el-icon><Cpu /></el-icon>
            </el-avatar>
            <div class="message-stack">
              <div class="message-bubble ai-bubble">
                您好，我是智能问诊助手。请描述您的症状，我会先提示风险，再给出可追溯的初步建议。
              </div>
            </div>
          </article>

          <article
            v-for="msg in messageList"
            :key="msg.id"
            class="message-item"
            :class="{ 'user-message': msg.role === 'user' }"
          >
            <template v-if="msg.role === 'ai'">
              <el-avatar :size="36" class="ai-avatar">
                <el-icon><Cpu /></el-icon>
              </el-avatar>

              <div class="message-stack">
                <section v-if="msg.failed" class="chat-error" role="alert">
                  <div>
                    <strong>AI 问诊服务暂时不可用</strong>
                    <p>{{ msg.content }}</p>
                  </div>
                  <el-button
                    type="primary"
                    plain
                    class="ai-consult-action"
                    :disabled="loading"
                    @click="retryMessage(msg.retryText)"
                  >
                    重试
                  </el-button>
                </section>

                <template v-else>
                  <section
                    v-if="hasSafetySummary(msg)"
                    class="safety-summary"
                    :class="riskClass(msg)"
                    data-testid="safety-summary"
                    :role="isUrgent(msg) ? 'alert' : undefined"
                    :aria-live="isUrgent(msg) ? undefined : 'polite'"
                  >
                    <div class="safety-head">
                      <span class="risk-label">{{ riskLabel(msg.riskLevel) }}</span>
                      <strong>{{ isUrgent(msg) ? '优先处理急症风险' : '初步风险提示' }}</strong>
                    </div>
                    <p class="safety-callout">{{ safetyAdvice(msg) }}</p>

                    <div class="safety-grid">
                      <div v-if="msg.suggestedDepartments?.length">
                        <span class="summary-label">建议科室</span>
                        <div class="chip-row">
                          <el-tag
                            v-for="dept in msg.suggestedDepartments"
                            :key="dept"
                            effect="plain"
                            :type="isUrgent(msg) ? 'danger' : 'success'"
                          >
                            {{ dept }}
                          </el-tag>
                        </div>
                      </div>
                      <div v-if="msg.possibleCauses?.length">
                        <span class="summary-label">可能原因</span>
                        <ul class="compact-list">
                          <li v-for="cause in msg.possibleCauses" :key="cause">{{ cause }}</li>
                        </ul>
                      </div>
                    </div>
                  </section>

                  <div class="message-bubble ai-bubble">{{ msg.content }}</div>

                  <section v-if="hasEvidence(msg)" class="evidence-panel" aria-label="RAG 检索证据">
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
                      <summary
                        class="ai-consult-action"
                        data-testid="evidence-toggle"
                        tabindex="0"
                      >
                        <span class="summary-main">
                          <el-icon><Search /></el-icon>
                          向量匹配
                        </span>
                        <span class="summary-count">{{ msg.vectorMatches.length }} 条</span>
                      </summary>
                      <div class="vector-list">
                        <article
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
                            <span>{{ match.fieldName || 'text' }}</span>
                            <span v-if="match.vectorId"> · {{ match.vectorId }}</span>
                            <span v-if="match.sourceId"> · {{ match.sourceId }}</span>
                          </div>
                          <p v-if="match.chunkText" class="vector-snippet">{{ match.chunkText }}</p>
                        </article>
                      </div>
                    </details>
                  </section>

                  <section v-else class="evidence-empty" aria-live="polite">
                    本次回答未返回可展示证据，请结合线下就医和医生判断。
                  </section>
                </template>
              </div>
            </template>

            <template v-else>
              <div class="message-stack user-stack">
                <div class="message-bubble user-bubble">{{ msg.content }}</div>
              </div>
              <el-avatar :size="36" class="user-avatar">
                {{ (userStore.userInfo.name || '?').charAt(0) }}
              </el-avatar>
            </template>
          </article>

          <article v-if="loading" class="message-item" role="status" aria-live="polite">
            <el-avatar :size="36" class="ai-avatar">
              <el-icon><Cpu /></el-icon>
            </el-avatar>
            <div class="message-stack">
              <div class="message-bubble ai-bubble loading-bubble">
                <el-icon class="is-loading"><Loading /></el-icon>
                正在分析症状和知识库证据...
              </div>
            </div>
          </article>
        </section>

        <form class="consult-composer" aria-label="发送症状描述" @submit.prevent="sendMessage">
          <label class="composer-label" for="symptom-input">症状描述</label>
          <div class="composer-row">
            <el-input
              id="symptom-input"
              v-model="inputText"
              type="textarea"
              :rows="3"
              :autosize="{ minRows: 2, maxRows: 5 }"
              maxlength="500"
              show-word-limit
              placeholder="请输入您的症状描述..."
              aria-label="症状描述"
              aria-keyshortcuts="Control+Enter"
              @keydown.ctrl.enter.prevent="sendMessage"
            />
            <el-button
              class="send-button ai-consult-action"
              type="primary"
              native-type="submit"
              :disabled="!inputText.trim() || loading"
            >
              发送
            </el-button>
          </div>

          <div class="quick-questions" aria-label="快捷提问">
            <span class="quick-label">快捷提问</span>
            <el-button
              v-for="q in quickQuestions"
              :key="q"
              class="quick-chip ai-consult-action"
              size="small"
              type="primary"
              plain
              @click="quickSend(q)"
            >
              {{ q }}
            </el-button>
          </div>
        </form>
      </main>

      <aside class="consult-aside" aria-label="问诊提示与会话状态">
        <section class="side-panel">
          <h2>问诊提示</h2>
          <ul>
            <li>请写清症状、持续时间、诱因和伴随表现。</li>
            <li>胸痛、大汗、意识异常、呼吸困难等情况优先线下急诊。</li>
            <li>AI 回答只作为分诊和就医准备参考。</li>
          </ul>
        </section>
        <section class="side-panel">
          <h2>当前会话</h2>
          <dl class="session-facts">
            <div>
              <dt>消息数</dt>
              <dd>{{ messageList.length }}</dd>
            </div>
            <div>
              <dt>证据状态</dt>
              <dd>{{ latestEvidenceStatus }}</dd>
            </div>
            <div>
              <dt>安全策略</dt>
              <dd>风险摘要优先</dd>
            </div>
          </dl>
        </section>
      </aside>
    </div>
  </section>
</template>

<script setup>
// keep-alive include 按组件 name 匹配，必须显式声明 name（与路由 name 一致）
defineOptions({ name: 'AiConsult' })

import { computed, ref, nextTick, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Cpu, Document, Loading, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useAiChatStore } from '@/store/modules/aiChat'

const userStore = useUserStore()
const aiChatStore = useAiChatStore()

const { messages: messageList, loading } = storeToRefs(aiChatStore)

const messagesRef = ref(null)
const inputText = ref('')

const quickQuestions = ['咳嗽有痰怎么办', '胸痛大汗怎么办', '胸闷是什么原因']

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

watch(
  () => [messageList.value.length, loading.value],
  () => {
    scrollToBottom()
  }
)

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return
  inputText.value = ''
  await aiChatStore.sendMessage(text)
}

const retryMessage = async (text) => {
  const retryText = String(text || '').trim()
  if (!retryText || loading.value) return
  await aiChatStore.sendMessage(retryText)
}

const quickSend = (text) => {
  inputText.value = text
  sendMessage()
}

const hasEvidence = (msg) => {
  return Boolean(msg?.citations?.length || msg?.vectorMatches?.length)
}

const hasSafetySummary = (msg) => {
  return Boolean(
    msg?.riskLevel ||
    msg?.emergencyAdvice ||
    msg?.possibleCauses?.length ||
    msg?.suggestedDepartments?.length
  )
}

const isUrgent = (msg) => {
  return Boolean(msg?.emergencyAdvice || ['HIGH', 'CRITICAL'].includes(String(msg?.riskLevel || '').toUpperCase()))
}

const riskLabel = (riskLevel) => {
  const map = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
    CRITICAL: '危急'
  }
  return map[String(riskLevel || '').toUpperCase()] || '待评估'
}

const riskClass = (msg) => {
  if (isUrgent(msg)) return 'is-urgent'
  if (String(msg?.riskLevel || '').toUpperCase() === 'MEDIUM') return 'is-warning'
  return 'is-stable'
}

const safetyAdvice = (msg) => {
  if (isUrgent(msg)) {
    return '建议尽快就医或拨打 120，并优先前往急诊科评估。'
  }
  if (String(msg?.riskLevel || '').toUpperCase() === 'MEDIUM') {
    return '建议尽快预约相关科室，若症状加重请及时线下就医。'
  }
  return '当前未识别到急症信号，请继续观察症状变化，并按建议科室就医确认。'
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

const latestEvidenceStatus = computed(() => {
  const latestAi = [...messageList.value].reverse().find(msg => msg.role === 'ai' && !msg.failed)
  if (!latestAi) return '尚未问诊'
  return hasEvidence(latestAi) ? '已返回引用证据' : '未返回引用证据'
})

onMounted(async () => {
  if (aiChatStore.initialized && aiChatStore.sessionId) {
    await scrollToBottom()
    return
  }
  await aiChatStore.initSession()
})
</script>

<style scoped>
.consult-shell {
  min-height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-x: hidden;
}

.consult-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}

.consult-heading {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.ai-mark {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  color: #0369a1;
  background: #e0f2fe;
}

.consult-heading h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 24px;
  line-height: 1.3;
  font-weight: 700;
}

.consult-heading p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.consult-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 320px);
  gap: 16px;
}

.consult-main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #fff;
}

.message-timeline {
  flex: 1;
  min-height: 420px;
  max-height: calc(100vh - 300px);
  padding: 18px;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: #f8fafc;
}

.welcome-message,
.message-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.message-item.user-message {
  flex-direction: row-reverse;
}

.message-stack {
  width: min(760px, calc(100% - 48px));
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.user-stack {
  align-items: flex-end;
}

.message-bubble {
  max-width: 100%;
  padding: 12px 14px;
  border-radius: 8px;
  line-height: 1.6;
  font-size: 16px;
  word-break: break-word;
}

.ai-bubble {
  color: var(--text-primary);
  background: #fff;
  border: 1px solid var(--border-lighter);
}

.user-bubble {
  color: #fff;
  background: #0284c7;
  border-top-right-radius: 4px;
}

.ai-avatar {
  flex-shrink: 0;
  background: #0369a1;
}

.user-avatar {
  flex-shrink: 0;
  background: #15803d;
}

.loading-bubble {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.safety-summary,
.chat-error,
.evidence-panel,
.evidence-empty {
  padding: 14px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--border-light);
}

.safety-summary {
  border-left-width: 4px;
}

.safety-summary.is-urgent {
  border-left-color: #b91c1c;
  background: #fff7f7;
}

.safety-summary.is-warning {
  border-left-color: #b45309;
  background: #fffbeb;
}

.safety-summary.is-stable {
  border-left-color: #15803d;
  background: #f0fdf4;
}

.safety-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}

.risk-label {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: #0369a1;
}

.is-urgent .risk-label {
  background: #b91c1c;
}

.is-warning .risk-label {
  background: #b45309;
}

.is-stable .risk-label {
  background: #15803d;
}

.safety-callout {
  margin: 10px 0 0;
  color: var(--text-primary);
  line-height: 1.6;
}

.safety-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.summary-label,
.field-label,
.vector-meta,
.summary-count {
  display: inline-block;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.compact-list {
  margin: 6px 0 0;
  padding-left: 18px;
  color: var(--text-primary);
  line-height: 1.6;
}

.chat-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-color: #fecaca;
  border-left: 4px solid #b91c1c;
  background: #fff7f7;
}

.chat-error strong {
  color: #991b1b;
}

.chat-error p {
  margin: 4px 0 0;
  color: var(--text-primary);
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
  color: var(--text-primary);
  font-weight: 700;
}

.evidence-section-title {
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.citation-card,
.vector-item {
  padding: 12px;
  border: 1px solid var(--border-lighter);
  border-radius: 8px;
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
  color: var(--text-primary);
  font-size: 14px;
}

.citation-snippet,
.vector-snippet {
  margin: 8px 0 0;
  color: var(--text-regular);
  font-size: 14px;
  line-height: 1.6;
}

.field-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.score-pill {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  color: #075985;
  background: #e0f2fe;
  font-size: 12px;
  font-weight: 700;
}

.vector-details {
  margin-top: 10px;
}

.vector-details summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 10px;
  border-radius: 8px;
  color: var(--text-primary);
  cursor: pointer;
  list-style: none;
}

.vector-details summary::-webkit-details-marker {
  display: none;
}

.vector-details summary:focus-visible,
.ai-consult-action:focus-visible {
  outline: 3px solid #0ea5e9;
  outline-offset: 2px;
}

.vector-list {
  padding-top: 8px;
}

.evidence-empty {
  color: var(--text-secondary);
  font-size: 14px;
}

.consult-composer {
  padding: 14px;
  border-top: 1px solid var(--border-light);
  background: #fff;
}

.composer-label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-primary);
  font-weight: 700;
}

.composer-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 92px;
  gap: 10px;
  align-items: stretch;
}

.send-button {
  height: auto;
}

.ai-consult-action {
  min-width: 44px;
  min-height: 44px;
}

.quick-questions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.quick-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.quick-chip {
  margin-left: 0;
}

.consult-aside {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-panel {
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #fff;
}

.side-panel h2 {
  margin: 0 0 10px;
  color: var(--text-primary);
  font-size: 16px;
  line-height: 1.4;
}

.side-panel ul {
  margin: 0;
  padding-left: 18px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.session-facts {
  display: grid;
  gap: 8px;
  margin: 0;
}

.session-facts div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}

.session-facts div:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.session-facts dt,
.session-facts dd {
  margin: 0;
}

.session-facts dt {
  color: var(--text-secondary);
}

.session-facts dd {
  color: var(--text-primary);
  font-weight: 700;
  text-align: right;
}

@media (max-width: 900px) {
  .consult-layout {
    grid-template-columns: 1fr;
  }

  .consult-aside {
    order: -1;
  }
}

@media (max-width: 768px) {
  .consult-shell {
    min-height: calc(100vh - 76px);
    gap: 12px;
  }

  .consult-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .consult-main {
    min-height: calc(100vh - 210px);
  }

  .message-timeline {
    min-height: 360px;
    max-height: none;
    padding: 14px;
    padding-bottom: 18px;
  }

  .message-stack {
    width: calc(100% - 48px);
  }

  .message-bubble {
    font-size: 15px;
  }

  .safety-grid,
  .composer-row {
    grid-template-columns: 1fr;
  }

  .send-button {
    width: 100%;
    height: 44px;
  }

  .chat-error,
  .evidence-title,
  .citation-head,
  .vector-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .consult-composer {
    position: sticky;
    bottom: 0;
    z-index: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .message-timeline {
    scroll-behavior: auto;
  }

  * {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
