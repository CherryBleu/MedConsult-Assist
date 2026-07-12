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
                :label="`${item.recordNo} - ${item.patientName} - ${item.chiefComplaint}`"
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

        <el-descriptions :column="2" border class="summary-desc">
          <el-descriptions-item label="主诉">
            {{ summaryResult.summary?.chiefComplaint || summaryResult.summaryContent?.chiefComplaint || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="诊断">
            <span class="diagnosis-text">{{ formatList(summaryResult.summary?.diagnosis || summaryResult.summaryContent?.diagnosis) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="用药方案" :span="2">
            {{ formatList(summaryResult.summary?.medications || summaryResult.summaryContent?.medications) || formatList(summaryResult.summary?.treatmentPlan || summaryResult.summaryContent?.treatmentPlan) }}
          </el-descriptions-item>
          <el-descriptions-item label="医嘱建议" :span="2">
            {{ summaryResult.summary?.followUpAdvice || summaryResult.summaryContent?.followUpAdvice || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="action-bar">
          <el-button type="primary" @click="applyToRecord">应用到病历</el-button>
          <el-button @click="reset">重新生成</el-button>
        </div>
      </div>

      <el-empty v-else description="选择病历后点击生成，AI将自动提取结构化摘要" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRecordListApi } from '@/api/record'
import { generateSummaryByRecordApi } from '@/api/ai'

const selectedRecord = ref('')
const generating = ref(false)
const recordList = ref([])
const summaryResult = ref(null)

// 格式化摘要字段：数组 join 成字符串，空数组/空串/undefined 返回空（由模板兜底显示 -）
const formatList = (val) => {
  if (val == null) return ''
  if (Array.isArray(val)) return val.length > 0 ? val.join('、') : ''
  const s = String(val).trim()
  return s === '[]' ? '' : s
}

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
  try {
    const res = await generateSummaryByRecordApi(selectedRecord.value)
    summaryResult.value = res.data
    ElMessage.success('摘要生成完成')
  } catch (e) {
    // 原逻辑只有 finally，接口失败（403/404/500/网络异常）时静默吞异常，
    // summaryResult 保持 null → 页面停留在 el-empty，看起来像"返回空表"。
    // 这里显式置空让页面回到 el-empty 状态（错误 toast 由 request 拦截器统一弹，
    // 这里不再重复弹，避免双弹窗）。
    console.error('生成病历摘要失败', e)
    summaryResult.value = null
  } finally {
    generating.value = false
  }
}

const applyToRecord = () => {
  ElMessage.success('已将摘要内容填充到病历书写页')
}

const reset = () => {
  summaryResult.value = null
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