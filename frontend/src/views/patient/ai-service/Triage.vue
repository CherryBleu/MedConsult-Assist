<template>
  <div class="page-container">
    <div class="card-box triage-card">
      <div class="triage-header">
        <div class="triage-header__brand">
          <el-icon :size="32" color="#1677ff"><Cpu /></el-icon>
          <div>
            <h2 class="title">智能分诊</h2>
            <p class="desc">输入症状和持续时间，AI 为您推荐就诊科室</p>
          </div>
        </div>
        <div class="triage-header__badge" aria-live="polite">
          <span class="triage-header__badge-label">当前状态</span>
          <strong>{{ triageStatusLabel }}</strong>
        </div>
      </div>

      <div class="triage-workspace" data-testid="triage-workspace">
        <section class="triage-main-panel" data-testid="triage-workspace-main">
          <div v-if="!result" class="input-section">
            <section class="triage-toolbar" data-testid="triage-duration-toolbar">
              <div class="triage-toolbar__copy">
                <span class="triage-toolbar__eyebrow">先确认症状持续时间</span>
                <h3 class="triage-toolbar__title">持续时间会直接影响分诊优先级判断</h3>
              </div>
              <el-radio-group v-model="duration" class="duration-group">
                <el-radio
                  v-for="item in durationOptions"
                  :key="item.value"
                  :value="item.value"
                  border
                  class="duration-option"
                >
                  <span class="duration-option__label">{{ item.label }}</span>
                  <span class="duration-option__hint">{{ item.hint }}</span>
                </el-radio>
              </el-radio-group>
            </section>

            <el-form label-width="0" class="triage-form">
              <el-form-item label="">
                <div class="symptom-panel">
                  <div class="section-heading">
                    <div>
                      <h3 class="section-heading__title">症状描述</h3>
                      <p class="section-heading__desc">优先写部位、表现、伴随症状和发生时间</p>
                    </div>
                    <span class="section-heading__meta">{{ symptomCount }} 项症状关键词</span>
                  </div>
                  <el-input
                    v-model="symptoms"
                    type="textarea"
                    :rows="5"
                    :disabled="submitting"
                    class="symptom-input"
                    placeholder="请描述您的症状，例如：咳嗽、咳痰、发热3天..."
                    maxlength="200"
                    show-word-limit
                  />
                </div>
              </el-form-item>
              <el-form-item label="">
                <el-button
                  type="primary"
                  size="large"
                  class="submit-btn triage-action"
                  :loading="submitting"
                  :disabled="submitting"
                  @click="handleTriage"
                >
                  开始分诊
                </el-button>
              </el-form-item>
            </el-form>

            <div
              v-if="submitting"
              class="triage-stream"
              role="status"
              aria-live="polite"
              aria-atomic="false"
            >
              <div class="triage-stream__header">
                <span class="triage-stream__dot" aria-hidden="true"></span>
                <div>
                  <div class="triage-stream__title">正在接收分诊建议</div>
                  <div class="triage-stream__stage">{{ streamStage }}</div>
                </div>
              </div>
              <p class="triage-stream__preview">
                {{ streamText || '正在建立 AI 分诊流式连接，请稍候...' }}
              </p>
            </div>

            <el-alert
              v-else-if="errorMsg"
              class="triage-error"
              role="alert"
              type="error"
              :closable="false"
              show-icon
              title="分诊请求失败"
            >
              <p class="triage-error__message">{{ errorMsg }}</p>
              <div class="triage-error__footer">
                <span>请检查症状描述，或稍后重试。AI 分诊不能替代医生诊断。</span>
                <el-button type="primary" size="small" class="triage-action" @click="handleTriage">重试</el-button>
              </div>
            </el-alert>
          </div>

          <div v-else class="result-section" data-testid="triage-result">
            <div class="result-header">
              <div>
                <h3>分诊结果</h3>
                <p class="result-header__desc">优先查看首推科室，再决定后续挂号</p>
              </div>
              <el-button link type="primary" class="triage-action" @click="reset">重新分诊</el-button>
            </div>

            <div class="risk-bar" :class="riskLevel">
              <span>风险等级：{{ riskLabel }}</span>
              <el-tag v-if="result.emergencyRecommended" type="danger">建议急诊</el-tag>
            </div>

            <div class="dept-recommend">
              <h4>推荐就诊科室</h4>
              <div
                v-for="(item, index) in result.recommendations"
                :key="index"
                class="dept-item"
              >
                <div class="dept-rank">{{ index + 1 }}</div>
                <div class="dept-info">
                  <div class="dept-name">{{ item.departmentName }}</div>
                  <div class="dept-reason">{{ item.reason }}</div>
                </div>
                <div class="confidence">
                  <div class="confidence-text">置信度 {{ Math.round((item.confidence || 0) * 100) }}%</div>
                  <el-progress :percentage="Math.round((item.confidence || 0) * 100)" :show-text="false" />
                </div>
                <el-button type="primary" size="small" class="triage-action" @click="goToDept(item.departmentId)">
                  去挂号
                </el-button>
              </div>
              <div v-if="!result.recommendations || result.recommendations.length === 0" class="empty-tip">
                暂无推荐科室，建议前往全科门诊
              </div>
            </div>
          </div>
        </section>

        <aside class="triage-side-panel" data-testid="triage-workspace-sidebar">
          <section class="side-card">
            <div class="side-card__label">本次分诊概况</div>
            <div class="side-card__value">{{ activeDuration.label }}</div>
            <div class="side-card__meta">持续时间区间</div>
            <dl class="side-metrics">
              <div>
                <dt>症状关键词</dt>
                <dd>{{ symptomCount }}</dd>
              </div>
              <div>
                <dt>当前阶段</dt>
                <dd>{{ currentStageLabel }}</dd>
              </div>
            </dl>
          </section>

          <section class="side-card">
            <div class="side-card__label">就诊提醒</div>
            <ul class="side-list">
              <li>急重症或突发加重时优先急诊。</li>
              <li>症状描述尽量包含持续时间与伴随表现。</li>
              <li>AI 分诊结果仅用于就诊分流参考。</li>
            </ul>
          </section>

          <section v-if="submitting || latestStreamLine" class="side-card side-card--live">
            <div class="side-card__label">{{ submitting ? '实时分诊片段' : '最后一次分诊片段' }}</div>
            <p class="side-live">{{ latestStreamLine || '系统正在整理分诊内容。' }}</p>
          </section>

          <section v-if="primaryRecommendation" class="side-card side-card--success">
            <div class="side-card__label">首推科室</div>
            <div class="side-card__value side-card__value--compact">{{ primaryRecommendation.departmentName }}</div>
            <p class="side-card__text">{{ primaryRecommendation.reason }}</p>
          </section>

          <section class="side-card side-card--warning">
            <div class="side-card__label">注意事项</div>
            <div class="tip-box">
              <el-icon><InfoFilled /></el-icon>
              <span>AI分诊结果仅供参考，不能替代医生诊断，急重症请立即就医。</span>
            </div>
          </section>
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'Triage' })

