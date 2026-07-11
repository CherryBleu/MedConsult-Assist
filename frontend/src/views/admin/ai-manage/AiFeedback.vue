<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">AI反馈管理</h2>
        <el-tabs v-model="activeTab" style="margin: 0">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="待处理" name="pending" />
          <el-tab-pane label="已处理" name="processed" />
        </el-tabs>
      </div>

      <el-table :data="filteredList" v-loading="loading" border stripe>
        <el-table-column prop="feedbackNo" label="反馈编号" width="160" />
        <el-table-column prop="serviceType" label="服务类型" width="120" />
        <el-table-column prop="userName" label="反馈用户" width="120" />
        <el-table-column label="评分" width="120">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled show-score />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="反馈内容" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'" size="small">
              {{ row.status === 'PENDING' ? '待处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openProcessDialog(row)" v-if="row.status === 'PENDING'">
              处理
            </el-button>
            <el-button size="small" link @click="viewDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 处理弹窗 -->
    <el-dialog v-model="dialogVisible" title="处理反馈" width="500px">
      <div class="feedback-content">
        <div class="label">反馈内容</div>
        <div class="text">{{ currentFeedback?.content }}</div>
      </div>
      <el-form label-width="80px">
        <el-form-item label="处理回复">
          <el-input v-model="replyContent" type="textarea" :rows="4" placeholder="请输入处理回复" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitProcess">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiFeedbackApi, processFeedbackApi } from '@/api/ai-manage'

const loading = ref(false)
const activeTab = ref('all')
const feedbackList = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const currentFeedback = ref(null)
const replyContent = ref('')

const filteredList = computed(() => {
  if (activeTab.value === 'all') return feedbackList.value
  if (activeTab.value === 'pending') return feedbackList.value.filter(i => i.status === 'PENDING')
  if (activeTab.value === 'processed') return feedbackList.value.filter(i => i.status === 'PROCESSED')
  return feedbackList.value
})

const getFeedbackList = async () => {
  loading.value = true
  try {
    const res = await getAiFeedbackApi()
    feedbackList.value = res.data
  } finally {
    loading.value = false
  }
}

const openProcessDialog = (row) => {
  currentFeedback.value = row
  replyContent.value = ''
  dialogVisible.value = true
}

const viewDetail = (row) => {
  currentFeedback.value = row
  replyContent.value = row.reply || ''
  dialogVisible.value = true
}

const submitProcess = async () => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入处理回复')
    return
  }
  submitting.value = true
  try {
    await processFeedbackApi(currentFeedback.value.id, replyContent.value)
    ElMessage.success('处理成功')
    dialogVisible.value = false
    getFeedbackList()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  getFeedbackList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.feedback-content {
  padding: 12px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
  margin-bottom: 16px;
}
.feedback-content .label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.feedback-content .text {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
}
</style>