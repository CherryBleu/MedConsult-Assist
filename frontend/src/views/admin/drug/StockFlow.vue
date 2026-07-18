<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存流水</h2>
      </div>

      <el-form :model="queryParams" inline class="search-form">
        <el-form-item label="药品">
          <el-input v-model="queryParams.keyword" placeholder="药品名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="drugName" label="药品名称" min-width="160" />
        <el-table-column prop="drugId" label="药品编号" width="130" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="flowTypeTag(row.type)" size="small">{{ flowTypeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="center">
          <template #default="{ row }">
            <span :class="row.type === 'INBOUND' ? 'text-success' : 'text-danger'">
              {{ row.type === 'INBOUND' ? '+' : '-' }}{{ row.quantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批号" width="130" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="getList"
        @current-change="getList"
        class="pagination"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getStockFlowListApi } from '@/api/drug'
import dayjs from 'dayjs'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})

const flowTypeText = (t) => ({ INBOUND: '入库', OUTBOUND: '出库', ADJUST: '调整' }[t] || t || '—')
const flowTypeTag = (t) => ({ INBOUND: 'success', OUTBOUND: 'warning', ADJUST: 'info' }[t] || '')
const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '—')

const getList = async () => {
  loading.value = true
  try {
    const res = await getStockFlowListApi({
      page: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword || undefined
    })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.pageNum = 1
  getList()
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.search-form {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.text-success {
  color: var(--el-color-success);
  font-weight: 600;
}
.text-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