import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cpu, InfoFilled } from '@element-plus/icons-vue'
import { DEFAULT_TRIAGE_DURATION, TRIAGE_DURATION_OPTIONS, useTriageStore } from '@/store/modules/triage'
import { triageStreamApi } from '@/api/ai'

const router = useRouter()
const triageStore = useTriageStore()
const { symptoms, duration, submitting, result } = storeToRefs(triageStore)
const durationOptions = TRIAGE_DURATION_OPTIONS
const streamText = ref('')
const streamStage = ref('准备连接')
const errorMsg = ref('')

const splitSymptoms = () => String(symptoms.value || '')
  .split(/[,，、\n]/)
  .map(item => item.trim())
  .filter(Boolean)

const symptomCount = computed(() => splitSymptoms().length)

const activeDuration = computed(() => (
  durationOptions.find(item => item.value === duration.value)
  || durationOptions.find(item => item.value === DEFAULT_TRIAGE_DURATION)
  || durationOptions[0]
))

const latestStreamLine = computed(() => {
  const lines = String(streamText.value || '')
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)
  return lines.length ? lines[lines.length - 1] : ''
})

const riskLevel = computed(() => {
  if (!result.value) return 'LOW'
  if (result.value.emergencyRecommended) return 'HIGH'
  const scores = (result.value.recommendations || []).map(item => item.confidence || 0)
  const maxConf = scores.length ? Math.max(...scores) : 0
  if (maxConf >= 0.8) return 'LOW'
  if (maxConf >= 0.5) return 'MEDIUM'
  return 'MEDIUM'
})

