<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存管理</h2>
        <el-alert type="info" :closable="false" show-icon style="margin-left: auto;">
          系统管理员仅查看库存，出入库操作请由药房管理员执行
        </el-alert>
      </div>

      <el-table :data="stockList" v-loading="loading" border stripe>
        <el-table-column prop="drugName" label="药品名称" width="200" />
        <el-table-column prop="specification" label="规格" width="160" />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column label="库存数量" width="120">
          <template #default="{ row }">
            <span :class="{ 'low-stock': row.status === 'LOW_STOCK' }">
              {{ row.stockQuantity }} {{ row.unit }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="warningQuantity" label="预警值" width="100" align="center" />
        <el-table-column prop="expireDate" label="有效期" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(STOCK_STATUS, row.status)">
              {{ getStatusLabel(STOCK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { STOCK_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getStockListApi } from '@/api/drug'

const loading = ref(false)
const stockList = ref([])

const getStockList = async () => {
  loading.value = true
  try {
    const res = await getStockListApi()
    stockList.value = res.data
  } finally {
    loading.value = false
  }
}

getStockList()
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.low-stock {
  color: var(--danger-color);
  font-weight: 600;
}
</style>