<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">AI病历摘要生成</h2>
      
      <div class="select-section">
        <el-form label-width="100px">
          <el-form-item label="选择病历">
            <el-select v-model="selectedRecord" placeholder="请选择要生成摘要的病历" style="width: 400px">
              <el-option
                v-for="item in recordList"
                :key="item.id"
                :label="`${item.recordNo} - ${item.patientName || '未知患者'} - ${item.chiefComplaint || '无主诉'}`"
                :value="item.recordNo"
              />
            </el-select>
            <el-button type="primary" :loading="generating" @click="generateSummary" style="margin-left: 12px">
              生成摘要
            </el-button>
          </el-form-item>
        </el-form>
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
          <el-button type="primary" @click="applyToRecord">应用到病历</el-button>
          <el-button @click="reset">重新生成</el-button>
        </div>
      </div>

      <el-alert
        v-else-if="errorMsg"
        type="error"
        :closable="false"
        show-icon
        :title="errorMsg"
        description="请检查病历是否存在且内容完整，或稍后重试。如持续失败请联系管理员检查 AI 服务配置。"
      >
        <el-button type="primary" size="small" @click="generateSummary" style="margin-top: 8px">重试</el-button>
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
  try {
    const res = await generateSummaryByRecordApi(selectedRecord.value)
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
</style>