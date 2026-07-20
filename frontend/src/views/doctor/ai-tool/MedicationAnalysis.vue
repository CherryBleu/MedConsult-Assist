<template>
  <div class="page-container">
    <div class="card-box medication-page">
      <div class="page-header">
        <div>
          <h2 class="page-title">AI用药安全分析</h2>
          <p class="page-subtitle">按真实药品明细校验禁忌、相互作用和用药提醒。</p>
        </div>
      </div>

      <div class="medication-workflow" data-testid="medication-analysis-workflow">
        <section class="select-section" aria-labelledby="medication-input-title">
          <div class="panel-header">
            <h3 id="medication-input-title">分析条件</h3>
            <span>实时校验</span>
          </div>

          <el-form class="medication-form" label-position="top">
            <div class="form-grid">
              <el-form-item label="选择病历">
                <el-select
                  v-model="selectedRecord"
                  class="field-control medication-record-select"
                  placeholder="请选择要分析的病历"
                  filterable
                  :loading="recordLoading"
                  aria-label="选择病历"
                >
                  <el-option
                    v-for="item in recordList"
                    :key="item.id"
                    :label="`${item.recordNo} | ${item.patientName || '未知患者'}`"
                    :value="item.id"
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
              </el-form-item>

              <el-form-item label="选择药品">
                <el-select
                  v-model="selectedDrugIds"
                  class="field-control medication-drug-select"
                  placeholder="请选择本次分析涉及的真实药品"
                  filterable
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  :loading="drugLoading"
                  aria-label="选择药品"
                >
                  <el-option
                    v-for="item in drugOptions"
                    :key="item.drugId"
                    :label="`${item.drugName || item.name} | ${item.specification || '无规格'}`"
                    :value="item.drugId"
                  />
                </el-select>
              </el-form-item>
            </div>

            <el-alert
              v-if="loadError"
              class="load-error"
              role="alert"
              type="error"
              :closable="false"
              show-icon
              title="基础数据加载失败"
            >
              <div class="load-error__content">
                <span>{{ loadError }}</span>
                <el-button size="small" type="primary" plain class="medication-action" @click="reloadReferenceData">
                  重试
                </el-button>
              </div>
            </el-alert>

            <div v-if="formError" class="form-error" role="alert">{{ formError }}</div>

            <div class="form-footer">
              <div class="form-helper" role="status" aria-live="polite">
                已选择 {{ selectedPrescriptions.length }} 个药品；缺少真实药品时不会提交占位分析。
              </div>
              <el-button
                type="primary"
                class="medication-action medication-analysis-action"
                :loading="analyzing"
                :disabled="!canAnalyze"
                @click="doAnalysis"
              >
                开始分析
              </el-button>
            </div>
          </el-form>
        </section>

        <section class="result-panel" aria-labelledby="medication-result-title">
          <div class="result-header" data-testid="medication-analysis-result-header">
            <h3 id="medication-result-title">分析结果</h3>
            <span class="result-meta">药品 {{ selectedPrescriptions.length }} 项</span>
          </div>

          <section
            v-if="analyzing"
            class="stream-section"
            role="status"
            aria-live="polite"
          >
            <div class="stream-header">
              <span class="stream-dot" aria-hidden="true"></span>
              <div class="stream-copy">
                <div class="stream-title">正在生成用药分析</div>
                <div class="stream-stage">AI 正在分析用药风险，请稍候...</div>
              </div>
            </div>
          </section>

          <PageState
            :loading="analyzing"
            :error="analysisError"
            :empty="!analysisResult"
            loading-text="正在分析用药风险..."
            error-title="用药分析失败"
            empty-text="选择病历和药品后开始分析"
            @retry="doAnalysis"
          >
            <section
              v-if="analysisResult"
              class="result-section"
              data-testid="medication-analysis-result"
              aria-label="用药分析结果明细"
            >
              <!-- 整体风险 -->
              <div
                class="risk-overview"
                :class="riskClass"
                role="status"
                aria-live="polite"
              >
                <div>
                  <div class="risk-label">整体风险等级</div>
                  <div class="risk-value">{{ riskLabel }}</div>
                </div>
                <div class="risk-stats" aria-label="风险统计">
                  <span>禁忌 {{ contraindicationCount }}</span>
                  <span>相互作用 {{ interactionCount }}</span>
                  <span>提醒 {{ reminderCount }}</span>
                </div>
              </div>

              <!-- 禁忌风险（后端 contraindicationRisks） -->
              <div class="analysis-block">
                <h4 class="block-title">禁忌风险</h4>
                <div v-if="contraindicationCount === 0" class="safe-tip" role="status">
                  <el-icon><CircleCheck /></el-icon>
                  <span>未检测到禁忌风险</span>
                </div>
                <div v-else class="risk-list">
                  <div v-for="(item, index) in analysisResult.contraindicationRisks" :key="index" class="risk-item danger">
                    {{ typeof item === 'string' ? item : (item.description || item.risk || '—') }}
                  </div>
                </div>
              </div>

              <!-- 药物相互作用（后端 interactionRisks: List<Map>） -->
              <div class="analysis-block">
                <h4 class="block-title">药物相互作用</h4>
                <div v-if="interactionCount === 0" class="safe-tip" role="status">
                  <el-icon><CircleCheck /></el-icon>
                  <span>未检测到相互作用风险</span>
                </div>
                <div v-else class="interaction-list">
                  <div
                    v-for="(item, index) in analysisResult.interactionRisks"
                    :key="index"
                    class="interaction-item"
                    :class="normalizeRiskLevel(item.level).toLowerCase()"
                  >
                    <div class="drug-pair">{{ item.drugA || item.drug_a || '?' }} + {{ item.drugB || item.drug_b || '?' }}</div>
                    <div class="risk-level">风险等级：{{ getLevelLabel(item.level) }}</div>
                    <div class="risk-desc">{{ item.desc || item.description || item.effect || '' }}</div>
                  </div>
                </div>
              </div>

              <!-- 用药提醒（后端 reminders: List<Map>） -->
              <div class="analysis-block">
                <h4 class="block-title">用药提醒</h4>
                <div v-if="reminderCount === 0" class="safe-tip" role="status">
                  <el-icon><CircleCheck /></el-icon>
                  <span>暂无用药提醒</span>
                </div>
                <ul v-else class="reminder-list">
                  <li v-for="(item, index) in analysisResult.reminders" :key="index">
                    {{ typeof item === 'string' ? item : ((item.drugName ? item.drugName + '：' : '') + (item.reminder || item.content || item.message || '—')) }}
                  </li>
                </ul>
              </div>
            </section>
          </PageState>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
