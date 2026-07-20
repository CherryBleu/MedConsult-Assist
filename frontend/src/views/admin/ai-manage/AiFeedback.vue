<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <div>
          <p class="eyebrow">Feedback Review</p>
          <h2 class="page-title">AI反馈管理</h2>
        </div>
        <el-tabs v-model="activeTab" class="status-tabs" aria-label="AI反馈状态筛选">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="待处理" name="pending" />
          <el-tab-pane label="已处理" name="processed" />
        </el-tabs>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="filteredList.length === 0"
        empty-text="暂无AI反馈"
        @retry="getFeedbackList"
      >
        <ResponsiveTable aria-label="AI反馈列表">
          <template #table>
            <div
              class="ai-feedback-table-shell"
              data-testid="ai-feedback-table-shell"
              aria-label="AI反馈宽表横向滚动区域"
              tabindex="0"
            >
                <el-table :data="filteredList" border stripe class="ai-feedback-table">
                  <el-table-column prop="feedbackNo" label="反馈编号" width="170" />
                  <el-table-column prop="serviceType" label="服务类型" width="130" />
                  <el-table-column prop="submittedData" label="提交数据" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="userName" label="反馈用户" width="140" />
                <el-table-column label="评分" width="140">
                  <template #default="{ row }">
                    <el-rate v-model="row.rating" disabled show-score />
                  </template>
                </el-table-column>
                <el-table-column prop="content" label="反馈内容" min-width="260" show-overflow-tooltip />
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'" size="small">
                      {{ statusLabel(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="提交时间" width="180" />
                <el-table-column label="操作" width="200" fixed="right">
                  <template #default="{ row }">
                    <div class="ai-feedback-table-actions">
                      <el-button
                        v-if="row.status === 'PENDING'"
                        size="small"
                        type="primary"
                        link
                        class="ai-feedback-action"
                        data-testid="ai-feedback-process-button"
                        @click="openProcessDialog(row)"
                      >
                        处理
                      </el-button>
                      <el-button size="small" link class="ai-feedback-action" @click="viewDetail(row)">查看</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>

          <template #card>
            <article
              v-for="row in filteredList"
              :key="row.id || row.feedbackNo"
              class="feedback-card"
              data-testid="responsive-ai-feedback-card"
            >
              <div class="feedback-card__header">
                <div>
                  <p class="feedback-card__title">{{ row.feedbackNo || '-' }}</p>
                  <p class="feedback-card__meta">{{ row.createdAt || '-' }}</p>
                </div>
                <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </div>

              <div class="feedback-card__rating" aria-label="反馈评分">
                <el-rate v-model="row.rating" disabled show-score />
              </div>

              <dl class="feedback-card__fields">
                <div>
                  <dt>服务</dt>
                  <dd>{{ row.serviceType || '-' }}</dd>
                </div>
                <div>
                  <dt>提交数据</dt>
                  <dd>{{ row.submittedData || '-' }}</dd>
                </div>
                <div>
                  <dt>用户</dt>
                  <dd>{{ row.userName || '-' }}</dd>
                </div>
                <div class="feedback-card__content">
                  <dt>内容</dt>
                  <dd>{{ row.content || '-' }}</dd>
                </div>
              </dl>

              <div class="feedback-card__actions">
                <el-button
                  v-if="row.status === 'PENDING'"
                  type="primary"
                  plain
                class="ai-feedback-action"
                data-testid="ai-feedback-process-button"
                @click="openProcessDialog(row)"
              >
                  处理反馈
                </el-button>
                <el-button plain class="ai-feedback-action" @click="viewDetail(row)">查看详情</el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :width="dialogWidth"
      destroy-on-close
      class="ai-feedback-dialog"
    >
      <dl class="feedback-dialog-meta">
        <div>
          <dt>服务类型</dt>
          <dd>{{ currentFeedback?.serviceType || '-' }}</dd>
        </div>
        <div>
          <dt>提交数据</dt>
          <dd>{{ currentFeedback?.submittedData || '-' }}</dd>
        </div>
        <div>
          <dt>反馈用户</dt>
          <dd>{{ currentFeedback?.userName || '-' }}</dd>
        </div>
        <div>
          <dt>评分</dt>
          <dd>{{ currentFeedback?.rating || '-' }}</dd>
        </div>
      </dl>

      <div class="feedback-content">
        <div class="label">反馈内容</div>
        <div class="text">{{ currentFeedback?.content || '-' }}</div>
      </div>

      <p v-if="dialogError" class="dialog-error" role="alert">{{ dialogError }}</p>

      <el-form label-width="88px">
        <el-form-item label="处理回复">
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="4"
            :disabled="isReadOnly"
            placeholder="请输入处理回复"
            aria-label="处理回复"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            v-if="!isReadOnly"
            type="primary"
            :loading="submitting"
            @click="submitProcess"
          >
            确认处理
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiFeedbackApi, processFeedbackApi } from '@/api/ai-manage'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'
import { useResponsive } from '@/composables/useResponsive'

const { isMobile } = useResponsive()

const loading = ref(false)
const errorMessage = ref('')
const activeTab = ref('all')
const feedbackList = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const currentFeedback = ref(null)
const replyContent = ref('')
const dialogError = ref('')

const filteredList = computed(() => {
  if (activeTab.value === 'all') return feedbackList.value
  if (activeTab.value === 'pending') return feedbackList.value.filter(i => i.status === 'PENDING')
  if (activeTab.value === 'processed') return feedbackList.value.filter(i => i.status === 'PROCESSED')
  return feedbackList.value
})

const isReadOnly = computed(() => currentFeedback.value?.status !== 'PENDING')
const dialogTitle = computed(() => isReadOnly.value ? '查看反馈' : '处理反馈')
const dialogWidth = computed(() => isMobile.value ? '92vw' : '520px')

const getFeedbackList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getAiFeedbackApi()
    const data = res.data
    feedbackList.value = Array.isArray(data) ? data : (data?.records ?? data?.items ?? [])
  } catch (error) {
    feedbackList.value = []
    errorMessage.value = error?.message || 'AI反馈列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const openProcessDialog = (row) => {
  currentFeedback.value = row
  replyContent.value = ''
  dialogError.value = ''
  dialogVisible.value = true
}

const viewDetail = (row) => {
  currentFeedback.value = row
  replyContent.value = row.reply || ''
  dialogError.value = ''
  dialogVisible.value = true
}

const submitProcess = async () => {
  if (!replyContent.value.trim()) {
    dialogError.value = '请输入处理回复'
    return
  }
  submitting.value = true
  dialogError.value = ''
  try {
    await processFeedbackApi(currentFeedback.value.feedbackId || currentFeedback.value.id, replyContent.value)
    ElMessage.success('处理成功')
    dialogVisible.value = false
    getFeedbackList()
  } catch (error) {
    dialogError.value = error?.message || '反馈处理失败，请重试'
  } finally {
    submitting.value = false
  }
}

const statusLabel = (status) => status === 'PENDING' ? '待处理' : '已处理'

onMounted(() => {
  getFeedbackList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.eyebrow,
.page-title {
  margin: 0;
}

.eyebrow {
  margin-bottom: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-xs);
  font-weight: 700;
  letter-spacing: .08em;
  color: var(--primary-color);
  text-transform: uppercase;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.status-tabs {
  max-width: 100%;
}

.ai-feedback-action {
  min-height: var(--touch-target);
  min-width: 88px;
  touch-action: manipulation;
}

.ai-feedback-table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 8px;
  white-space: nowrap;
}

.ai-feedback-table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.ai-feedback-table-shell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  padding-bottom: 4px;
}

.ai-feedback-table-shell:focus-visible {
  outline: 2px solid rgba(2, 132, 199, .18);
  outline-offset: 2px;
  box-shadow: var(--focus-ring);
}

.ai-feedback-table {
  min-width: 1510px;
}

.feedback-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(14, 165, 233, .16);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(145deg, rgba(240, 249, 255, .9), rgba(255, 255, 255, .97));
  box-shadow: 0 14px 32px rgba(15, 35, 95, .07);
}

