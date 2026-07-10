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
                :value="item.id"
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
          <el-tag type="info">{{ summaryResult.modelName }}</el-tag>
        </div>

        <el-descriptions :column="2" border class="summary-desc">
          <el-descriptions-item label="主诉">
            {{ summaryResult.summaryContent.chiefComplaint }}
          </el-descriptions-item>
          <el-descriptions-item label="诊断">
            <span class="diagnosis-text">{{ summaryResult.summaryContent.diagnosis }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="用药方案" :span="2">
            {{ summaryResult.summaryContent.medication }}
          </el-descriptions-item>
          <el-descriptions-item label="医嘱建议" :span="2">
            {{ summaryResult.summaryContent.advice }}
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

const getRecordList = async () => {
  const res = await getRecordListApi()
  recordList.value = res.data
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