// keep-alive include 按组件 name 匹配，必须显式声明 name（与路由 name 一致）
defineOptions({ name: 'MedicationAnalysis' })

import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck } from '@element-plus/icons-vue'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getRecordListApi } from '@/api/record'
import { getDrugListApi } from '@/api/drug'
import { medicationAnalysisApi } from '@/api/ai'
import PageState from '@/components/common/PageState.vue'

const selectedRecord = ref('')
const selectedDrugIds = ref([])
const analyzing = ref(false)
const recordLoading = ref(false)
const drugLoading = ref(false)
const recordList = ref([])
const drugOptions = ref([])
const analysisResult = ref(null)
const formError = ref('')
const loadError = ref('')
const analysisError = ref('')

const riskLabel = computed(() => {
  const map = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
  return map[riskClass.value.toUpperCase()] || '低风险'
})

const riskClass = computed(() => normalizeRiskLevel(analysisResult.value?.overallRiskLevel).toLowerCase())
const contraindicationCount = computed(() => analysisResult.value?.contraindicationRisks?.length || 0)
const interactionCount = computed(() => analysisResult.value?.interactionRisks?.length || 0)
const reminderCount = computed(() => analysisResult.value?.reminders?.length || 0)
const canAnalyze = computed(() => {
  return Boolean(selectedRecord.value && selectedPrescriptions.value.length > 0 && !loadError.value && !analyzing.value)
})

const normalizeRiskLevel = (level) => {
  const normalized = String(level || 'LOW').toUpperCase()
  return ['LOW', 'MEDIUM', 'HIGH'].includes(normalized) ? normalized : 'LOW'
}

const getLevelLabel = (level) => {
  const map = { LOW: '低', MEDIUM: '中', HIGH: '高' }
  return map[normalizeRiskLevel(level)] || '低'
}

