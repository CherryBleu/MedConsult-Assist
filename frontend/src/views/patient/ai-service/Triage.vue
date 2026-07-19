<template>
  <div class="page-container">
    <div class="card-box triage-card">
      <div class="triage-header">
        <el-icon :size="32" color="#1677ff"><Cpu /></el-icon>
        <div>
          <h2 class="title">智能分诊</h2>
          <p class="desc">输入您的症状，AI为您推荐就诊科室</p>
        </div>
      </div>

      <div v-if="!result" class="input-section">
        <el-form label-width="0">
          <el-form-item label="">
            <el-input
              v-model="symptoms"
              type="textarea"
              :rows="4"
              :disabled="submitting"
              placeholder="请描述您的症状，例如：咳嗽、咳痰、发热3天..."
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="症状持续时间">
            <el-radio-group v-model="duration">
              <el-radio value="1天以内">1天以内</el-radio>
              <el-radio value="1-3天">1-3天</el-radio>
              <el-radio value="3-7天">3-7天</el-radio>
              <el-radio value="7天以上">7天以上</el-radio>
            </el-radio-group>
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

        <div class="tip-box">
          <el-icon><InfoFilled /></el-icon>
          <span>AI分诊结果仅供参考，不能替代医生诊断，急重症请立即就医。</span>
        </div>
      </div>

      <!-- 分诊结果 -->
      <div v-else class="result-section" data-testid="triage-result">
        <div class="result-header">
          <h3>分诊结果</h3>
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
    </div>
  </div>
</template>

<script setup>
// keep-alive include 按组件 name 匹配，必须显式声明 name（与路由 name 一致）
defineOptions({ name: 'Triage' })

import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cpu, InfoFilled } from '@element-plus/icons-vue'
import { useTriageStore } from '@/store/modules/triage'
import { triageStreamApi } from '@/api/ai'

const router = useRouter()
const triageStore = useTriageStore()

// 症状/持续时长/请求态/结果全部上提到 Pinia store：组件卸载（路由切走）后状态不丢，
// 切回来时直接读 store 即可恢复"正在加载"或"已有分诊结果"。请求的 Promise
// 也由 store action 持有，组件销毁不影响请求继续。
// storeToRefs 返回可写 ref，v-model 双向绑定直接落到 store 状态上。
const { symptoms, duration, submitting, result } = storeToRefs(triageStore)
const streamText = ref('')
const streamStage = ref('准备连接')
const errorMsg = ref('')

// 后端 TriageResponse 无 riskLevel 字段，根据 emergencyRecommended + 最高置信度推导
const riskLevel = computed(() => {
  if (!result.value) return 'LOW'
  if (result.value.emergencyRecommended) return 'HIGH'
  const maxConf = Math.max(...(result.value.recommendations || []).map(r => r.confidence || 0))
  if (maxConf >= 0.8) return 'LOW'
  if (maxConf >= 0.5) return 'MEDIUM'
  return 'MEDIUM'
})

const riskLabel = computed(() => {
  const map = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '危重' }
  return map[riskLevel.value] || '低风险'
})

const splitSymptoms = () => String(symptoms.value || '')
  .split(/[,，、\n]/)
  .map(s => s.trim())
  .filter(Boolean)

const getStreamText = (payload) => {
  if (payload == null) return ''
  if (typeof payload === 'string') return payload
  if (payload.departmentName) {
    const confidence = Math.round((payload.confidence || 0) * 100)
    return `${payload.departmentName}：${payload.reason || '正在评估匹配度'}（置信度 ${confidence}%）`
  }
  return payload.token == null ? '' : String(payload.token)
}

// 触发分诊：页面消费 SSE 事件，同时继续把输入、请求态和结果写入既有 Pinia 状态。
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
  max-width: 700px;
  margin: 0 auto;
  overflow: hidden;
}

.triage-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}
.desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.submit-btn {
  width: 100%;
}

.triage-action {
  min-height: 44px;
  min-width: 88px;
}

.triage-stream {
  margin-top: 20px;
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

.triage-error {
  margin-top: 20px;
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

.tip-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: #fff7e6;
  border-radius: var(--radius-base);
  font-size: 12px;
  color: #d46b08;
  margin-top: 20px;
}

/* 结果样式 */
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.result-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.risk-bar {
  padding: 12px 16px;
  border-radius: var(--radius-base);
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.risk-bar.LOW { background: #f6ffed; color: #52c41a; }
.risk-bar.MEDIUM { background: #fff7e6; color: #faad14; }
.risk-bar.HIGH { background: #fff1f0; color: #ff4d4f; }
.risk-bar.CRITICAL { background: #fff1f0; color: #cf1322; }

.dept-recommend h4 {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
}
.dept-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  margin-bottom: 12px;
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
  flex: 1;
  min-width: 0;
}
.dept-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}
.dept-reason {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.confidence {
  width: 120px;
  flex-shrink: 0;
}
.confidence-text {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.empty-tip {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
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

@media (max-width: 640px) {
  .triage-card {
    max-width: 100%;
  }

  .triage-header {
    align-items: flex-start;
  }

  .triage-stream,
  .triage-error {
    margin-top: 16px;
  }

  .triage-error__footer,
  .result-header,
  .risk-bar,
  .dept-item {
    align-items: stretch;
    flex-direction: column;
  }

  .triage-error__footer .triage-action,
  .result-header .triage-action,
  .dept-item .triage-action {
    width: 100%;
  }

  .risk-bar {
    gap: 8px;
  }

  .dept-item {
    gap: 12px;
  }

  .dept-info {
    width: 100%;
  }

  .confidence {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .triage-stream__dot {
    animation: none;
  }
}
</style>
