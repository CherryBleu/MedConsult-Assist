<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">AI病历摘要生成</h2>
      
      <div class="select-section">
        <el-form class="summary-form" label-width="100px">
          <el-form-item label="选择病历">
            <el-select
              v-model="selectedRecord"
              class="record-select"
              placeholder="请选择要生成摘要的病历"
              filterable
            >
              <el-option
                v-for="item in recordList"
                :key="item.id"
                :label="`${item.recordNo} | ${item.patientName || '未知患者'}`"
                :value="item.recordNo"
              >
                <div class="record-option">
                  <div class="option-main">
                    <span class="option-no">{{ item.recordNo }}</span>
                    <span class="option-patient">{{ item.patientName || '未知患者' }}</span>
                    <el-tag v-if="item.deptName" size="small" type="info">{{ item.deptName }}</el-tag>
                    <el-tag :type="getStatusType(MEDICAL_RECORD_STATUS, item.status)" size="small">
                      {{ getStatusLabel(MEDICAL_RECORD_STATUS, item.status) }}
                    </el-tag>
                  </div>
                  <div class="option-complaint">{{ item.chiefComplaint || '无主诉' }}</div>
                </div>
              </el-option>
            </el-select>
            <p v-if="selectedRecordItem" class="selected-record-hint" data-testid="selected-record-complaint">
              {{ selectedRecordItem.chiefComplaint || '无主诉' }}
            </p>
            <el-button
              type="primary"
              class="record-summary-action generate-action"
              :loading="generating"
              :disabled="generating"
              @click="generateSummary"
            >
              生成摘要
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div
        v-if="generating"
        class="stream-section"
        role="status"
        aria-live="polite"
      >
        <div class="stream-header">
          <span class="stream-dot" aria-hidden="true"></span>
          <div>
            <div class="stream-title">正在生成摘要</div>
            <div class="stream-subtitle">AI 正在分析病历内容，请稍候...</div>
          </div>
        </div>
      </div>

      <div v-if="summaryResult" class="result-section">
        <div class="result-header">
          <h3>生成结果</h3>
          <el-tag type="info">{{ summaryResult.status || '已完成' }}</el-tag>
        </div>

        <el-alert
          v-if="isSummaryEmpty"
          type="warning"
          :closable="false"
          show-icon
          title="该病历的摘要字段均为空"
          description="病历内容可能不完整（草稿状态或缺主诉/诊断），AI 无法提取有效摘要。建议补全病历后再生成。"
          style="margin-bottom: 16px"
        />

        <el-descriptions :column="2" border class="summary-desc">
          <el-descriptions-item label="主诉">
            {{ summaryResult.summary?.chiefComplaint || summaryResult.summaryContent?.chiefComplaint || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="诊断">
            <span class="diagnosis-text">{{ formatList(summaryResult.summary?.diagnosis || summaryResult.summaryContent?.diagnosis) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="用药方案" :span="2">
            <span v-if="formatList(summaryResult.summary?.medications ?? summaryResult.summaryContent?.medications) || formatList(summaryResult.summary?.treatmentPlan ?? summaryResult.summaryContent?.treatmentPlan)">
              {{ formatList(summaryResult.summary?.medications ?? summaryResult.summaryContent?.medications) || formatList(summaryResult.summary?.treatmentPlan ?? summaryResult.summaryContent?.treatmentPlan) }}
            </span>
            <span v-else class="empty-hint">病历未包含用药/治疗方案信息</span>
          </el-descriptions-item>
          <el-descriptions-item label="医嘱建议" :span="2">
            <span v-if="formatList(summaryResult.summary?.followUpAdvice ?? summaryResult.summaryContent?.followUpAdvice)">
              {{ formatList(summaryResult.summary?.followUpAdvice ?? summaryResult.summaryContent?.followUpAdvice) }}
            </span>
            <span v-else class="empty-hint">病历未包含医嘱建议</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="action-bar">
          <el-button type="primary" class="record-summary-action" @click="applyToRecord">应用到病历</el-button>
          <el-button class="record-summary-action" @click="reset">重新生成</el-button>
        </div>
      </div>

      <el-alert
        v-else-if="errorMsg"
        class="summary-error"
        role="alert"
        type="error"
        :closable="false"
        show-icon
        title="病历摘要生成失败"
      >
        <p class="summary-error__message">{{ errorMsg }}</p>
        <div class="summary-error__footer">
          <span>请检查病历是否存在且内容完整，或稍后重试。</span>
          <el-button type="primary" size="small" class="record-summary-action" @click="generateSummary">重试</el-button>
        </div>
      </el-alert>

      <el-empty v-else description="选择病历后点击生成，AI将自动提取结构化摘要" />
    </div>
  </div>
</template>

<script setup>
// keep-alive include 按组件 name 匹配，必须显式声明 name（与路由 name 一致）
defineOptions({ name: 'RecordSummary' })

import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getRecordListApi } from '@/api/record'
import { generateSummaryByRecordApi } from '@/api/ai'

const selectedRecord = ref('')
const generating = ref(false)
const recordList = ref([])
const summaryResult = ref(null)
// 接口失败时的错误信息（非空则显示错误提示而非空 el-empty，让用户知道是失败了而非"返回空"）
const errorMsg = ref('')

// 格式化摘要字段：数组 join 成字符串，空数组/空串/undefined 返回空（由模板兜底显示 -）
const formatList = (val) => {
  if (val == null) return ''
  if (Array.isArray(val)) return val.length > 0 ? val.join('、') : ''
  const s = String(val).trim()
  return s === '[]' ? '' : s
}

// 摘要关键字段是否全空（成功返回但无有效内容时给出提示，而非显示一堆 '-' 像空表）
const isSummaryEmpty = computed(() => {
  if (!summaryResult.value) return false
  const s = summaryResult.value.summary || summaryResult.value.summaryContent || {}
  return !formatList(s.chiefComplaint) && !formatList(s.diagnosis)
    && !formatList(s.medications) && !formatList(s.treatmentPlan)
    && !formatList(s.followUpAdvice)
})

// 当前选中的病历对象，用于在选择器下方展示完整主诉（避免 select 触发器截断长文本）
const selectedRecordItem = computed(() => {
  return recordList.value.find(item => item.recordNo === selectedRecord.value) || null
})

const getRecordList = async () => {
  const res = await getRecordListApi()
  // 后端分页返回 {records,total}；兼容 mock 返回数组
  recordList.value = res.data?.records ?? res.data ?? []
}

const generateSummary = async () => {
  if (!selectedRecord.value) {
    ElMessage.warning('请先选择病历')
    return
  }
  generating.value = true
  errorMsg.value = ''
  summaryResult.value = null
  try {
    // 用一次性 POST /ai/summary/by-record/{recordNo} 接口，避免 LLM 流式结构化输出
    // 带来的逐 token 渲染体验。后端返回完整 MedicalRecordSummaryResponse。
    const res = await generateSummaryByRecordApi(selectedRecord.value)
    if (!res?.data) {
      throw new Error('AI 未返回摘要结果')
    }
    summaryResult.value = res.data
    ElMessage.success('摘要生成完成')
  } catch (e) {
    // 接口失败（403/404/500/网络异常/LLM 不可用）时不再静默吞异常回到 el-empty
    // （那会让用户误以为"返回空表"）。改为显示明确错误提示 + 重试按钮。
    // request 拦截器已弹 toast，这里取后端 message 显示在页面内（持久可见）。
    console.error('生成病历摘要失败', e)
    summaryResult.value = null
    errorMsg.value = e?.response?.data?.message || e?.message || '病历摘要生成失败'
  } finally {
    generating.value = false
  }
}

const applyToRecord = () => {
  ElMessage.success('已将摘要内容填充到病历书写页')
}

const reset = () => {
  summaryResult.value = null
  errorMsg.value = ''
  selectedRecord.value = ''
}

onMounted(() => {
  getRecordList()
})
</script>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.select-section {
  padding: 20px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
  margin-bottom: 24px;
}

.summary-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.summary-form :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.record-select {
  /* 桌面端允许更宽，避免长主诉在 select 触发器中被截断；
     移动端 flex: 1 让其占满列宽。 */
  min-width: 320px;
  max-width: 800px;
  flex: 1 1 480px;
}

.selected-record-hint {
  margin: 6px 0 0;
  padding: 6px 10px;
  border-left: 3px solid var(--primary-color, #0284c7);
  background: rgba(2, 132, 199, 0.06);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
  flex-basis: 100%;
}

.record-summary-action {
  min-height: 44px;
  min-width: 88px;
}

.generate-action {
  flex: 0 0 auto;
}

.stream-section {
  margin-bottom: 24px;
  padding: 16px;
  border: 1px solid rgba(2, 132, 199, .18);
  border-radius: 8px;
  background: rgba(240, 249, 255, .78);
}

.stream-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.stream-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--primary-color);
  box-shadow: 0 0 0 5px rgba(2, 132, 199, .12);
  animation: stream-pulse 1.2s ease-in-out infinite;
}

.stream-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}

.stream-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.stream-preview {
  margin: 0;
  padding: 12px;
  min-height: 48px;
  border-radius: 8px;
  background: rgba(255, 255, 255, .72);
  color: var(--text-primary);
  line-height: 1.6;
  word-break: break-word;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.result-header h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.summary-desc {
  margin-bottom: 20px;
}

.empty-hint {
  color: var(--el-text-color-placeholder, #a8abb2);
  font-style: italic;
  font-size: 13px;
}
.diagnosis-text {
  color: var(--primary-color);
  font-weight: 500;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.action-bar :deep(.el-button + .el-button) {
  margin-left: 0;
}

.summary-error {
  margin-bottom: 16px;
}

.summary-error__message {
  margin: 0 0 8px;
  color: var(--el-color-danger);
  line-height: 1.5;
}

.summary-error__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 8px;
}

/* 病历下拉选项 */
.record-option {
  line-height: 1.4;
}
.option-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.option-no {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: monospace;
}
.option-patient {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.option-complaint {
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes stream-pulse {
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
  .select-section {
    padding: 16px;
  }

  .summary-form :deep(.el-form-item) {
    display: block;
  }

  .summary-form :deep(.el-form-item__label) {
    display: block;
    width: auto !important;
    margin-bottom: 8px;
    line-height: 1.4;
    text-align: left;
  }

  .summary-form :deep(.el-form-item__content) {
    flex-direction: column;
    align-items: stretch;
    margin-left: 0 !important;
  }

  .record-select,
  .generate-action {
    width: 100%;
    flex-basis: auto;
  }

  .stream-section {
    padding: 14px;
  }

  .result-header,
  .action-bar,
  .summary-error__footer {
    align-items: stretch;
    flex-direction: column;
  }

  .action-bar .record-summary-action,
  .summary-error__footer .record-summary-action {
    width: 100%;
  }

  .option-main {
    flex-wrap: wrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  .stream-dot {
    animation: none;
  }
}
</style>