const riskLabel = computed(() => {
  const map = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '危重' }
  return map[riskLevel.value] || '低风险'
})

const primaryRecommendation = computed(() => result.value?.recommendations?.[0] || null)

const triageStatusLabel = computed(() => {
  if (submitting.value) return '分析中'
  if (result.value) return '已生成结果'
  return '待提交'
})

const currentStageLabel = computed(() => {
  if (submitting.value) return streamStage.value
  if (result.value) return riskLabel.value
  return '待提交'
})

const getStreamText = (payload) => {
  if (payload == null) return ''
  if (typeof payload === 'string') return payload
  if (payload.departmentName) {
    const confidence = Math.round((payload.confidence || 0) * 100)
    return `${payload.departmentName}：${payload.reason || '正在评估匹配度'}（置信度 ${confidence}%）`
  }
  return payload.token == null ? '' : String(payload.token)
}

const handleTriage = async () => {
  if (!symptoms.value.trim()) {
    ElMessage.warning('请描述您的症状')
    return
  }
  if (submitting.value) return

  submitting.value = true
  result.value = null
  errorMsg.value = ''
  streamText.value = ''
  streamStage.value = '正在连接 AI 分诊服务'
  try {
    const res = await triageStreamApi({
      symptoms: splitSymptoms(),
      duration: duration.value
    }, {
      onStart: () => {
        streamStage.value = 'AI 已开始分析症状'
      },
      onDelta: (payload) => {
        const text = getStreamText(payload)
        if (text) streamText.value += streamText.value ? `\n${text}` : text
      },
      onResult: (payload) => {
        result.value = payload
      },
      onDone: () => {
        streamStage.value = '分诊建议生成完成'
      },
      onError: (payload) => {
        errorMsg.value = payload?.message || '智能分诊请求失败'
      }
    })
    if (!result.value && res?.data) {
      result.value = res.data
    }
    if (!result.value) {
      throw new Error('AI 流未返回分诊结果')
    }
  } catch (e) {
    console.error('智能分诊失败', e)
    result.value = null
    errorMsg.value = errorMsg.value || e?.response?.data?.message || e?.message || '智能分诊请求失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

const reset = () => {
  triageStore.reset()
  errorMsg.value = ''
  streamText.value = ''
  streamStage.value = '准备连接'
}

const goToDept = (deptId) => {
  router.push({
    path: '/patient/appointment/doctor',
    query: { deptId }
  })
}
</script>

<style scoped>
.triage-card {
  max-width: 1160px;
  margin: 0 auto;
  padding: 24px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(248, 251, 255, .95));
}

.triage-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 20px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--border-light);
}

.triage-header__brand {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.triage-header__badge {
  display: grid;
  gap: 6px;
  min-width: 132px;
  padding: 12px 14px;
  border: 1px solid rgba(22, 119, 255, .18);
  border-radius: 8px;
  background: rgba(240, 247, 255, .82);
  text-align: right;
}

.triage-header__badge-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.triage-header__badge strong {
  font-size: 16px;
  line-height: 1.4;
  color: var(--text-primary);
}

.title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.triage-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.68fr) minmax(280px, .96fr);
  gap: 20px;
  align-items: start;
}

.triage-main-panel {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 12px 24px rgba(15, 23, 42, .04);
}

.triage-side-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
  position: sticky;
  top: 20px;
}

.input-section,
.result-section {
  display: grid;
  gap: 16px;
}

.triage-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.triage-toolbar {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(22, 119, 255, .18);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(230, 244, 255, .9), rgba(245, 250, 255, .95));
}

.triage-toolbar__copy {
  display: grid;
  gap: 6px;
}

.triage-toolbar__eyebrow {
  font-size: 12px;
  font-weight: 600;
  color: var(--primary-color);
}

.triage-toolbar__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.duration-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.duration-option {
  margin-right: 0;
  width: 100%;
}

:deep(.duration-option.el-radio.is-bordered) {
  display: flex;
  align-items: flex-start;
  width: 100%;
  height: auto;
  min-height: 72px;
  margin-right: 0;
  padding: 14px 16px;
  border-color: rgba(22, 119, 255, .18);
  border-radius: 8px;
  background: rgba(255, 255, 255, .96);
}

