<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存流水</h2>
        <div class="header-actions">
          <el-input v-model="searchKey" placeholder="搜索药品名称" clearable style="width: 200px" @keyup.enter="getFlowList" />
          <el-select v-model="typeFilter" placeholder="操作类型" clearable style="width: 120px">
            <el-option label="入库" value="INBOUND" />
            <el-option label="出库" value="OUTBOUND" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table :data="flowList" v-loading="loading" border stripe>
        <el-table-column prop="flowNo" label="流水号" width="160">
          <template #default="{ row }">{{ row.flowNo ?? row.stockFlowId ?? row.id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="isInbound(row) ? 'success' : 'warning'" size="small">
              {{ isInbound(row) ? '入库' : '出库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="drugName" label="药品名称" min-width="180" />
        <el-table-column prop="specification" label="规格" width="140" />
        <el-table-column prop="batchNo" label="批号" width="120">
          <template #default="{ row }">{{ row.batchNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作数量" width="120" align="center">
          <template #default="{ row }">
            <span :class="isInbound(row) ? 'text-success' : 'text-warning'">
              {{ isInbound(row) ? '+' : '-' }}{{ row.quantity ?? 0 }} {{ row.unit || '' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="120">
          <template #default="{ row }">{{ row.operatorName ?? row.operator ?? '系统' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="170" align="center">
          <template #default="{ row }">{{ row.createdAt ?? '-' }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="getFlowList"
        @current-change="getFlowList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { getStockFlowListApi } from '@/api/drug'

const loading = ref(false)
const flowList = ref([])
const total = ref(0)
const searchKey = ref('')
const typeFilter = ref('')
const dateRange = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

const isInbound = (row) => {
  const t = row.type ?? row.flowType
  return t === 'INBOUND' || t === 'IN'
}

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
  try {
    const params = { ...queryParams }
    if (searchKey.value) params.keyword = searchKey.value
    if (typeFilter.value) params.type = typeFilter.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getStockFlowListApi(params)
    flowList.value = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
    total.value = res.data?.total ?? res.total ?? 0
  } finally {
    loading.value = false
  }
}

getFlowList()
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
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>