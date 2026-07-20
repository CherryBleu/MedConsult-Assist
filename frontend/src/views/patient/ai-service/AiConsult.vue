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

                  <div class="ai-feedback-row" data-testid="ai-feedback-row">
                    <span class="ai-feedback-label">这条回答对您有帮助吗？</span>
                    <div class="ai-feedback-actions">
                      <el-rate
                        v-model="msg.feedbackRating"
                        :max="5"
                        :disabled="msg.feedbackSubmitted"
                        :texts="['很差', '一般', '还行', '有用', '非常有用']"
                        show-text
                        :aria-label="'为这条 AI 回答打分'"
                        :data-testid="`ai-feedback-rate-${msg.id}`"
                      />
                      <div v-if="!msg.feedbackSubmitted" class="ai-feedback-comment">
                        <el-input
                          v-model="msg.feedbackComment"
                          type="textarea"
                          :rows="2"
                          maxlength="200"
                          show-word-limit
                          placeholder="可以补充一句您的使用感受"
                          :aria-label="'补充这条 AI 回答的文字反馈'"
                        />
                        <el-button
                          type="primary"
                          size="small"
                          class="ai-consult-action ai-feedback-submit"
                          :disabled="!msg.feedbackRating || !(msg.feedbackComment || '').trim()"
                          @click="submitFeedback(msg)"
                        >
                          提交反馈
                        </el-button>
                      </div>
                      <span v-if="msg.feedbackSubmitted" class="ai-feedback-thanks" role="status">
                        感谢您的 {{ msg.feedbackRating }} 星评分和文字反馈
                      </span>
                    </div>
                  </div>

                  <section v-if="hasEvidence(msg)" class="evidence-panel" aria-label="相关疾病依据">
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
                          <div class="citation-title">
                            <strong>{{ displayDiseaseName(citation) }}</strong>
                            <span class="citation-source">{{ evidenceSourceLabel(citation.sourceId) }}</span>
                          </div>
                          <span v-if="typeof citation.score === 'number'" class="score-pill">
                            {{ formatScore(citation.score) }}
                          </span>
                        </div>
                        <p v-if="citation.snippet" class="citation-snippet">{{ citation.snippet }}</p>
                        <div v-if="evidenceFieldLabels(citation).length" class="field-row">
                          <span class="field-label">涉及内容</span>
                          <el-tag
                            v-for="field in evidenceFieldLabels(citation)"
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
                            <div class="citation-title">
                              <strong>{{ displayDiseaseName(match) }}</strong>
                              <span class="citation-source">{{ evidenceSourceLabel(match.sourceId) }}</span>
                            </div>
                            <span v-if="typeof match.score === 'number'" class="score-pill">
                              {{ formatScore(match.score) }}
                            </span>
                          </div>
                          <div class="vector-meta">
                            <span>证据字段：{{ fieldLabel(match.fieldName) }}</span>
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
                {{ userInitial }}
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
          <div class="composer-shell" data-testid="desktop-composer">
            <div class="composer-body">
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
            </div>
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
import { ElMessage } from 'element-plus'
import { Cpu, Document, Loading, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useAiChatStore } from '@/store/modules/aiChat'
import { submitFeedbackApi } from '@/api/ai'

const userStore = useUserStore()
const aiChatStore = useAiChatStore()

const { messages: messageList, loading } = storeToRefs(aiChatStore)

const messagesRef = ref(null)
const inputText = ref('')

const quickQuestions = ['咳嗽有痰怎么办', '胸痛大汗怎么办', '胸闷是什么原因']
const evidenceFieldMap = {
  name: '疾病名称',
  category: '疾病分类',
  symptom: '症状表现',
  cause: '可能原因',
  prevent: '日常预防',
  yibao_status: '医保参考',
  get_prob: '常见发病率',
  easy_get: '易感人群',
  get_way: '传播途径',
  acompany: '伴随症状',
  cure_department: '建议就诊科室',
  cure_way: '常见治疗方式',
  cure_lasttime: '恢复周期参考',
  cured_prob: '恢复可能性参考',
  common_drug: '常用药物',
  cost_money: '费用参考',
  check: '相关检查',
  do_eat: '适宜饮食',
  not_eat: '饮食注意',
  recommand_eat: '推荐饮食',
  recommand_drug: '常用药物',
  text: '内容摘要'
}

const userInitial = computed(() => {
  const name = String(userStore.userInfo.name || '').trim()
  return name ? name.charAt(0) : '?'
})

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

// 提交反馈：联动后端 POST /ai/feedback，admin 在 AiFeedback.vue 中查看/回复
// body 字段：aiResultType='SYMPTOM_CHAT'，aiResultId=msg.id，rating=1-5，comment=文字反馈
const submitFeedback = async (msg) => {
  if (!msg || msg.feedbackSubmitted) return
  const value = Number(msg.feedbackRating)
  if (!Number.isInteger(value) || value < 1 || value > 5) {
    ElMessage.warning('请先选择评分')
    return
  }
  const comment = String(msg.feedbackComment || '').trim()
  if (!comment) {
    ElMessage.warning('请补充文字反馈')
    return
  }
  try {
    await submitFeedbackApi({
      aiResultType: 'SYMPTOM_CHAT',
      aiResultId: String(msg.id || ''),
      rating: value,
      comment
    })
    msg.feedbackRating = value
    msg.feedbackSubmitted = true
    msg.feedbackComment = comment
    ElMessage.success('感谢您的反馈')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '反馈提交失败，请稍后重试')
  }
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

