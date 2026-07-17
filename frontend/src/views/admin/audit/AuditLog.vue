<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">审计日志</h2>
        <div class="header-actions">
          <el-input v-model="filters.resourceType" class="filter-field" placeholder="资源类型" clearable />
          <el-input v-model="filters.resourceId" class="filter-field filter-field--wide" placeholder="资源编号" clearable />
          <el-input v-model="filters.operatorId" class="filter-field" placeholder="操作人编号" clearable />
          <el-select v-model="filters.action" class="filter-field filter-field--compact" placeholder="操作类型" clearable>
            <el-option label="查看" value="VIEW" />
            <el-option label="新增" value="CREATE" />
            <el-option label="修改" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="导出" value="EXPORT" />
            <el-option label="登录" value="LOGIN" />
            <el-option label="登出" value="LOGOUT" />
          </el-select>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="loadError"
        :empty="auditList.length === 0"
        empty-text="暂无审计日志"
        @retry="fetchList"
      >
        <ResponsiveTable aria-label="审计日志列表">
          <template #table>
            <el-table :data="auditList" border stripe>
              <el-table-column prop="auditNo" label="审计编号" width="180" />
              <el-table-column prop="resourceType" label="资源类型" width="140" />
              <el-table-column prop="resourceId" label="资源编号" width="160" />
              <el-table-column label="操作类型" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="getActionType(row.action)">{{ getActionLabel(row.action) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="operatorName" label="操作人" width="120">
                <template #default="{ row }">
                  <span>{{ row.operatorName || row.operatorId || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="operatorRole" label="角色" width="120" />
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.result" size="small" effect="plain" :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
                    {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="时间" min-width="170" />
            </el-table>
          </template>

          <template #card>
            <article
              v-for="row in auditList"
              :key="row.id || row.auditNo"
              class="audit-card"
              data-testid="responsive-audit-card"
            >
              <div class="audit-card__header">
                <div>
                  <p class="audit-card__title">{{ row.auditNo }}</p>
                  <p class="audit-card__meta">{{ row.createdAt || '-' }}</p>
                </div>
                <el-tag size="small" :type="getActionType(row.action)">
                  {{ getActionLabel(row.action) }}
                </el-tag>
              </div>
              <dl class="audit-card__fields">
                <div>
                  <dt>资源</dt>
                  <dd>{{ row.resourceType || '-' }} / {{ row.resourceId || '-' }}</dd>
                </div>
                <div>
                  <dt>操作人</dt>
                  <dd>{{ row.operatorName || row.operatorId || '-' }}</dd>
                </div>
                <div>
                  <dt>角色</dt>
                  <dd>{{ row.operatorRole || '-' }}</dd>
                </div>
                <div>
                  <dt>结果</dt>
                  <dd>
                    <el-tag v-if="row.result" size="small" effect="plain" :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
                      {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
                    </el-tag>
                    <span v-else>-</span>
                  </dd>
                </div>
              </dl>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAuditLogListApi } from '@/api/audit'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const auditList = ref([])
const total = ref(0)
const loadError = ref('')

const filters = reactive({
  resourceType: '',
  resourceId: '',
  operatorId: '',
  action: ''
})

const pagination = reactive({ page: 1, pageSize: 10 })

const fetchList = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: pagination.page, pageSize: pagination.pageSize }
    if (filters.resourceType) params.resourceType = filters.resourceType
    if (filters.resourceId) params.resourceId = filters.resourceId
    if (filters.operatorId) params.operatorId = filters.operatorId
    if (filters.action) params.action = filters.action
    const res = await getAuditLogListApi(params)
    const data = res.data
    auditList.value = data.items ?? data.records ?? (Array.isArray(data) ? data : [])
    total.value = data.total ?? auditList.value.length
  } catch (e) {
    // 审计日志接口超时/503 时不再一直 loading（全局 90s 超时太长，api/audit.js 已调为 15s）。
    // 在表格 empty 区显示错误 + 重试按钮，让用户知道是服务不可用而非"无数据"。
    auditList.value = []
    total.value = 0
    loadError.value = e?.message || '审计日志查询失败'
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchList()
}

const handleReset = () => {
  filters.resourceType = ''
  filters.resourceId = ''
  filters.operatorId = ''
  filters.action = ''
  pagination.page = 1
  fetchList()
}

const ACTION_META = {
  VIEW: { label: '查看', type: 'info' },
  CREATE: { label: '新增', type: 'success' },
  UPDATE: { label: '修改', type: 'warning' },
  DELETE: { label: '删除', type: 'danger' },
  EXPORT: { label: '导出', type: 'warning' },
  LOGIN: { label: '登录', type: 'primary' },
  LOGOUT: { label: '登出', type: 'info' }
}
const getActionLabel = (a) => ACTION_META[a]?.label || a || '-'
const getActionType = (a) => ACTION_META[a]?.type || ''

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.filter-field {
  width: 150px;
}
.filter-field--wide {
  width: 160px;
}
.filter-field--compact {
  width: 130px;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
.audit-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, .84);
  box-shadow: 0 12px 30px rgba(15, 35, 95, .06);
}
.audit-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.audit-card__title,
.audit-card__meta {
  margin: 0;
}
.audit-card__title {
  overflow-wrap: anywhere;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}
.audit-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.audit-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}
.audit-card__fields div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}
.audit-card__fields dt,
.audit-card__fields dd {
  margin: 0;
}
.audit-card__fields dt {
  color: var(--text-secondary);
}
.audit-card__fields dd {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  text-align: right;
}
@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .header-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .filter-field,
  .filter-field--wide,
  .filter-field--compact,
  .header-actions .el-button {
    width: 100%;
    min-height: var(--touch-target);
  }

  .pagination-wrapper {
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 4px;
  }
}
</style>
