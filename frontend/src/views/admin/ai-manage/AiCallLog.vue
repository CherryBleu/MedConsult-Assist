<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <div>
          <p class="eyebrow">AI Operations</p>
          <h2 class="page-title">AI调用日志</h2>
        </div>
        <div class="stat-bar" aria-label="AI调用日志统计">
          <span>累计调用：<b>{{ total }}</b> 次</span>
          <span>本页成功：<b class="success">{{ successCount }}</b></span>
          <span>本页失败：<b class="danger">{{ failCount }}</b></span>
          <span>缓存命中：<b>{{ cacheHitCount }}</b></span>
          <span>Token：<b>{{ pageTotalTokens }}</b></span>
          <span>成本：<b>{{ formatCost(pageEstimatedCost) }}</b></span>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="logList.length === 0"
        empty-text="暂无AI调用日志"
        @retry="getLogList"
      >
        <ResponsiveTable aria-label="AI调用日志列表">
          <template #table>
            <el-table :data="logList" border stripe>
              <el-table-column prop="logNo" label="日志编号" width="170" />
              <el-table-column prop="serviceType" label="服务类型" width="130" />
              <el-table-column prop="callerService" label="调用方" width="150" show-overflow-tooltip />
              <el-table-column prop="modelName" label="模型名称" min-width="170" show-overflow-tooltip />
              <el-table-column prop="userName" label="调用用户" width="120" />
              <el-table-column label="缓存" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="isCacheHit(row) ? 'success' : 'info'" effect="plain" size="small">
                    {{ cacheHitLabel(row) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Token" width="150" align="center">
                <template #default="{ row }">{{ tokenSummary(row) }}</template>
              </el-table-column>
              <el-table-column label="预估成本" width="120" align="right">
                <template #default="{ row }">{{ formatCost(row.estimatedCostYuan) }}</template>
              </el-table-column>
              <el-table-column prop="traceId" label="Trace ID" min-width="180" show-overflow-tooltip />
              <el-table-column prop="requestId" label="Request ID" min-width="160" show-overflow-tooltip />
              <el-table-column label="输入长度" width="100" align="center">
                <template #default="{ row }">{{ row.inputLength }} 字</template>
              </el-table-column>
              <el-table-column label="输出长度" width="100" align="center">
                <template #default="{ row }">{{ row.outputLength }} 字</template>
              </el-table-column>
              <el-table-column label="耗时" width="100" align="center">
                <template #default="{ row }">{{ row.costTime }} ms</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                    {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="调用时间" width="180" />
            </el-table>
          </template>

          <template #card>
            <article
              v-for="row in logList"
              :key="row.id || row.logNo"
              class="ai-log-card"
              data-testid="responsive-ai-call-log-card"
            >
              <div class="ai-log-card__header">
                <div>
                  <p class="ai-log-card__title">{{ row.logNo || '-' }}</p>
                  <p class="ai-log-card__meta">{{ row.createdAt || '-' }}</p>
                </div>
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
              </div>

              <dl class="ai-log-card__fields">
                <div>
                  <dt>服务</dt>
                  <dd>{{ row.serviceType || '-' }}</dd>
                </div>
                <div>
                  <dt>模型</dt>
                  <dd>{{ row.modelName || '-' }}</dd>
                </div>
                <div>
                  <dt>用户</dt>
                  <dd>{{ row.userName || '-' }}</dd>
                </div>
                <div>
                  <dt>调用方</dt>
                  <dd>{{ row.callerService || '-' }}</dd>
                </div>
                <div>
                  <dt>缓存</dt>
                  <dd>
                    <el-tag :type="isCacheHit(row) ? 'success' : 'info'" effect="plain" size="small">
                      {{ cacheHitLabel(row) }}
                    </el-tag>
                  </dd>
                </div>
                <div>
                  <dt>Token</dt>
                  <dd>{{ tokenSummary(row) }}</dd>
                </div>
                <div>
                  <dt>成本</dt>
                  <dd>{{ formatCost(row.estimatedCostYuan) }}</dd>
                </div>
                <div>
                  <dt>Trace</dt>
                  <dd class="ai-log-card__mono">{{ row.traceId || '-' }}</dd>
                </div>
                <div>
                  <dt>Request</dt>
                  <dd class="ai-log-card__mono">{{ row.requestId || '-' }}</dd>
                </div>
                <div>
                  <dt>输入/输出</dt>
                  <dd>{{ row.inputLength ?? 0 }} / {{ row.outputLength ?? 0 }} 字</dd>
                </div>
                <div>
                  <dt>耗时</dt>
                  <dd>{{ row.costTime ?? 0 }} ms</dd>
                </div>
              </dl>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>

      <div v-if="!loading && !errorMessage && total > 0" class="pagination-box">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          aria-label="AI调用日志分页"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getLogList"
          @current-change="getLogList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getAiCallLogApi } from '@/api/ai-manage'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const errorMessage = ref('')
const logList = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const successCount = computed(() => logList.value.filter(i => i.status === 'SUCCESS').length)
const failCount = computed(() => logList.value.filter(i => i.status === 'FAILED').length)
const cacheHitCount = computed(() => logList.value.filter(isCacheHit).length)
const pageTotalTokens = computed(() => logList.value.reduce((sum, row) => sum + numberOrZero(row.totalTokens ?? row.costTokens), 0))
const pageEstimatedCost = computed(() => logList.value.reduce((sum, row) => sum + numberOrZero(row.estimatedCostYuan), 0))

const numberOrZero = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const isCacheHit = (row) => row?.cacheHit === true || row?.cacheHit === 1
const cacheHitLabel = (row) => isCacheHit(row) ? '缓存命中' : '未命中'
const tokenSummary = (row) => {
  const totalTokens = numberOrZero(row?.totalTokens ?? row?.costTokens)
  const promptTokens = numberOrZero(row?.promptTokens)
  const completionTokens = numberOrZero(row?.completionTokens)
  return `${totalTokens}（P${promptTokens} / C${completionTokens}）`
}
const formatCost = (value) => `￥${numberOrZero(value).toFixed(6)}`

const getLogList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getAiCallLogApi({ page: pageNum.value, pageSize: pageSize.value })
    const data = res.data
    logList.value = Array.isArray(data) ? data : (data?.records ?? data?.items ?? [])
    total.value = typeof data?.total === 'number' ? data.total : logList.value.length
  } catch (error) {
    logList.value = []
    total.value = 0
    errorMessage.value = error?.message || 'AI调用日志加载失败，请重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getLogList()
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

.stat-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  font-size: 14px;
  color: var(--text-regular);
}

.stat-bar span {
  min-height: var(--touch-target);
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--border-lighter);
  border-radius: 999px;
  background: rgba(255, 255, 255, .72);
}