const getRecordList = async () => {
  recordLoading.value = true
  try {
    const res = await getRecordListApi()
    // 后端分页返回 {records,total}；兼容 mock 返回数组
    recordList.value = res.data?.records ?? res.data ?? []
  } finally {
    recordLoading.value = false
  }
}

const getDrugOptions = async () => {
  drugLoading.value = true
  try {
    const res = await getDrugListApi({ page: 1, pageSize: 100 })
    const list = res.data?.records ?? res.data?.items ?? (Array.isArray(res.data) ? res.data : [])
    drugOptions.value = list.map(item => ({
      ...item,
      drugId: item.drugId ?? item.drugNo ?? item.id,
      drugName: item.drugName ?? item.name ?? item.genericName
    }))
  } finally {
    drugLoading.value = false
  }
}

const selectedRecordItem = computed(() => {
  return recordList.value.find(item => item.id === selectedRecord.value)
})

const selectedPrescriptions = computed(() => {
  return selectedDrugIds.value
    .map(id => drugOptions.value.find(item => item.drugId === id))
    .filter(Boolean)
    .map(item => ({
      drugId: String(item.drugId || ''),
      drugName: item.drugName || item.name || item.genericName,
      dosage: '',
      frequency: '',
      route: '',
      days: 1
    }))
    .filter(item => item.drugName)
})

const doAnalysis = async () => {
  formError.value = ''
  analysisError.value = ''
  if (!selectedRecord.value) {
    formError.value = '请先选择病历'
    ElMessage.warning(formError.value)
    return
  }
  if (selectedPrescriptions.value.length === 0) {
    formError.value = '请至少选择 1 个真实药品后再开始分析'
    ElMessage.warning(formError.value)
    return
  }
  analyzing.value = true
  try {
    // 用一次性 POST /ai/medication-analysis 接口，避免 LLM 流式结构化输出
    // 带来的逐 token 渲染体验。后端返回完整 MedicationAnalysisResponse。
    const res = await medicationAnalysisApi({
      recordId: selectedRecord.value,
      patientId: selectedRecordItem.value?.patientId ? String(selectedRecordItem.value.patientId) : undefined,
      prescriptions: selectedPrescriptions.value,
      patientContext: null
    })
    if (!res?.data) {
      throw new Error('AI 未返回用药分析结果')
    }
    analysisResult.value = res.data
    ElMessage.success('分析完成')
  } catch (e) {
    analysisResult.value = null
    analysisError.value = e?.response?.data?.message || e?.message || '用药分析请求失败，请稍后重试'
    ElMessage.error(analysisError.value)
  } finally {
    analyzing.value = false
  }
}

const reloadReferenceData = async () => {
  loadError.value = ''
  formError.value = ''
  try {
    await Promise.all([getRecordList(), getDrugOptions()])
  } catch (e) {
    loadError.value = e?.response?.data?.message || e?.message || '病历或药品数据加载失败，请重试'
  }
}

onMounted(() => {
  reloadReferenceData()
})
</script>

<style scoped>
.medication-page {
  max-width: var(--content-max);
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: var(--font-sm);
  line-height: 1.5;
}

.medication-workflow {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.select-section {
  min-width: 0;
  padding-right: 24px;
  border-right: 1px solid var(--border-lighter);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header h3,
.result-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
}

.panel-header span {
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: rgba(22, 163, 74, .1);
  color: #15803d;
  font-size: var(--font-xs);
  font-weight: 600;
  line-height: 1.4;
}

.medication-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.medication-form :deep(.el-form-item__label) {
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.4;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.field-control {
  width: 100%;
}

.field-control :deep(.el-select__wrapper) {
  min-height: var(--touch-target);
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
}

.form-error {
  margin: 14px 0 0;
  color: var(--el-color-danger);
  font-size: var(--font-sm);
  line-height: 1.5;
}

.load-error {
  margin-top: 16px;
}

.load-error__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  line-height: 1.5;
}

.form-footer {
  display: flex;
  align-items: stretch;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(2, 132, 199, .1);
}

.form-helper {
  color: var(--text-secondary);
  font-size: var(--font-sm);
  line-height: 1.6;
}

.medication-action {
  min-height: var(--touch-target);
  min-width: 96px;
  touch-action: manipulation;
  transition: background-color var(--motion-base) ease, border-color var(--motion-base) ease, box-shadow var(--motion-base) ease;
}
.medication-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
  box-shadow: var(--focus-ring);
}

