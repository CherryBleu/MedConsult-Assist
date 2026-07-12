<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存流水</h2>
        <div class="header-actions">
          <el-select
            v-model="selectedDrugId"
            filterable
            remote
            reserve-keyword
            clearable
            placeholder="请先选择药品"
            :remote-method="searchDrugs"
            :loading="drugLoading"
            style="width: 280px"
            @change="onDrugChange"
          >
            <el-option
              v-for="d in drugOptions"
              :key="d.drugId"
              :label="`${d.name}（${d.specification || '-'}）`"
              :value="d.drugId"
            />
          </el-select>
          <el-select v-model="typeFilter" placeholder="操作类型" clearable style="width: 130px">
            <el-option label="入库" value="INBOUND" />
            <el-option label="出库" value="OUTBOUND" />
          </el-select>
          <el-button type="primary" :disabled="!selectedDrugId" @click="getFlowList">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>

      <el-empty v-if="!selectedDrugId" description="请先选择药品后查看库存流水" />
      <template v-else>
        <el-table :data="filteredList" v-loading="loading" border stripe>
          <el-table-column prop="stockFlowId" label="流水号" width="160">
            <template #default="{ row }">{{ row.stockFlowId ?? row.flowId ?? row.id ?? '-' }}</template>
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

const loading = ref(false)
const flowList = ref([])
const typeFilter = ref('')

// 药品选择
const drugLoading = ref(false)
const drugOptions = ref([])
const selectedDrugId = ref('')

const selectedDrugLabel = computed(() => {
  const d = drugOptions.value.find(i => i.drugId === selectedDrugId.value)
  return d ? `${d.name}（${d.specification || '-'}）` : '-'
})

// 分页
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 后端流水类型 INBOUND/OUTBOUND，兼容旧值 IN/OUT
const isInbound = (row) => {
  const t = row.type ?? row.flowType
  return t === 'INBOUND' || t === 'IN'
}
const flowTagType = (row) => (isInbound(row) ? 'success' : 'warning')
const flowLabel = (row) => (isInbound(row) ? '入库' : '出库')

const filteredList = computed(() => {
  if (!typeFilter.value) return flowList.value
  return flowList.value.filter(row => {
    const t = row.type ?? row.flowType
    return t === typeFilter.value
  })
})

// 远程搜索药品（对齐后端 GET /drugs?keyword=）
const searchDrugs = async (keyword) => {
  drugLoading.value = true
  try {
    const res = await getDrugListApi({ keyword: keyword || undefined, page: 1, pageSize: 50 })
    drugOptions.value = res.data || []
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
  loading.value = true
  try {
    const res = await getStockFlowApi(selectedDrugId.value, {
      page: currentPage.value,
      pageSize: pageSize.value
    })
    flowList.value = res.data || []
    total.value = res.total || (res.data?.length ?? 0)
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
}
</style>