.feedback-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.feedback-card__title,
.feedback-card__meta {
  margin: 0;
}

.feedback-card__title {
  overflow-wrap: anywhere;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.feedback-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.feedback-card__rating {
  min-width: 0;
}

.feedback-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.feedback-card__fields div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(14, 165, 233, .1);
}

.feedback-card__fields dt,
.feedback-card__fields dd {
  margin: 0;
}

.feedback-card__fields dt {
  color: var(--text-secondary);
}

.feedback-card__fields dd {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  text-align: right;
}

.feedback-card__content {
  align-items: start;
}

.feedback-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.feedback-card__actions .el-button {
  min-height: var(--touch-target);
  margin-left: 0;
}

.feedback-dialog-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 16px;
}

.feedback-dialog-meta div {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-lighter);
  border-radius: var(--radius-base);
  background: #fff;
}

.feedback-dialog-meta dt,
.feedback-dialog-meta dd {
  margin: 0;
}

.feedback-dialog-meta dt {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.feedback-dialog-meta dd {
  margin-top: 4px;
  color: var(--text-primary);
  overflow-wrap: anywhere;
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
  overflow-wrap: anywhere;
}

.dialog-error {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid rgba(220, 38, 38, .24);
  border-radius: var(--radius-base);
  background: rgba(254, 242, 242, .94);
  color: var(--danger-color);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.dialog-actions .el-button {
  min-height: var(--touch-target);
  margin-left: 0;
}

@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .status-tabs {
    overflow-x: auto;
  }

  .feedback-card__actions,
  .dialog-actions {
    grid-template-columns: 1fr;
    flex-direction: column-reverse;
  }

  .feedback-dialog-meta {
    grid-template-columns: 1fr;
  }

  .dialog-actions .el-button {
    width: 100%;
  }
}
</style>
