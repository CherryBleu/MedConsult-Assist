<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存流水</h2>
        <div class="header-actions">
          <el-select
            v-model="selectedDrugId"
            class="drug-select"
            filterable
            remote
            reserve-keyword
            clearable
            placeholder="请先选择药品"
            :remote-method="searchDrugs"
            :loading="drugLoading"
            @change="onDrugChange"
          >
            <el-option
              v-for="d in drugOptions"
              :key="normalizeDrugId(d)"
              :label="formatDrugLabel(d)"
              :value="normalizeDrugId(d)"
            />
          </el-select>
          <el-select v-model="typeFilter" class="type-select" placeholder="操作类型" clearable>
            <el-option label="入库" value="INBOUND" />
            <el-option label="出库" value="OUTBOUND" />
          </el-select>
          <el-button type="primary" class="flow-action" :disabled="!selectedDrugId" @click="getFlowList">查询</el-button>
          <el-button class="flow-action" @click="resetFilter">重置</el-button>
        </div>
      </div>

      <el-empty v-if="!selectedDrugId" description="请先选择药品后查看库存流水" />
      <template v-else>
        <PageState
          :loading="loading"
          :error="errorMessage"
          :empty="filteredList.length === 0"
          loading-text="正在加载库存流水..."
          empty-text="暂无库存流水"
          @retry="getFlowList"
        >
          <ResponsiveTable aria-label="库存流水列表">
            <template #table>
              <el-table :data="filteredList" border stripe>
                <el-table-column prop="stockFlowId" label="流水号" width="160">
                  <template #default="{ row }">{{ flowId(row) }}</template>
                </el-table-column>
                <el-table-column label="操作类型" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="flowTagType(row)" size="small">{{ flowLabel(row) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="药品名称" min-width="160">
                  <template #default>{{ selectedDrugLabel }}</template>
                </el-table-column>
                <el-table-column prop="quantity" label="操作数量" width="120" align="center">
                  <template #default="{ row }">
                    <span :class="isInbound(row) ? 'text-success' : 'text-warning'">
                      {{ isInbound(row) ? '+' : '-' }}{{ row.quantity ?? 0 }} {{ row.unit || '' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="batchNo" label="批号" width="130">
                  <template #default="{ row }">{{ row.batchNo ?? '-' }}</template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.remark ?? '-' }}</template>
                </el-table-column>
                <el-table-column prop="createdAt" label="操作时间" width="170" align="center">
                  <template #default="{ row }">{{ row.createdAt ?? '-' }}</template>
                </el-table-column>
              </el-table>
            </template>

            <template #card>
              <article
                v-for="row in filteredList"
                :key="flowId(row)"
                class="flow-card"
                data-testid="responsive-stock-flow-card"
              >
                <div class="flow-card__header">
                  <div>
                    <p class="flow-card__title">{{ flowId(row) }}</p>
                    <p class="flow-card__meta">{{ row.createdAt || '-' }}</p>
                  </div>
                  <el-tag :type="flowTagType(row)" size="small">{{ flowLabel(row) }}</el-tag>
                </div>

                <dl class="flow-card__fields">
                  <div>
                    <dt>药品</dt>
                    <dd>{{ selectedDrugLabel }}</dd>
                  </div>
                  <div>
                    <dt>数量</dt>
                    <dd :class="isInbound(row) ? 'text-success' : 'text-warning'">
                      {{ isInbound(row) ? '+' : '-' }}{{ row.quantity ?? 0 }} {{ row.unit || '' }}
                    </dd>
                  </div>
                  <div>
                    <dt>批号</dt>
                    <dd>{{ row.batchNo || '-' }}</dd>
                  </div>
                  <div>
                    <dt>备注</dt>
                    <dd>{{ row.remark || '-' }}</dd>
                  </div>
                </dl>
              </article>
            </template>
          </ResponsiveTable>
        </PageState>

        <div class="pagination-wrap" v-if="total > 0">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="getFlowList"
            @size-change="getFlowList"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDrugListApi, getStockFlowApi } from '@/api/drug'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const errorMessage = ref('')
const flowList = ref([])
const typeFilter = ref('')

// 药品选择
const drugLoading = ref(false)
const drugOptions = ref([])
const selectedDrugId = ref('')

const normalizeDrugId = (drug) => drug?.drugId ?? drug?.id ?? drug?.drugNo ?? ''
const formatDrugLabel = (drug) => {
  const name = drug?.name ?? drug?.drugName ?? drug?.genericName ?? '-'
  return `${name}（${drug?.specification || '-'}）`
}

const selectedDrugLabel = computed(() => {
  const d = drugOptions.value.find(i => String(normalizeDrugId(i)) === String(selectedDrugId.value))
  return d ? formatDrugLabel(d) : '-'
})

// 分页
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 后端流水类型 INBOUND/OUTBOUND，兼容旧值 IN/OUT
const normalizedFlowType = (row) => {
  const t = row.type ?? row.flowType
  if (t === 'INBOUND' || t === 'IN') return 'INBOUND'
  if (t === 'OUTBOUND' || t === 'OUT') return 'OUTBOUND'
  return t
}
const isInbound = (row) => normalizedFlowType(row) === 'INBOUND'
const flowTagType = (row) => (isInbound(row) ? 'success' : 'warning')
const flowLabel = (row) => (isInbound(row) ? '入库' : '出库')
const flowId = (row) => row.stockFlowId ?? row.flowId ?? row.id ?? '-'

const filteredList = computed(() => {
  if (!typeFilter.value) return flowList.value
  return flowList.value.filter(row => normalizedFlowType(row) === typeFilter.value)
})

// 远程搜索药品（对齐后端 GET /drugs?keyword=）
const searchDrugs = async (keyword) => {
  drugLoading.value = true
  try {
    const res = await getDrugListApi({ keyword: keyword || undefined, page: 1, pageSize: 50 })
    drugOptions.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (e) {
    drugOptions.value = []
  } finally {
    drugLoading.value = false
  }
}

const onDrugChange = () => {
  currentPage.value = 1
  if (selectedDrugId.value) {
    getFlowList()
  } else {
    flowList.value = []
    total.value = 0
  }
}

// 查询某药品的库存流水（对齐后端 GET /drugs/{drugId}/stock/flows?page=&pageSize=）
const getFlowList = async () => {
  if (!selectedDrugId.value) {
    ElMessage.warning('请先选择药品')
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const res = await getStockFlowApi(selectedDrugId.value, {
      page: currentPage.value,
      pageSize: pageSize.value
    })
    flowList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
    total.value = res.total ?? res.data?.total ?? flowList.value.length
  } catch (e) {
    flowList.value = []
    total.value = 0
    errorMessage.value = e?.message || '库存流水加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  typeFilter.value = ''
  currentPage.value = 1
  if (selectedDrugId.value) getFlowList()
}

onMounted(() => {
  searchDrugs('')
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.drug-select {
  width: 280px;
}
.type-select {
  width: 130px;
}
.flow-action {
  min-height: var(--touch-target);
  min-width: 72px;
}
.text-success {
  color: #67c23a;
  font-weight: 600;
}
.text-warning {
  color: #e6a23c;
  font-weight: 600;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  max-width: 100%;
  overflow-x: auto;
}
.flow-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, .86);
  box-shadow: 0 12px 30px rgba(15, 35, 95, .06);
}
.flow-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.flow-card__header > div {
  min-width: 0;
}
.flow-card__title,
.flow-card__meta {
  margin: 0;
}
.flow-card__title {
  overflow-wrap: anywhere;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}
.flow-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.flow-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}
.flow-card__fields div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}
.flow-card__fields dt,
.flow-card__fields dd {
  margin: 0;
}
.flow-card__fields dt {
  color: var(--text-secondary);
}
.flow-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}

@media (max-width: 768px) {
  .page-header,
  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .drug-select,
  .type-select,
  .header-actions .flow-action {
    width: 100%;
  }

  .header-actions :deep(.el-select__wrapper) {
    min-height: var(--touch-target);
  }

  .pagination-wrap {
    justify-content: flex-start;
    padding-bottom: 4px;
  }

  .pagination-wrap :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>
