<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存预警</h2>
        <div class="warn-count">
          <el-tag type="danger" effect="dark">共 {{ warningList.length }} 条预警</el-tag>
        </div>
      </div>

      <el-table :data="warningList" v-loading="loading" border stripe>
        <el-table-column prop="drugName" label="药品名称" width="200" />
        <el-table-column prop="specification" label="规格" width="160" />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column label="预警类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.warningType === 'LOW_STOCK' ? 'warning' : 'danger'" size="small">
              {{ row.warningType === 'LOW_STOCK' ? '库存不足' : '临期预警' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" width="120">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.warningType === 'LOW_STOCK' }">
              {{ row.stockQuantity }} {{ row.unit }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="预警值" width="100" align="center">
          <template #default="{ row }">{{ row.warningQuantity }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="有效期" width="120" />
        <el-table-column label="剩余天数" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.daysLeft < 30 }">{{ row.daysLeft }} 天</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="goStockIn(row)">
              立即入库
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStockWarningApi } from '@/api/drug'

const router = useRouter()
const loading = ref(false)
const warningList = ref([])

const getWarningList = async () => {
  loading.value = true
  try {
    const res = await getStockWarningApi()
    warningList.value = res.data
  } finally {
    loading.value = false
  }
}

const goStockIn = (row) => {
  router.push('/admin/stock')
}

onMounted(() => {
  getWarningList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.text-danger {
  color: var(--danger-color);
  font-weight: 600;
}
</style>