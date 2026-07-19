<template>
  <div class="page-container">
    <div class="card-box admin-drug-page stock-flow-page">
      <div class="page-header">
        <div class="page-heading">
          <h2 class="page-title">库存流水</h2>
          <p class="page-subtitle">共 {{ total }} 条出入库记录</p>
        </div>
        <div class="header-actions" aria-label="库存流水筛选">
          <el-input
            v-model="searchKey"
            class="filter-control search-control"
            aria-label="搜索药品名称"
            placeholder="搜索药品名称"
            clearable
            :disabled="loading"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="typeFilter"
            class="filter-control type-select"
            aria-label="筛选操作类型"
            placeholder="操作类型"
            clearable
            :disabled="loading"
          >
            <el-option label="入库" value="INBOUND" />
            <el-option label="出库" value="OUTBOUND" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            class="date-filter"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :disabled="loading"
          />
          <el-button
            type="primary"
            class="flow-action"
            :icon="Search"
            :loading="loading"
            :disabled="loading"
            @click="handleSearch"
          >
            搜索
          </el-button>
          <el-button
            class="flow-action"
            :icon="Refresh"
            :disabled="loading"
            @click="handleReset"
          >
            重置
          </el-button>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="flowList.length === 0"
        loading-text="正在加载库存流水..."
        empty-text="暂无库存流水"
        @retry="getFlowList"
      >
        <section
          class="admin-table-scroll"
          data-testid="admin-stock-flow-table-scroll"
          aria-label="库存流水横向滚动区域"
        >
          <el-table class="admin-stock-flow-table" :data="flowList" border stripe>
            <el-table-column prop="flowNo" label="流水号" width="170">
              <template #default="{ row }">{{ row.flowNo ?? row.stockFlowId ?? row.id ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="操作类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="isInbound(row) ? 'success' : 'warning'" size="small">
                  {{ isInbound(row) ? '入库' : '出库' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="drugName" label="药品名称" min-width="190" />
            <el-table-column prop="specification" label="规格" width="160" />
            <el-table-column prop="batchNo" label="批号" width="130">
              <template #default="{ row }">{{ row.batchNo ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="操作数量" width="130" align="center">
              <template #default="{ row }">
                <span :class="isInbound(row) ? 'text-success' : 'text-warning'">
                  {{ isInbound(row) ? '+' : '-' }}{{ row.quantity ?? 0 }} {{ row.unit || '' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="120">
              <template #default="{ row }">{{ row.operatorName ?? row.operator ?? '系统' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="操作时间" width="180" align="center">
              <template #default="{ row }">{{ row.createdAt ?? '-' }}</template>
            </el-table-column>
          </el-table>
        </section>
      </PageState>

      <div class="pagination-wrap" v-if="!loading && !errorMessage && total > 0">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="getFlowList"
          @current-change="getFlowList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getStockFlowListApi } from '@/api/drug'
import PageState from '@/components/common/PageState.vue'

const loading = ref(false)
const errorMessage = ref('')
const flowList = ref([])
const total = ref(0)
const searchKey = ref('')
const typeFilter = ref('')
const dateRange = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

const asArray = (data) => Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback

const normalizedFlowType = (row) => {
  const type = row.type ?? row.flowType
  if (type === 'INBOUND' || type === 'IN') return 'INBOUND'
  if (type === 'OUTBOUND' || type === 'OUT') return 'OUTBOUND'
  return type
}

const isInbound = (row) => normalizedFlowType(row) === 'INBOUND'

const handleSearch = () => {
  queryParams.pageNum = 1
  getFlowList()
}

const handleReset = () => {
  searchKey.value = ''
  typeFilter.value = ''
  dateRange.value = []
  queryParams.pageNum = 1
  getFlowList()
}

const getFlowList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const params = { ...queryParams }
    if (searchKey.value) params.keyword = searchKey.value
    if (typeFilter.value) params.type = typeFilter.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getStockFlowListApi(params)
    flowList.value = asArray(res.data)
    total.value = res.data?.total ?? res.total ?? flowList.value.length
  } catch (error) {
    flowList.value = []
    total.value = 0
    errorMessage.value = getErrorMessage(error, '库存流水加载失败，请重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getFlowList()
})
</script>

<style scoped>
.admin-drug-page.card-box {
  min-width: 0;
  padding: 18px;
  border-radius: var(--radius-sm);
}

.page-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.page-heading {
  min-width: 0;
}

.page-title,
.page-subtitle {
  margin: 0;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.page-subtitle {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.filter-control {
  width: 190px;
}

.search-control {
  width: 220px;
}

.type-select {
  width: 132px;
}

.date-filter {
  width: 260px;
}

.flow-action {
  min-width: 76px;
  min-height: var(--touch-target);
  transition: border-color var(--motion-base) ease, box-shadow var(--motion-base) ease, transform var(--motion-fast) ease;
}

.flow-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.admin-table-scroll {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  scrollbar-gutter: stable;
}

.admin-stock-flow-table {
  min-width: 1180px;
}

.admin-stock-flow-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: var(--text-regular);
  font-weight: 700;
}

.admin-stock-flow-table :deep(.el-table__row) {
  transition: background-color var(--motion-fast) ease;
}

.text-success {
  color: #15803d;
  font-weight: 700;
}

.text-warning {
  color: #b45309;
  font-weight: 700;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  max-width: 100%;
  margin-top: 16px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.pagination-wrap :deep(.el-pagination button),
.pagination-wrap :deep(.el-pagination .number),
.pagination-wrap :deep(.el-pagination .more),
.pagination-wrap :deep(.el-pagination__sizes .el-select__wrapper),
.pagination-wrap :deep(.el-pagination__jump .el-input__wrapper) {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
}

@media (max-width: 900px) {
  .page-header,
  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-control,
  .search-control,
  .type-select,
  .date-filter,
  .header-actions .flow-action {
    width: 100%;
  }

  .pagination-wrap {
    justify-content: flex-start;
  }
}
</style>