.stat-bar b {
  color: var(--text-primary);
  font-size: 16px;
}

.stat-bar .success {
  color: var(--success-color);
}

.stat-bar .danger {
  color: var(--danger-color);
}

.pagination-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
  padding-bottom: 4px;
  max-width: 100%;
}

.pagination-box :deep(.el-pagination) {
  flex-wrap: wrap;
  gap: 6px;
}

.pagination-box :deep(.btn-prev),
.pagination-box :deep(.btn-next),
.pagination-box :deep(.el-pager li.number) {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
}

.pagination-box :deep(.el-pagination__sizes),
.pagination-box :deep(.el-pagination__jump) {
  min-height: var(--touch-target);
}

.pagination-box :deep(.el-pagination__sizes .el-select),
.pagination-box :deep(.el-pagination__jump .el-input) {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
}

.pagination-box :deep(.el-pagination__sizes .el-select__wrapper),
.pagination-box :deep(.el-pagination__sizes .el-input__wrapper),
.pagination-box :deep(.el-pagination__jump .el-input__wrapper),
.pagination-box :deep(.el-pagination__jump .el-input__inner) {
  min-height: var(--touch-target);
}

.ai-log-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(37, 99, 235, .14);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(145deg, rgba(239, 246, 255, .88), rgba(255, 255, 255, .96));
  box-shadow: 0 14px 32px rgba(15, 35, 95, .07);
}

.ai-log-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-log-card__title,
.ai-log-card__meta {
  margin: 0;
}

.ai-log-card__title {
  overflow-wrap: anywhere;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.ai-log-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.ai-log-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.ai-log-card__fields div {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(37, 99, 235, .1);
}

.ai-log-card__fields dt,
.ai-log-card__fields dd {
  margin: 0;
}

.ai-log-card__fields dt {
  color: var(--text-secondary);
}

.ai-log-card__fields dd {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  text-align: right;
}

.ai-log-card__mono {
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
}

@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .stat-bar {
    display: grid;
    grid-template-columns: 1fr;
    justify-content: stretch;
  }

  .stat-bar span {
    justify-content: space-between;
  }

  .pagination-box {
    justify-content: flex-start;
  }
}
</style>
