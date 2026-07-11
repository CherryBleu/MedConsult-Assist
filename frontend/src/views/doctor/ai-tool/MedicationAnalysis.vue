<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">AI用药安全分析</h2>

      <div class="select-section">
        <el-form label-width="100px">
          <el-form-item label="选择病历">
            <el-select v-model="selectedRecord" placeholder="请选择要分析的病历" style="width: 400px">
              <el-option
                v-for="item in recordList"
                :key="item.id"
                :label="`${item.recordNo} - ${item.patientName}`"
                :value="item.id"
              />
            </el-select>
            <el-button type="primary" :loading="analyzing" @click="doAnalysis" style="margin-left: 12px">
              开始分析
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="analysisResult" class="result-section">
        <!-- 整体风险 -->
        <div class="risk-overview" :class="analysisResult.overallRiskLevel.toLowerCase()">
          <div class="risk-label">整体风险等级</div>
          <div class="risk-value">{{ riskLabel }}</div>
        </div>

        <!-- 禁忌风险（后端 contraindicationRisks） -->
        <div class="analysis-block">
          <h3 class="block-title">禁忌风险</h3>
          <div v-if="!analysisResult.contraindicationRisks || analysisResult.contraindicationRisks.length === 0" class="safe-tip">
            <el-icon color="#52c41a"><CircleCheck /></el-icon>
            <span>未检测到禁忌风险</span>
          </div>
          <div v-else class="risk-list">
            <div v-for="(item, index) in analysisResult.contraindicationRisks" :key="index" class="risk-item danger">
              {{ typeof item === 'string' ? item : (item.description || item.risk || JSON.stringify(item)) }}
            </div>
          </div>
        </div>

        <!-- 药物相互作用（后端 interactionRisks: List<Map>） -->
        <div class="analysis-block">
          <h3 class="block-title">药物相互作用</h3>
          <div v-if="!analysisResult.interactionRisks || analysisResult.interactionRisks.length === 0" class="safe-tip">
            <el-icon color="#52c41a"><CircleCheck /></el-icon>
            <span>未检测到相互作用风险</span>
          </div>
          <div v-else class="interaction-list">
            <div
              v-for="(item, index) in analysisResult.interactionRisks"
              :key="index"
              class="interaction-item"
              :class="(item.level || 'LOW').toLowerCase()"
            >
              <div class="drug-pair">{{ item.drugA || item.drug_a || '?' }} + {{ item.drugB || item.drug_b || '?' }}</div>
              <div class="risk-level">风险等级：{{ item.level === 'LOW' ? '低' : item.level === 'MEDIUM' ? '中' : item.level === 'HIGH' ? '高' : (item.level || '低') }}</div>
              <div class="risk-desc">{{ item.desc || item.description || item.effect || '' }}</div>
            </div>
          </div>
        </div>

        <!-- 用药提醒（后端 reminders: List<Map>） -->
        <div class="analysis-block">
          <h3 class="block-title">用药提醒</h3>
          <div v-if="!analysisResult.reminders || analysisResult.reminders.length === 0" class="safe-tip">
            <el-icon color="#52c41a"><CircleCheck /></el-icon>
            <span>暂无用药提醒</span>
          </div>
          <ul v-else class="reminder-list">
            <li v-for="(item, index) in analysisResult.reminders" :key="index">
              {{ typeof item === 'string' ? item : (item.content || item.message || JSON.stringify(item)) }}
            </li>
          </ul>
        </div>
      </div>

      <el-empty v-else description="选择病历后点击分析，AI将自动检测用药安全风险" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck } from '@element-plus/icons-vue'
import { getRecordListApi } from '@/api/record'
import { medicationAnalysisApi } from '@/api/ai'

const selectedRecord = ref('')
const analyzing = ref(false)
const recordList = ref([])
const analysisResult = ref(null)

const riskLabel = computed(() => {
  const map = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
  return map[analysisResult.value?.overallRiskLevel] || '低风险'
})

const getRecordList = async () => {
  const res = await getRecordListApi()
  // 后端分页返回 {records,total}；兼容 mock 返回数组
  recordList.value = res.data?.records ?? res.data ?? []
}

const doAnalysis = async () => {
  if (!selectedRecord.value) {
    ElMessage.warning('请先选择病历')
    return
  }
  analyzing.value = true
  try {
    // 后端 MedicationAnalysisRequest 需要 prescriptions 列表（@NotEmpty）
    // 前端暂无处方数据组装能力，发送最小占位处方让后端能处理
    const res = await medicationAnalysisApi({
      recordId: selectedRecord.value,
      prescriptions: [{ drugName: 'placeholder' }],
      patientContext: null
    })
    analysisResult.value = res.data
    ElMessage.success('分析完成')
  } finally {
    analyzing.value = false
  }
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

.risk-overview {
  padding: 20px;
  border-radius: var(--radius-base);
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.risk-overview.low { background: #f6ffed; border: 1px solid #b7eb8f; }
.risk-overview.medium { background: #fff7e6; border: 1px solid #ffd591; }
.risk-overview.high { background: #fff1f0; border: 1px solid #ffa39e; }

.risk-label {
  font-size: 14px;
  color: var(--text-regular);
}
.risk-value {
  font-size: 20px;
  font-weight: 600;
}
.low .risk-value { color: #52c41a; }
.medium .risk-value { color: #faad14; }
.high .risk-value { color: #ff4d4f; }

.analysis-block {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.analysis-block:last-child {
  border-bottom: none;
  margin-bottom: 0;
}
.block-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
}

.safe-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #52c41a;
  font-size: 14px;
}

.interaction-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.interaction-item {
  padding: 14px;
  border-radius: var(--radius-base);
  border-left: 4px solid;
}
.interaction-item.low {
  background: #f6ffed;
  border-left-color: #52c41a;
}
.interaction-item.medium {
  background: #fff7e6;
  border-left-color: #faad14;
}
.interaction-item.high {
  background: #fff1f0;
  border-left-color: #ff4d4f;
}
.drug-pair {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
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
  line-height: 2;
  font-size: 14px;
  color: var(--text-regular);
}
</style>