<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存流水</h2>
        <div class="header-actions">
          <el-input v-model="searchKey" placeholder="搜索药品名称/流水号" clearable style="width: 220px" @keyup.enter="getFlowList" />
          <el-select v-model="typeFilter" placeholder="操作类型" clearable style="width: 130px">
            <el-option label="入库" value="IN" />
            <el-option label="出库" value="OUT" />
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
          <el-button type="primary" @click="getFlowList">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>

      <el-table :data="filteredList" v-loading="loading" border stripe>
        <el-table-column prop="flowNo" label="流水号" width="160" />
        <el-table-column label="操作类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.flowType === 'IN' ? 'success' : 'warning'" size="small">
              {{ row.flowType === 'IN' ? '入库' : '出库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="drugName" label="药品名称" min-width="160" />
        <el-table-column prop="specification" label="规格" width="140" />
        <el-table-column prop="batchNo" label="批号" width="110" />
        <el-table-column label="操作数量" width="120" align="center">
          <template #default="{ row }">
            <span :class="row.flowType === 'IN' ? 'text-success' : 'text-warning'">
              {{ row.flowType === 'IN' ? '+' : '-' }}{{ row.quantity }} {{ row.unit }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="库存变动" width="150" align="center">
          <template #default="{ row }">
            {{ row.beforeStock }} → <span class="fw-600">{{ row.afterStock }}</span> {{ row.unit }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="90" align="center" />
        <el-table-column prop="createdAt" label="操作时间" width="160" align="center" />
      </el-table>

      <div class="pagination-wrap" v-if="filteredList.length > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStockFlowApi } from '@/api/drug'

const loading = ref(false)
const flowList = ref([])
const searchKey = ref('')
const typeFilter = ref('')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filteredList = computed(() => {
  let list = flowList.value
  if (searchKey.value) {
    const key = searchKey.value.toLowerCase()
    list = list.filter(i =>
      i.drugName.toLowerCase().includes(key) ||
      i.flowNo.toLowerCase().includes(key)
    )
  }
  if (typeFilter.value) {
    list = list.filter(i => i.flowType === typeFilter.value)
  }
  return list
})

const getFlowList = async () => {
  loading.value = true
  try {
    const res = await getStockFlowApi({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    })
    flowList.value = res.data
    total.value = res.total || res.data.length
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  searchKey.value = ''
  typeFilter.value = ''
  dateRange.value = []
  currentPage.value = 1
  getFlowList()
}

onMounted(() => {
  getFlowList()
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
.fw-600 {
  font-weight: 600;
  color: var(--primary-color);
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