:deep(.duration-option.el-radio.is-bordered.is-checked) {
  border-color: var(--primary-color);
  background: rgba(230, 244, 255, .9);
}

:deep(.duration-option .el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 10px;
  white-space: normal;
}

.duration-option__label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}

.duration-option__hint {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.symptom-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #fff;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.section-heading__title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.section-heading__desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.section-heading__meta {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, .05);
  font-size: 12px;
  color: var(--text-secondary);
}

.submit-btn {
  width: 100%;
}

.triage-action {
  min-height: 44px;
  min-width: 88px;
}

.triage-stream {
  padding: 16px;
  border: 1px solid rgba(22, 119, 255, .18);
  border-radius: 8px;
  background: rgba(240, 247, 255, .8);
}

.triage-stream__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.triage-stream__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--primary-color);
  box-shadow: 0 0 0 5px rgba(22, 119, 255, .12);
  animation: triage-pulse 1.2s ease-in-out infinite;
  flex-shrink: 0;
}

.triage-stream__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}

.triage-stream__stage {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.triage-stream__preview {
  margin: 0;
  padding: 12px;
  min-height: 48px;
  border-radius: 8px;
  background: rgba(255, 255, 255, .72);
  color: var(--text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.triage-error__message {
  margin: 0 0 8px;
  color: var(--el-color-danger);
  line-height: 1.5;
}

.triage-error__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.result-header h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.result-header__desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.risk-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
}

.risk-bar.LOW { background: #f6ffed; color: #52c41a; }
.risk-bar.MEDIUM { background: #fff7e6; color: #faad14; }
.risk-bar.HIGH { background: #fff1f0; color: #ff4d4f; }
.risk-bar.CRITICAL { background: #fff1f0; color: #cf1322; }

.dept-recommend h4 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}

.dept-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) 132px auto;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  margin-bottom: 12px;
  background: rgba(255, 255, 255, .96);
}

.dept-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--primary-color);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.dept-info {
  min-width: 0;
}

.dept-name {
  margin-bottom: 4px;
  font-size: 16px;
  font-weight: 600;
}

.dept-reason {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.confidence {
  width: 132px;
  flex-shrink: 0;
}

.confidence-text {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.empty-tip {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}

.side-card {
  padding: 18px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: rgba(255, 255, 255, .96);
}

.side-card__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.side-card__value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--text-primary);
}

.side-card__value--compact {
  font-size: 20px;
}

.side-card__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.side-card__text {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.side-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.side-metrics div {
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}

.side-metrics dt {
  font-size: 12px;
  color: var(--text-secondary);
}

.side-metrics dd {
  margin: 6px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.side-list {
  display: grid;
  gap: 10px;
  margin: 12px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.side-live {
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 8px;
  background: rgba(240, 247, 255, .8);
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.tip-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: #fff7e6;
  font-size: 12px;
  line-height: 1.6;
  color: #d46b08;
}

.side-card--live {
  border-color: rgba(22, 119, 255, .18);
}

.side-card--success {
  border-color: rgba(82, 196, 26, .18);
}

.side-card--warning {
  border-color: rgba(250, 173, 20, .22);
}

@keyframes triage-pulse {
  0%, 100% {
    opacity: .72;
    transform: scale(.95);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 960px) {
  .triage-workspace {
    grid-template-columns: 1fr;
  }

  .triage-side-panel {
    position: static;
  }
}

@media (max-width: 640px) {
  .triage-card {
    max-width: 100%;
    padding: 16px;
  }

  .triage-header {
    flex-direction: column;
  }

  .triage-header__brand {
    align-items: flex-start;
  }

  .triage-header__badge {
    width: 100%;
    text-align: left;
  }

  .triage-main-panel {
    padding: 16px;
  }

  .duration-group,
  .side-metrics {
    grid-template-columns: 1fr;
  }

  .section-heading,
  .triage-error__footer,
  .result-header,
  .risk-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .dept-item {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .confidence {
    width: 100%;
  }

  .triage-error__footer .triage-action,
  .result-header .triage-action,
  .dept-item .triage-action {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .triage-stream__dot {
    animation: none;
  }
}
</style>