.result-panel {
  min-width: 0;
}

.result-section {
  display: grid;
  gap: 14px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.result-meta {
  color: var(--text-secondary);
  font-size: var(--font-sm);
}

.stream-section {
  display: grid;
  gap: 12px;
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid rgba(2, 132, 199, .18);
  border-radius: var(--radius-base);
  background: rgba(240, 249, 255, .78);
}

.stream-header {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.stream-dot {
  flex: 0 0 auto;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--primary-color);
  box-shadow: 0 0 0 6px rgba(2, 132, 199, .12);
  animation: stream-pulse 1.2s ease-in-out infinite;
}

.stream-copy {
  min-width: 0;
}

.stream-title {
  color: var(--text-primary);
  font-size: var(--font-base);
  font-weight: 700;
  line-height: 1.4;
}

.stream-stage,
.stream-preview {
  color: var(--text-secondary);
  font-size: var(--font-sm);
  line-height: 1.6;
}

.stream-preview {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.risk-overview {
  padding: 16px;
  border-radius: var(--radius-base);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  border: 1px solid;
}
.risk-overview.low { background: #f0fdf4; border-color: #86efac; }
.risk-overview.medium { background: #fffbeb; border-color: #fcd34d; }
.risk-overview.high { background: #fef2f2; border-color: #fca5a5; }

.risk-label {
  font-size: 14px;
  color: var(--text-regular);
}
.risk-value {
  font-size: 20px;
  font-weight: 600;
  margin-top: 4px;
}
.low .risk-value { color: #15803d; }
.medium .risk-value { color: #92400e; }
.high .risk-value { color: #b91c1c; }

.risk-stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.risk-stats span {
  min-height: 32px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, .7);
  color: var(--text-primary);
  font-size: var(--font-sm);
  line-height: 20px;
}

.analysis-block {
  padding: 14px 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, .64);
  transition: border-color var(--motion-base) ease, box-shadow var(--motion-base) ease;
}

.block-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--text-primary);
}

.safe-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #15803d;
  font-size: 14px;
}

.interaction-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.risk-list {
  display: grid;
  gap: 10px;
}
.risk-item {
  padding: 12px;
  border-radius: var(--radius-sm);
  line-height: 1.6;
}
.risk-item.danger {
  color: #991b1b;
  background: #fef2f2;
  border: 1px solid #fecaca;
}
.interaction-item {
  padding: 14px;
  border-radius: var(--radius-base);
  border: 1px solid transparent;
  border-left: 4px solid;
}
.interaction-item.low {
  background: #f0fdf4;
  border-color: #bbf7d0;
  border-left-color: #16a34a;
}
.interaction-item.medium {
  background: #fffbeb;
  border-color: #fde68a;
  border-left-color: #d97706;
}
.interaction-item.high {
  background: #fef2f2;
  border-color: #fecaca;
  border-left-color: #dc2626;
}
.drug-pair {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--text-primary);
}
.risk-level {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.risk-desc {
  font-size: 13px;
  color: var(--text-regular);
}

.reminder-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
  font-size: 14px;
  color: var(--text-regular);
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

@media (max-width: 960px) {
  .medication-workflow {
    grid-template-columns: 1fr;
  }

  .select-section {
    padding-right: 0;
    padding-bottom: 18px;
    border-right: 0;
    border-bottom: 1px solid var(--border-lighter);
  }
}

@media (max-width: 768px) {
  .page-header,
  .risk-overview,
  .result-header {
    align-items: stretch;
    flex-direction: column;
  }

  .medication-action {
    width: 100%;
  }

  .risk-stats {
    justify-content: flex-start;
  }

  .option-main {
    flex-wrap: wrap;
  }
}

@media (max-width: 480px) {
  .analysis-block,
  .risk-overview {
    padding: 14px;
  }

  .risk-stats {
    display: grid;
    grid-template-columns: 1fr;
  }
}

@keyframes stream-pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }

  50% {
    opacity: .72;
    transform: scale(.86);
  }
}

@media (prefers-reduced-motion: reduce) {
  .stream-dot {
    animation: none;
  }
}
</style>