const displayDiseaseName = (record) => {
  if (record?.diseaseName) return record.diseaseName
  const sourceId = String(record?.sourceId || '').trim()
  if (!sourceId) return '相关疾病信息'
  const dividerIndex = sourceId.indexOf(':')
  if (dividerIndex >= 0) {
    const label = sourceId.slice(dividerIndex + 1).trim()
    if (label) return label
  }
  return '相关疾病信息'
}

const evidenceSourceLabel = (sourceId) => {
  if (!sourceId) return '知识库资料'
  return '知识库疾病信息'
}

const fieldLabel = (fieldName) => {
  const rawValue = String(fieldName || '').trim()
  if (!rawValue) return '相关内容'

  const normalized = rawValue.toLowerCase()
  if (evidenceFieldMap[normalized]) {
    return evidenceFieldMap[normalized]
  }

  if (/[\u4e00-\u9fa5]/.test(rawValue)) {
    return rawValue
  }

  return '相关内容'
}

const evidenceFieldLabels = (record) => {
  const rawFields = Array.isArray(record?.matchedFields)
    ? record.matchedFields
    : record?.fieldName
      ? [record.fieldName]
      : []

  return [...new Set(rawFields.map(fieldLabel).filter(Boolean))]
}

const answerSourceLabel = (source) => {
  if (source === 'VECTOR_SEARCH_AND_RULE') {
    return '向量检索 + 规则'
  }
  if (source === 'VECTOR_SEARCH_AND_RULE_DEGRADED') {
    return '降级检索 + 规则'
  }
  return '综合分析结果'
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
  --chat-column-max: 820px;
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
  background: #f8fafc;
}

.message-timeline {
  flex: 1;
  min-height: 420px;
  max-height: calc(100vh - 300px);
  padding: 18px 18px 12px;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: transparent;
}

.welcome-message,
.message-item {
  width: min(100%, var(--chat-column-max));
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.welcome-message,
.message-item:not(.user-message) {
  margin-right: auto;
}

.message-item.user-message {
  justify-content: flex-end;
  margin-left: auto;
}

.message-stack {
  flex: 1 1 auto;
  width: auto;
  max-width: calc(100% - 48px);
  min-width: 0;
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
  /* 圆角方向：左上小、其他大，标识"来自左侧 AI 头像方向" */
  border-top-left-radius: 4px;
}

/* AI 回复反馈行：紧贴 message-bubble 下方，与现有色板契合。
   星级评分（el-rate）替换原 boolean 按钮，统一视觉权重。 */
.ai-feedback-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 4px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.ai-feedback-label {
  color: var(--text-secondary);
}

.ai-feedback-actions {
  display: flex;
  flex: 1 1 320px;
  min-width: min(100%, 260px);
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.ai-feedback-actions :deep(.el-rate) {
  /* 与 .ai-feedback-label 文字基线对齐 */
  display: inline-flex;
  align-items: center;
}

.ai-feedback-comment {
  width: min(100%, 420px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 8px;
}

.ai-feedback-submit {
  min-width: 88px;
  min-height: var(--touch-target);
}

.ai-feedback-thanks {
  color: var(--primary-color, #0284c7);
  font-size: 12px;
}

.user-bubble {
  /* 限制最大 85%，让靠右对齐的短消息视觉对比明显（不被长消息占满整行） */
  max-width: 85%;
  color: #fff;
  background: #0284c7;
  /* 圆角方向：右上小、其他大，与 ai-bubble 左右对称（标识"来自右侧用户头像方向"） */
  border-top-left-radius: 12px;
  border-top-right-radius: 4px;
  border-bottom-right-radius: 12px;
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

.citation-title {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.citation-source {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
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

.vector-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
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
  padding: 0 18px 18px;
  border-top: 1px solid var(--border-light);
  background: #f8fafc;
}

.composer-shell {
  /* 与对话消息 .message-item 同宽（--chat-column-max: 820px），并左对齐，
     让 composer 宽度跟随窗口缩放、视觉与消息流严格对齐。 */
  width: min(100%, var(--chat-column-max));
  margin-right: auto;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 12px;
}

.composer-body {
  flex: 1 1 auto;
  max-width: 100%;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  background: #fff;
}

.composer-label {
  display: block;
  margin-bottom: 10px;
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
    padding: 14px 14px 10px;
  }

  .message-stack {
    max-width: calc(100% - 48px);
  }

  .message-bubble {
    font-size: 15px;
  }

  .safety-grid,
  .composer-row,
  .ai-feedback-comment {
    grid-template-columns: 1fr;
  }

  .send-button {
    width: 100%;
    height: 44px;
  }

  .ai-feedback-submit {
    width: 100%;
  }

  .chat-error,
  .evidence-title,
  .citation-head,
  .vector-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .consult-composer {
    padding: 0 14px 14px;
    position: sticky;
    bottom: 0;
    z-index: 1;
  }

  .composer-shell {
    width: 100%;
    gap: 0;
  }

  .composer-body {
    max-width: 100%;
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
